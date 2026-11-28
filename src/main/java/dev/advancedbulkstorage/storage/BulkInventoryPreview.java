package dev.advancedbulkstorage.storage;

import appeng.api.stacks.AEItemKey;
import com.mojang.serialization.DynamicOps;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;

public record BulkInventoryPreview(int total, List<Entry> entries, boolean invalid) {
    public static final int MAX_ENTRIES = 54;
    private static final int MAX_REMOTE_CHARS = 24_000;
    public static final BulkInventoryPreview EMPTY = new BulkInventoryPreview(0, List.of(), false);
    public static final BulkInventoryPreview INVALID = new BulkInventoryPreview(0, List.of(), true);
    private static final String[] UNITS = {"", "K", "M", "G", "T", "P", "E"};
    private static final Comparator<EncodedEntry> ORDER = Comparator.comparing(EncodedEntry::amount).reversed()
            .thenComparing(EncodedEntry::id)
            .thenComparingInt(entry -> entry.key().hashCode())
            .thenComparing(entry -> entry.key().toString());

    public BulkInventoryPreview {
        entries = List.copyOf(entries);
    }

    public record Entry(AEItemKey key, BigInteger exactAmount, String label, boolean pool) {
        public long amount() { return StockAmount.toLong(exactAmount); }
    }

    static final class Selection {
        private record Candidate(AEItemKey key, BigInteger amount, boolean pool) {}

        private static final Comparator<Candidate> BY_AMOUNT = Comparator.comparing(Candidate::amount).reversed()
                .thenComparing(row -> row.key().getId())
                .thenComparingInt(row -> row.key().hashCode())
                .thenComparing(Candidate::pool);
        private final PriorityQueue<Candidate> entries = new PriorityQueue<>(MAX_ENTRIES, BY_AMOUNT.reversed());

        void add(AEItemKey key, BigInteger amount, boolean pool) {
            if (entries.size() == MAX_ENTRIES && amount.compareTo(entries.element().amount()) < 0) {
                return;
            }
            var row = new Candidate(key, amount, pool);
            if (entries.size() == MAX_ENTRIES) {
                if (BY_AMOUNT.compare(row, entries.element()) >= 0) {
                    return;
                }
                entries.remove();
            }
            entries.add(row);
        }

        List<Entry> entries() {
            return entries.stream().sorted(BY_AMOUNT)
                    .map(row -> new Entry(row.key(), row.amount(), formatAmount(row.amount()), row.pool())).toList();
        }
    }

    // A read-only payload for one hovered disk. It is never installed on an ItemStack.
    static final class RemoteEncoder {
        private final StringBuilder wire;
        private final DynamicOps<Tag> ops;

        RemoteEncoder(int total, boolean invalid, DynamicOps<Tag> ops) {
            this.wire = new StringBuilder().append(invalid ? -1 : total);
            this.ops = ops;
        }

        void add(Entry entry) {
            var encoded = AEItemKey.CODEC.encodeStart(ops, entry.key()).result().orElse(null);
            if (encoded == null) {
                return;
            }
            String key = encoded.toString();
            int room = MAX_REMOTE_CHARS - wire.length() - key.length() - 12;
            if (room <= 0 || entry.exactAmount().bitLength() > 4L * room) return;
            String amount = entry.exactAmount().toString();
            if (amount.length() <= room) {
                // SNBT escapes newlines only according to its own rules, so frame by character length.
                wire.append(';').append(entry.pool() ? 'p' : 'i').append(amount)
                        .append(';').append(key.length()).append(';').append(key);
            }
        }

        String finish() {
            return wire.toString();
        }
    }

    public static BulkInventoryPreview decodeRemote(String wire, DynamicOps<Tag> ops) {
        if (wire.isEmpty() || wire.length() > MAX_REMOTE_CHARS) {
            return INVALID;
        }
        try {
            int cursor = wire.indexOf(';');
            int total = Integer.parseInt(wire.substring(0, cursor < 0 ? wire.length() : cursor));
            if (total < 0) {
                return INVALID;
            }
            var entries = new ArrayList<Entry>();
            while (cursor >= 0) {
                if (entries.size() == Math.min(total, MAX_ENTRIES)) {
                    return INVALID;
                }
                int start = cursor + 1;
                int next = wire.indexOf(';', start);
                boolean pool = wire.charAt(start) == 'p';
                var amount = new BigInteger(wire.substring(start + 1, next));
                int keyStart = wire.indexOf(';', next + 1) + 1;
                int length = Integer.parseInt(wire.substring(next + 1, keyStart - 1));
                if (length <= 0 || length > wire.length() - keyStart || amount.signum() <= 0) {
                    return INVALID;
                }
                int end = keyStart + length;
                var key = AEItemKey.CODEC.parse(ops, TagParser.parseTag(wire.substring(keyStart, end))).result().orElse(null);
                if (key != null) {
                    entries.add(new Entry(key, amount, formatAmount(amount), pool));
                }
                if (end < wire.length() && wire.charAt(end) != ';') {
                    return INVALID;
                }
                cursor = end == wire.length() ? -1 : end;
            }
            return total == 0 ? EMPTY : new BulkInventoryPreview(total, entries, false);
        } catch (IllegalArgumentException | IndexOutOfBoundsException | CommandSyntaxException invalid) {
            return INVALID;
        }
    }

    public int visibleCount(boolean expanded, int normalLimit, int expandedLimit, int columns, int rows) {
        int limit = expanded ? Math.max(normalLimit, expandedLimit) : normalLimit;
        return Math.min(entries.size(), Math.min(limit, columns * rows));
    }

    private record EncodedEntry(String id, CompoundTag key, BigInteger amount, boolean pool) {}

    @SuppressWarnings("deprecation")
    public static BulkInventoryPreview read(CustomData data, DynamicOps<Tag> ops) {
        if (data.isEmpty()) {
            return EMPTY;
        }
        var contents = data.getUnsafe();
        int version = contents.getInt("version");
        if (!contents.contains("version", Tag.TAG_INT)
                || version < BulkCellInventory.DATA_VERSION || version > BulkCellInventory.WIDE_VERSION) {
            return new BulkInventoryPreview(0, List.of(), true);
        }
        if (!(contents.get("items") instanceof net.minecraft.nbt.ListTag rows)
                || !rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND) {
            return new BulkInventoryPreview(0, List.of(), true);
        }
        var pools = new net.minecraft.nbt.ListTag();
        boolean wide = version == BulkCellInventory.WIDE_VERSION;
        if (version == BulkCellInventory.POOL_VERSION || wide && contents.contains("pools")) {
            if (!(contents.get("pools") instanceof net.minecraft.nbt.ListTag list)
                    || list.isEmpty() || list.getElementType() != Tag.TAG_COMPOUND) {
                return INVALID;
            }
            pools = list;
        }
        int total = rows.size() + pools.size();
        var largest = new PriorityQueue<EncodedEntry>(MAX_ENTRIES, ORDER.reversed());
        for (int index = 0; index < total; index++) {
            boolean pool = index >= rows.size();
            var row = pool ? pools.getCompound(index - rows.size()) : rows.getCompound(index);
            String quantity = pool ? "units" : "amount";
            String item = pool ? "base" : "key";
            var amount = StockAmount.read(row, quantity, wide);
            if (amount == null || !row.contains(item, Tag.TAG_COMPOUND)) {
                return new BulkInventoryPreview(total, List.of(), true);
            }
            if (largest.size() == MAX_ENTRIES && amount.compareTo(largest.element().amount()) < 0) {
                continue;
            }
            var key = row.getCompound(item);
            var entry = new EncodedEntry(key.getString("id"), key, amount, pool);
            if (largest.size() < MAX_ENTRIES) {
                largest.add(entry);
            } else if (ORDER.compare(entry, largest.element()) < 0) {
                largest.remove();
                largest.add(entry);
            }
        }
        var entries = new java.util.ArrayList<Entry>(largest.size());
        for (var row : largest.stream().sorted(ORDER).toList()) {
            var key = AEItemKey.CODEC.parse(ops, row.key()).result().orElse(null);
            if (key == null) {
                return new BulkInventoryPreview(total, List.of(), true);
            }
            entries.add(new Entry(key, row.amount(), formatAmount(row.amount()), row.pool()));
        }
        return new BulkInventoryPreview(total, entries, false);
    }

    public static String formatAmount(BigInteger amount) {
        if (amount.bitLength() <= 63) return formatAmount(amount.longValue());
        // Keep the label narrow even for imported quantities with many digits.
        if (amount.bitLength() > 1024) return StockAmount.display(amount);
        String digits = amount.toString();
        if (digits.length() <= 21) {
            int wholeDigits = digits.length() - 18;
            if (wholeDigits == 1 && digits.charAt(1) != '0') {
                return digits.substring(0, 1) + "." + digits.charAt(1) + "E";
            }
            return digits.substring(0, wholeDigits) + "E";
        }
        return digits.substring(0, 1) + "." + digits.charAt(1) + "e" + (digits.length() - 1);
    }

    public static String formatAmount(long amount) {
        int unit = 0;
        long scale = 1;
        while (unit + 1 < UNITS.length && amount / scale >= 1000) {
            scale *= 1000;
            unit++;
        }
        long whole = amount / scale;
        if (unit > 0 && whole < 10) {
            long tenth = amount / (scale / 10) % 10;
            if (tenth != 0) {
                return whole + "." + tenth + UNITS[unit];
            }
        }
        return whole + UNITS[unit];
    }
}
