package dev.advancedbulkstorage.migration;

import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.BulkConfig;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One cooperative background budget per logical server, shared by its dimensions. Server-thread only. */
public final class BulkScheduler {
    private static final int[] PERCENT = {100, 75, 50, 25, 0};
    private static final int[] THRESHOLDS = {25, 35, 45, 50};
    private static final long PREVIEW_PAUSE_NANOS = 250_000;
    private static final int PREVIEW_PAUSE_KEYS = 64;
    private static final Map<MinecraftServer, BulkScheduler> SERVERS = new HashMap<>();
    private final IdentityHashMap<Job, Ticket> jobs = new IdentityHashMap<>();
    private final ArrayDeque<Ticket> regular = new ArrayDeque<>();
    private final ArrayDeque<Ticket> priority = new ArrayDeque<>();
    private final ArrayDeque<Ticket> previews = new ArrayDeque<>();
    private double smoothed = -1;
    private int band;
    private int priorityStreak;
    private boolean preferPreview = true;
    private long processedTick;
    private long lastBudget;
    private long lastElapsed;
    private int lastKeys;

    @FunctionalInterface
    public interface Job {
        /** Returns true to requeue for the next tick. False sleeps until a new wakeup. */
        boolean run(Allowance allowance);
    }

    public static final class Allowance {
        private final long start;
        private final long nanos;
        private int keys;

        private Allowance(long start, long nanos, int keys) {
            this.start = start;
            this.nanos = nanos;
            this.keys = keys;
        }

        public boolean takeKey() {
            if (keys == 0 || System.nanoTime() - start >= nanos) {
                return false;
            }
            keys--;
            return true;
        }
    }

    private enum Lane { REGULAR, PRIORITY, PREVIEW }

    private static final class Ticket {
        final Job job;
        final Lane lane;
        boolean queued;
        long processed = Long.MIN_VALUE;

        Ticket(Job job, Lane lane) {
            this.job = job;
            this.lane = lane;
        }
    }

    public record Status(int registered, int queued, int bandPercent, double averagedMspt,
                         long budgetNanos, long elapsedNanos, int keys) {}

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(BulkScheduler::tick);
        NeoForge.EVENT_BUS.addListener(BulkScheduler::stop);
        NeoForge.EVENT_BUS.addListener(BulkScheduler::commands);
    }

    public static BulkScheduler get(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new BulkScheduler());
    }

    public void wake(Job job, boolean high) {
        wake(job, high ? Lane.PRIORITY : Lane.REGULAR);
    }

    public void wakePreview(Job job) {
        wake(job, Lane.PREVIEW);
    }

    private void wake(Job job, Lane lane) {
        enqueue(jobs.computeIfAbsent(job, key -> new Ticket(key, lane)));
    }

    public void cancel(Job job) {
        var ticket = jobs.remove(job);
        if (ticket != null && ticket.queued) {
            queue(ticket).remove(ticket);
            ticket.queued = false;
        }
    }

    public Status status() {
        return new Status(jobs.size(), regular.size() + priority.size() + previews.size(), PERCENT[band], Math.max(0, smoothed),
                lastBudget, lastElapsed, lastKeys);
    }

    /** Also used by deterministic scheduler regression tests. */
    public void advance(double mspt, long budgetNanos, int maxKeys, boolean adaptive) {
        updateBand(mspt, adaptive);
        lastBudget = budgetNanos * PERCENT[band] / 100;
        lastElapsed = 0;
        lastKeys = 0;
        boolean previewsOnly = lastBudget == 0 && !previews.isEmpty();
        if (previewsOnly) {
            // Keep an open screen responsive while the adaptive scheduler pauses background transfers.
            lastBudget = Math.min(budgetNanos, PREVIEW_PAUSE_NANOS);
        }
        int remaining = previews.size() + (previewsOnly ? 0 : regular.size() + priority.size());
        if (remaining == 0 || lastBudget == 0 || maxKeys <= 0) {
            return;
        }
        processedTick++;
        long start = System.nanoTime();
        int keys = previewsOnly ? Math.min(maxKeys, PREVIEW_PAUSE_KEYS) : (int) ((long) maxKeys * PERCENT[band] / 100);
        var allowance = new Allowance(start, lastBudget, keys);
        var deferred = new ArrayDeque<Ticket>();
        while (remaining-- > 0 && allowance.keys > 0 && System.nanoTime() - start < lastBudget) {
            var ticket = previewsOnly ? previews.pollFirst() : next();
            if (ticket == null) {
                break;
            }
            ticket.queued = false;
            if (ticket.processed == processedTick) {
                deferred.addLast(ticket);
                continue;
            }
            ticket.processed = processedTick;
            try {
                if (ticket.job.run(allowance) && jobs.get(ticket.job) == ticket) {
                    deferred.addLast(ticket);
                }
            } catch (RuntimeException failure) {
                cancel(ticket.job);
                LogUtils.getLogger().error("Bulk storage background job stopped after an exception", failure);
            }
        }
        for (var ticket : deferred) {
            if (jobs.get(ticket.job) == ticket) {
                enqueue(ticket);
            }
        }
        lastElapsed = System.nanoTime() - start;
        lastKeys = keys - allowance.keys;
    }

    private void updateBand(double sample, boolean adaptive) {
        if (!Double.isFinite(sample) || sample < 0) {
            sample = 0;
        }
        smoothed = smoothed < 0 ? sample : smoothed + (sample - smoothed) / 8;
        if (!adaptive) {
            band = 0;
            return;
        }
        while (band < THRESHOLDS.length && smoothed >= THRESHOLDS[band]) {
            band++;
        }
        while (band > 0 && smoothed < THRESHOLDS[band - 1] - 2) {
            band--;
        }
    }

    private ArrayDeque<Ticket> queue(Ticket ticket) {
        return switch (ticket.lane) {
            case REGULAR -> regular;
            case PRIORITY -> priority;
            case PREVIEW -> previews;
        };
    }

    private void enqueue(Ticket ticket) {
        if (!ticket.queued) {
            ticket.queued = true;
            queue(ticket).addLast(ticket);
        }
    }

    private Ticket next() {
        if ((!priority.isEmpty() || !previews.isEmpty()) && (regular.isEmpty() || priorityStreak < 3)) {
            priorityStreak = Math.min(3, priorityStreak + 1);
            if (priority.isEmpty() || !previews.isEmpty() && preferPreview) {
                preferPreview = false;
                return previews.removeFirst();
            }
            preferPreview = true;
            return priority.removeFirst();
        }
        if (!regular.isEmpty()) {
            priorityStreak = 0;
            return regular.removeFirst();
        }
        return null;
    }

    private static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var scheduler = SERVERS.get(server);
        if (scheduler != null && (!scheduler.regular.isEmpty() || !scheduler.priority.isEmpty()
                || !scheduler.previews.isEmpty())) {
            scheduler.advance(server.getAverageTickTimeNanos() / 1_000_000.0,
                    BulkConfig.BACKGROUND_MICROSECONDS.get() * 1000L, BulkConfig.BACKGROUND_TYPES.get(),
                    BulkConfig.ADAPTIVE_BACKGROUND.get());
        }
    }

    private static void stop(ServerStoppedEvent event) {
        SERVERS.remove(event.getServer());
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("absdiagnostics").requires(source -> source.hasPermission(2))
                .executes(context -> {
                    var status = get(context.getSource().getServer()).status();
                    context.getSource().sendSuccess(() -> Component.translatable("command.advanced_bulk_storage.scheduler",
                            status.queued(), status.registered(), status.registered() - status.queued(), status.bandPercent(),
                            status.budgetNanos() / 1000, status.elapsedNanos() / 1000, status.keys()), false);
                    return status.queued();
                }));
    }
}
