package dev.advancedbulkstorage.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import com.google.gson.Gson;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.device.BulkManagerEntity;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.migration.BulkScheduler;
import dev.advancedbulkstorage.storage.BulkCellIndex;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.LimitProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public final class BulkManagerMenu extends AEBaseMenu {
    public static final int PAGE_SIZE = 6;
    private static final Gson JSON = new Gson();
    private final BulkManagerEntity host;
    private String search = "";
    private int page;
    private int nextAction;
    private UUID selected;
    private long revision = -1;
    private BulkCellInventory lastInventory;
    private List<Row> lastStock = List.of();
    private List<DisplayedTarget> visible = List.of();
    private int scope;
    private int order;
    private BulkScheduler scheduler;
    private BulkScheduler.Job editing;
    @GuiSync(1) public String result = "";
    @GuiSync(2) public int viewToken;
    @GuiSync(3) public String operationStatus = "";

    public record Request(String type, String value) {}
    public record Change(String operation, String text, boolean batch, int token) {}
    private record DisplayedTarget(UUID id, BulkCellIndex.Mount mount) {}
    private record QueuedTarget(DisplayedTarget target, BulkCellInventory.Configuration before) {}
    public record Row(String id, String name, String item, long amount, String position, String state,
                      int capacity, boolean pool) {}
    public record Details(String filter, String limits, List<String> upgrades, int used, int maximum, int pools,
                          long sortRate, long discarded, int priority, double power, long bindings, String position,
                          boolean sorting, boolean limitCard, boolean canEdit, boolean discardConfigured) {}
    public record View(String cell, boolean online, int page, int total, List<Row> rows, int scope, int order,
                       Details details) {}

    public static MenuType<BulkManagerMenu> createType() {
        return MenuTypeBuilder.create(BulkManagerMenu::new, BulkManagerEntity.class)
                .withMenuTitle(BulkManagerEntity::getName).buildUnregistered(BulkStorageMod.id("bulk_storage_manager"));
    }

    private BulkManagerMenu(MenuType<BulkManagerMenu> type, int id, Inventory inventory, BulkManagerEntity host) {
        super(type, id, inventory, host);
        this.host = host;
        registerClientAction("browse", Request.class, this::browse);
        registerClientAction("change", Change.class, this::change);
        if (isServerSide()) publish();
    }

    public void request(String type, String value) { sendClientAction("browse", new Request(type, value)); }

    public void apply(String operation, String text, boolean batch) {
        sendClientAction("change", new Change(operation, text, batch, viewToken));
    }

    public View view() {
        return result.isEmpty() ? new View("", false, 0, 0, List.of(), 0, 0, null) : JSON.fromJson(result, View.class);
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && !host.isRemoved() && host.getLevel() == player.level()
                && player.distanceToSqr(host.getBlockPos().getCenter()) <= 64;
    }

    private void browse(Request request) {
        if (request == null || request.type() == null || request.value() == null || request.value().length() > 128
                || !stillValid(getPlayer()) || getPlayer().tickCount < nextAction) return;
        nextAction = getPlayer().tickCount + 3;
        switch (request.type()) {
            case "search" -> { search = request.value().strip().toLowerCase(Locale.ROOT); page = 0; }
            case "back" -> { selected = null; page = 0; search = ""; }
            case "previous" -> page = Math.max(0, page - 1);
            case "next" -> page++;
            case "scope" -> { scope = (scope + 1) % (3 + CellTier.values().length); page = 0; }
            case "order" -> { order = (order + 1) % 3; page = 0; }
            case "open" -> {
                var index = host.index();
                if (index == null || request.value().length() != 36) return;
                try {
                    var id = UUID.fromString(request.value());
                    if (index.mounted(id) == null) return;
                    selected = id;
                    page = 0;
                    search = "";
                } catch (IllegalArgumentException invalid) {
                    return;
                }
            }
            case "refresh" -> {}
            default -> { return; }
        }
        publish();
    }

    private void change(Change change) {
        if (change == null || change.operation() == null || change.text() == null
                || change.text().length() > FilterConfig.MAX_INPUT_LENGTH || change.token() != viewToken
                || !(getPlayer() instanceof ServerPlayer player) || !stillValid(player) || player.containerMenu != this
                || editing != null || player.tickCount < nextAction
                || !List.of("filter", "limit", "enable_sort", "disable_sort").contains(change.operation())) return;
        var original = host.index();
        if (visible.isEmpty() || original == null || change.batch() != (selected == null)) return;
        var targets = visible.stream().map(target -> new QueuedTarget(target,
                mayEdit(player, target.mount()) && original.find(target.id()) == target.mount()
                        ? target.mount().inventory().configuration() : null)).toList();
        nextAction = player.tickCount + 5;
        operationStatus = "working";
        var remaining = targets.iterator();
        scheduler = BulkScheduler.get(player.server);
        editing = new BulkScheduler.Job() {
            private int edited;

            @Override public boolean run(BulkScheduler.Allowance allowance) {
                try {
                    return applyNext(allowance);
                } catch (RuntimeException failure) {
                    finishEdit("cancelled");
                    throw failure;
                }
            }

            private boolean applyNext(BulkScheduler.Allowance allowance) {
                if (!stillValid(player) || player.containerMenu != BulkManagerMenu.this || host.index() != original) {
                    finishEdit("cancelled");
                    return false;
                }
                if (remaining.hasNext() && allowance.takeKey()) {
                    var next = remaining.next();
                    var mount = original.find(next.target().id());
                    if (mount == next.target().mount() && mayEdit(player, mount)
                            && mount.inventory().configuration().equals(next.before())
                            && mount.inventory().configure(change.operation(), change.text())) {
                        original.configurationChanged(mount);
                        edited++;
                    }
                }
                if (remaining.hasNext()) return true;
                finishEdit(edited + "/" + targets.size());
                publish();
                return false;
            }
        };
        scheduler.wake(editing, true);
    }

    private static boolean mayEdit(Player player, BulkCellIndex.Mount mount) {
        return mount != null && mount.drive().getLevel() == player.level()
                && !player.isSpectator() && player.mayInteract(player.level(), mount.drive().getBlockPos());
    }

    private void finishEdit(String summary) {
        operationStatus = summary;
        if (editing != null) {
            scheduler.cancel(editing);
            editing = null;
        }
    }

    @Override
    public void removed(Player player) {
        finishEdit("");
        super.removed(player);
    }

    private void publish() {
        var index = host.index();
        var rows = new ArrayList<Row>();
        String title = "";
        Details details = null;
        var mount = index == null || selected == null ? null : index.mounted(selected);
        if (index != null && selected == null) {
            for (var candidate : index.mountedCells()) {
                var inventory = candidate.inventory();
                boolean online = index.find(candidate.id()) == candidate;
                if (scope == 1 && !online || scope == 2 && online && !inventory.quarantined()
                        || scope >= 3 && inventory.tier().ordinal() != scope - 3) continue;
                String position = candidate.drive().getBlockPos().toShortString() + " #" + (candidate.slot() + 1);
                String name = candidate.name().getString() + " · " + position;
                if (contains(name) || candidate.id().toString().contains(search) || contains(inventory.filterExpression())
                        || inventory.tier().id().contains(search)) {
                    rows.add(new Row(candidate.id().toString(), shorten(name), BulkStorageMod.id(inventory.tier().id()).toString(),
                            inventory.types(), position, inventory.quarantined() ? "quarantined" : online ? "online" : "offline",
                            inventory.maximumTypes(), false));
                }
            }
            Comparator<Row> sort = switch (order) {
                case 1 -> Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER);
                case 2 -> Comparator.comparingDouble((Row row) -> (double) row.amount() / row.capacity()).reversed();
                default -> Comparator.comparing(Row::position);
            };
            rows.sort(sort.thenComparing(Row::id));
        } else if (mount != null) {
            title = shorten(mount.name().getString());
            boolean online = index.find(selected) == mount;
            var inventory = mount.inventory();
            if (online) {
                if (lastInventory != inventory || revision != inventory.revision()) {
                    var stock = new ArrayList<Row>();
                    inventory.visitPhysicalStock((key, amount, pool) -> stock.add(new Row("", shorten(key.getDisplayName().getString()),
                            key.getId().toString(), amount, "", "", 0, pool)));
                    stock.sort(Comparator.comparingLong(Row::amount).reversed().thenComparing(Row::item));
                    lastStock = stock;
                    lastInventory = inventory;
                    revision = inventory.revision();
                }
                lastStock.stream().filter(row -> contains(row.name()) || row.item().contains(search)).forEach(rows::add);
            }
            String limits = inventory.limitExpression();
            details = new Details(inventory.filterExpression(), limits, inventory.upgradeIds(), inventory.types(),
                    inventory.maximumTypes(), inventory.compressionPools(), inventory.recentSortRate(mount.drive().getLevel().getGameTime()),
                    inventory.discardedItems(), mount.priority(), inventory.getIdleDrain(), index.bindingCount(selected),
                    mount.drive().getBlockPos().toShortString() + " #" + (mount.slot() + 1), inventory.autoSortRate() > 0,
                    inventory.hasLimitUpgrade(), online && mayEdit(getPlayer(), mount) && !inventory.quarantined(),
                    inventory.hasLimitUpgrade() && LimitProfile.parse(limits).hasOverflow());
        } else if (selected != null) {
            title = selected.toString();
        }
        page = Math.clamp(page, 0, Math.max(0, (rows.size() - 1) / PAGE_SIZE));
        int start = page * PAGE_SIZE;
        var shown = rows.subList(start, Math.min(start + PAGE_SIZE, rows.size()));
        var targets = (selected == null ? shown.stream().map(row -> UUID.fromString(row.id())) : java.util.stream.Stream.of(selected))
                .map(id -> new DisplayedTarget(id, index == null ? null : index.mounted(id))).toList();
        if (!targets.equals(visible)) {
            visible = targets;
            viewToken++;
        }
        result = JSON.toJson(new View(title, index != null, page, rows.size(), shown, scope, order, details));
    }

    private boolean contains(String text) { return text.toLowerCase(Locale.ROOT).contains(search); }

    private static String shorten(String value) {
        return value.substring(0, value.offsetByCodePoints(0, Math.min(100, value.codePointCount(0, value.length()))));
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && getPlayer().tickCount % 20 == 0) publish();
        super.broadcastChanges();
    }
}
