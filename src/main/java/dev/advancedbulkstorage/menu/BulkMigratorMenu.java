package dev.advancedbulkstorage.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.ResizableSlot;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.device.BulkMigratorEntity;
import dev.advancedbulkstorage.filter.FilterConfig;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public final class BulkMigratorMenu extends AEBaseMenu {
    private final BulkMigratorEntity host;
    private int nextAction;
    @GuiSync(1) public long rate;
    @GuiSync(2) public long recent;
    @GuiSync(3) public long total;
    @GuiSync(4) public int keys;
    @GuiSync(5) public String state = "paused";
    @GuiSync(6) public boolean running;
    @GuiSync(7) public boolean reversed;
    @GuiSync(8) public String power = "fixed";
    @GuiSync(9) public String signal = "ignore";
    @GuiSync(10) public String expression = "";
    @GuiSync(11) public String message = "";
    @GuiSync(12) public String scope = "slots";
    @GuiSync(13) public String origin = "";
    @GuiSync(14) public String destination = "";
    @GuiSync(15) public long remaining = -1;
    @GuiSync(16) public long eta = -1;

    public record Edit(String action, String argument) {}

    public static MenuType<BulkMigratorMenu> createType() {
        return MenuTypeBuilder.create(BulkMigratorMenu::new, BulkMigratorEntity.class)
                .withMenuTitle(BulkMigratorEntity::getName).buildUnregistered(BulkStorageMod.id("bulk_storage_migrator"));
    }

    private BulkMigratorMenu(MenuType<BulkMigratorMenu> type, int id, Inventory inventory, BulkMigratorEntity host) {
        super(type, id, inventory, host);
        this.host = host;
        createPlayerInventorySlots(inventory);
        var cells = host.getInternalInventory();
        addSlot(new ResizableSlot(cells, 0, "migratorCellA"), SlotSemantics.MACHINE_INPUT);
        addSlot(new ResizableSlot(cells, 1, "migratorCellB"), SlotSemantics.MACHINE_INPUT);
        for (int slot = 0; slot < host.upgrades().size(); slot++) {
            addSlot(new AppEngSlot(host.upgrades(), slot), SlotSemantics.UPGRADE);
        }
        registerClientAction("edit", Edit.class, this::edit);
    }

    public void request(String action, String argument) {
        sendClientAction("edit", new Edit(action, argument));
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && !host.isRemoved() && host.getLevel() == player.level()
                && player.distanceToSqr(host.getBlockPos().getCenter()) <= 64;
    }

    private void edit(Edit edit) {
        if (edit == null || edit.action() == null || edit.argument() == null
                || edit.argument().length() > FilterConfig.MAX_INPUT_LENGTH || !stillValid(getPlayer())
                || getPlayer().tickCount < nextAction) {
            return;
        }
        nextAction = getPlayer().tickCount + 2;
        message = "";
        switch (edit.action()) {
            case "toggle" -> host.toggle();
            case "direction" -> host.reverse();
            case "power" -> host.cycleEnergy();
            case "signal" -> host.cycleSignal();
            case "scope" -> host.cycleScope();
            case "origin" -> host.cycleEndpoint(true);
            case "destination" -> host.cycleEndpoint(false);
            case "editor" -> appeng.menu.MenuOpener.open(BulkStorageMod.FILTER_EDITOR.get(), getPlayer(),
                    appeng.menu.locator.MenuLocators.forBlockEntity(host));
            case "filter" -> {
                var failure = host.setFilter(edit.argument());
                message = failure == null ? "saved" : "error." + failure;
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            rate = host.rate();
            recent = host.sampledRate();
            total = host.totalMoved();
            keys = host.remainingKeys();
            state = token(host.status());
            running = host.configured();
            reversed = host.reversed();
            power = token(host.energyMode());
            signal = token(host.signalMode());
            expression = host.expression();
            scope = token(host.scope());
            origin = host.endpointLabel(true);
            destination = host.endpointLabel(false);
            remaining = host.remainingItems();
            eta = remaining > 0 && recent > 0 && host.status() == BulkMigratorEntity.Status.RUNNING
                    ? Math.ceilDiv(remaining, recent * 20) : -1;
        }
        super.broadcastChanges();
    }

    private static String token(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT);
    }
}
