package dev.advancedbulkstorage.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.device.BulkDriveEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public final class BulkDriveMenu extends AEBaseMenu {
    private final BulkDriveEntity host;
    private int nextEdit;
    @GuiSync(1) public int priority;
    @GuiSync(2) public boolean powered;

    public static MenuType<BulkDriveMenu> createType() {
        return MenuTypeBuilder.create(BulkDriveMenu::new, BulkDriveEntity.class)
                .withMenuTitle(BulkDriveEntity::getName).buildUnregistered(BulkStorageMod.id("bulk_storage_drive"));
    }

    public BulkDriveMenu(MenuType<BulkDriveMenu> type, int id, Inventory inventory, BulkDriveEntity host) {
        super(type, id, inventory, host);
        this.host = host;
        createPlayerInventorySlots(inventory);
        for (int slot = 0; slot < BulkDriveEntity.SLOTS; slot++) {
            addSlot(new AppEngSlot(host.getInternalInventory(), slot), SlotSemantics.MACHINE_INPUT);
        }
        registerClientAction("priority", Integer.class, value -> {
            if (value != null && stillValid(getPlayer()) && getPlayer().tickCount >= nextEdit) {
                nextEdit = getPlayer().tickCount + 5;
                host.setPriority(value);
            }
        });
    }

    public void setPriority(String value) {
        try {
            sendClientAction("priority", Integer.parseInt(value));
        } catch (NumberFormatException invalid) {
            // Keep the old value while the player edits an incomplete number.
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && !host.isRemoved() && host.getLevel() == player.level()
                && player.distanceToSqr(host.getBlockPos().getCenter()) <= 64;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            priority = host.priority();
            powered = host.isPowered();
        }
        super.broadcastChanges();
    }
}
