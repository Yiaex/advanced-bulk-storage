package dev.advancedbulkstorage.mixin;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.EncodedSmithingTablePattern;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.storage.BulkCellItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AESmithingTablePattern.class, remap = false)
abstract class BulkSmithingPatternMixin {
    @ModifyVariable(method = "<init>", at = @At("STORE"), index = 3)
    private static @Nullable EncodedSmithingTablePattern advancedBulkStorage$decodeBlankCellPattern(
            @Nullable EncodedSmithingTablePattern pattern) {
        return advancedBulkStorage$normalizeBlankCellPattern(pattern);
    }

    @Inject(method = "encode", at = @At("RETURN"))
    private static void advancedBulkStorage$encodeBlankCellPattern(ItemStack stack, RecipeHolder<SmithingRecipe> recipe,
            AEItemKey template, AEItemKey base, AEItemKey addition, AEItemKey output, boolean substitutes,
            CallbackInfo callback) {
        var pattern = stack.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        var normalized = advancedBulkStorage$normalizeBlankCellPattern(pattern);
        if (normalized != null && normalized != pattern) {
            stack.set(AEComponents.ENCODED_SMITHING_TABLE_PATTERN, normalized);
        }
    }

    @Unique
    private static @Nullable EncodedSmithingTablePattern advancedBulkStorage$normalizeBlankCellPattern(
            @Nullable EncodedSmithingTablePattern pattern) {
        if (pattern == null || !(pattern.base().getItem() instanceof BulkCellItem source)
                || !(pattern.resultItem().getItem() instanceof BulkCellItem target)
                || source.tier().ordinal() + 1 != target.tier().ordinal()
                || !BulkStorageMod.id(target.tier().id()).equals(pattern.recipeId())) {
            return pattern;
        }
        var id = pattern.base().get(BulkStorageMod.CELL_ID);
        if (id == null || !id.equals(pattern.resultItem().get(BulkStorageMod.CELL_ID))) {
            return pattern;
        }
        var base = pattern.base().copy();
        var output = pattern.resultItem().copy();
        base.remove(BulkStorageMod.CELL_ID);
        output.remove(BulkStorageMod.CELL_ID);
        // A drive can assign a UUID to a blank cell. Only its pattern snapshots can be normalized.
        if (!ItemStack.matches(base, source.getDefaultInstance())
                || !ItemStack.matches(output, target.getDefaultInstance())) {
            return pattern;
        }
        return new EncodedSmithingTablePattern(pattern.template(), base, pattern.addition(), output,
                pattern.canSubstitute(), pattern.recipeId());
    }
}
