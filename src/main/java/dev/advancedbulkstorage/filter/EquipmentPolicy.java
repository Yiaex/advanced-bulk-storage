package dev.advancedbulkstorage.filter;

import dev.advancedbulkstorage.BulkStorageMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

final class EquipmentPolicy {
    private static final TagKey<Item> EQUIPMENT = TagKey.create(Registries.ITEM, BulkStorageMod.id("equipment"));
    private static final TagKey<Item> EXCEPTIONS = TagKey.create(Registries.ITEM, BulkStorageMod.id("equipment_exceptions"));

    private EquipmentPolicy() {}

    static boolean isEquipment(Holder<Item> holder) {
        if (holder.is(EXCEPTIONS)) {
            return false;
        }
        Item item = holder.value();
        return holder.is(EQUIPMENT)
                || item instanceof ArmorItem || item instanceof DiggerItem || item instanceof SwordItem
                || item instanceof ProjectileWeaponItem || item instanceof ShieldItem || item instanceof TridentItem
                || item instanceof Equipable && item.getDefaultInstance().getMaxStackSize() == 1;
    }
}
