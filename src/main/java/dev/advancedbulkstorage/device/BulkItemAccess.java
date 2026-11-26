package dev.advancedbulkstorage.device;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import dev.advancedbulkstorage.BulkStorageMod;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;

/** One face of a bound interface. Counts and the size of each individual call are capped at INT. */
public interface BulkItemAccess {
    BlockCapability<BulkItemAccess, Direction> CAPABILITY =
            BlockCapability.createSided(BulkStorageMod.id("bulk_item_access"), BulkItemAccess.class);

    int count(AEItemKey key);

    int insert(AEItemKey key, int amount, Actionable action);

    record Insertion(int stored, int discarded) {
        public int accepted() { return stored + discarded; }
    }

    default Insertion insertWithOverflow(AEItemKey key, int amount, Actionable action) {
        return new Insertion(insert(key, amount, action), 0);
    }

    int extract(AEItemKey key, int amount, Actionable action);
}
