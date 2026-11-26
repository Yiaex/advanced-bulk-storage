package dev.advancedbulkstorage.storage;

import appeng.api.stacks.AEItemKey;
import com.mojang.serialization.DynamicOps;
import java.util.LinkedHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

final class CompressionPool {
    final CompressionCatalog.Chain chain;
    long units;

    CompressionPool(CompressionCatalog.Chain chain, long units) {
        this.chain = chain;
        this.units = units;
    }

    long factor(AEItemKey key, boolean enabled) {
        return chain.base().equals(key) ? 1
                : enabled && chain.equals(CompressionCatalog.lookup(chain.base())) ? chain.factor(key) : 0;
    }

    CompoundTag encode(DynamicOps<Tag> ops) {
        var record = new CompoundTag();
        record.putLong("units", units);
        record.put("base", AEItemKey.CODEC.encodeStart(ops, chain.base()).getOrThrow());
        var forms = new ListTag();
        chain.factors().forEach((key, factor) -> {
            var form = new CompoundTag();
            form.put("key", AEItemKey.CODEC.encodeStart(ops, key).getOrThrow());
            form.putLong("factor", factor);
            forms.add(form);
        });
        record.put("forms", forms);
        return record;
    }

    static @Nullable CompressionPool decode(CompoundTag record, DynamicOps<Tag> ops) {
        if (!record.contains("units", Tag.TAG_LONG) || record.getLong("units") <= 0
                || !(record.get("forms") instanceof ListTag list) || list.size() < 2
                || list.size() > CompressionCatalog.MAX_FORMS || list.getElementType() != Tag.TAG_COMPOUND) {
            return null;
        }
        var base = AEItemKey.CODEC.parse(ops, record.getCompound("base")).result().orElse(null);
        var factors = new LinkedHashMap<AEItemKey, Long>();
        for (int i = 0; i < list.size(); i++) {
            var row = list.getCompound(i);
            var key = AEItemKey.CODEC.parse(ops, row.getCompound("key")).result().orElse(null);
            long factor = row.getLong("factor");
            if (key == null || !row.contains("factor", Tag.TAG_LONG) || factor <= 0
                    || factors.putIfAbsent(key, factor) != null) {
                return null;
            }
        }
        if (base == null || factors.getOrDefault(base, 0L) != 1L
                || new java.util.HashSet<>(factors.values()).size() != factors.size()) {
            return null;
        }
        return new CompressionPool(new CompressionCatalog.Chain(base, factors), record.getLong("units"));
    }
}
