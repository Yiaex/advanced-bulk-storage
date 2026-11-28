package dev.advancedbulkstorage.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import dev.advancedbulkstorage.menu.BulkManagerMenu;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.filter.FilterConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class BulkManagerScreen extends AEBaseScreen<BulkManagerMenu> {
    private final AE2Button[] rows = new AE2Button[BulkManagerMenu.PAGE_SIZE];
    private final ItemStack[] icons = new ItemStack[BulkManagerMenu.PAGE_SIZE];
    private BulkManagerMenu.View view;
    private AETextField search;
    private AE2Button back;
    private AE2Button next;
    private AE2Button previous;
    private AE2Button scope;
    private AE2Button order;
    private AE2Button details;
    private AE2Button operation;
    private AE2Button target;
    private AE2Button apply;
    private AETextField value;
    private int operationIndex;
    private boolean batch;
    private int armedToken = -1;
    private boolean showDetails;
    private String last = "";
    private static final String[] OPERATIONS = {"filter", "limit", "enable_sort", "disable_sort"};

    public BulkManagerScreen(BulkManagerMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        view = menu.view();
        setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Override protected void init() {
        super.init();
        search = addRenderableWidget(new AETextField(style, font, leftPos + 8, topPos + 20, 166, 13));
        search.setPlaceholder(text("search"));
        search.setMaxLength(128);
        button(179, 20, 65, text("search"), () -> menu.request("search", search.getValue()));
        for (int i = 0; i < rows.length; i++) {
            int row = i;
            rows[i] = button(28, 39 + i * 15, 216, Component.empty(), () -> {
                if (row < view.rows().size() && view.cell().isEmpty()) {
                    menu.request("open", view.rows().get(row).id());
                    search.setValue("");
                }
            });
        }
        back = button(8, 133, 38, text("back"), () -> {
            menu.request("back", "");
            search.setValue("");
        });
        previous = button(49, 133, 18, Component.literal("<"), () -> menu.request("previous", ""));
        next = button(70, 133, 18, Component.literal(">"), () -> menu.request("next", ""));
        scope = button(92, 133, 73, text("all"), () -> menu.request("scope", ""));
        order = button(169, 133, 75, text("position"), () -> menu.request("order", ""));
        details = button(169, 133, 75, text("details"), () -> {
            showDetails = !showDetails;
            update();
        });
        value = addRenderableWidget(new AETextField(style, font, leftPos + 8, topPos + 172, 236, 14));
        value.setMaxLength(FilterConfig.MAX_INPUT_LENGTH);
        value.setPlaceholder(text("configuration"));
        operation = button(8, 190, 86, text("op_filter"), () -> {
            operationIndex = (operationIndex + 1) % OPERATIONS.length;
            loadValue();
            controls();
        });
        target = button(98, 190, 72, text("target_page"), () -> {
            batch = !batch;
            armedToken = menu.viewToken;
            controls();
        });
        apply = button(174, 190, 70, text("apply"), () -> menu.apply(OPERATIONS[operationIndex],
                value.getValue(), view.cell().isEmpty() && batch));
        update();
    }

    private AE2Button button(int x, int y, int w, Component label, Runnable task) {
        return addRenderableWidget(new AE2Button(leftPos + x, topPos + y, w, 13, label, b -> task.run()));
    }

    @Override public void containerTick() {
        super.containerTick();
        if (!last.equals(menu.result)) {
            update();
        }
        apply.active = canApply() && !menu.operationStatus.equals("working");
    }

    private void update() {
        last = menu.result;
        var incoming = menu.view();
        boolean changedCell = !incoming.cell().equals(view.cell());
        view = incoming;
        if (changedCell) {
            batch = false;
            showDetails = false;
            loadValue();
        }
        if (armedToken != menu.viewToken) batch = false;
        for (int i = 0; i < rows.length; i++) {
            var button = rows[i];
            icons[i] = ItemStack.EMPTY;
            button.visible = !showDetails && i < view.rows().size();
            if (button.visible) {
                var row = view.rows().get(i);
                String label = view.cell().isEmpty() ? text("usage_row", row.name(), row.amount(), row.capacity()).getString()
                        : row.name() + (row.pool() ? " *" : "") + " × " + (row.stock() == null ? row.amount() : row.stock());
                button.setMessage(Component.literal(font.plainSubstrByWidth(label, 208)));
                var extra = row.id().isEmpty() ? Component.literal(row.item()).append(row.pool()
                        ? Component.literal("\n").append(text("pool")) : Component.empty())
                        : text(row.state()).append(" · ").append(row.position()).append("\n").append(row.id());
                button.setTooltip(Tooltip.create(Component.literal(label + "\n").append(extra)));
                var id = ResourceLocation.tryParse(row.item());
                if (id != null) {
                    icons[i] = BuiltInRegistries.ITEM.getOptional(id).map(item -> item.getDefaultInstance()).orElse(ItemStack.EMPTY);
                }
            }
        }
        back.active = !view.cell().isEmpty();
        previous.active = view.page() > 0;
        next.active = (view.page() + 1) * BulkManagerMenu.PAGE_SIZE < view.total();
        controls();
    }

    private void loadValue() {
        var info = view.details();
        String stored = info == null ? "" : operationIndex == 0 ? info.filter() : operationIndex == 1 ? info.limits() : "";
        value.setValue(stored);
    }

    private boolean canApply() {
        var info = view.details();
        return view.cell().isEmpty() ? batch && armedToken == menu.viewToken && !view.rows().isEmpty()
                : info != null && info.canEdit() && (operationIndex != 1 || info.limitCard());
    }

    private void controls() {
        boolean list = view.cell().isEmpty();
        scope.visible = list;
        order.visible = list;
        details.visible = !list && view.details() != null;
        details.setMessage(text(showDetails ? "stock" : "details"));
        if (view.scope() < 3) {
            scope.setMessage(text(new String[]{"all", "online", "attention"}[view.scope()]));
        } else {
            var name = BulkStorageMod.CELLS.get(CellTier.values()[view.scope() - 3]).get().getDescription();
            scope.setMessage(Component.literal(font.plainSubstrByWidth(name.getString(), 67)));
        }
        order.setMessage(text(new String[]{"position", "name", "occupancy"}[view.order()]));
        target.visible = list;
        target.setMessage(text(batch ? "page_armed" : "target_page"));
        target.setTooltip(Tooltip.create(text("page_help")));
        operation.setMessage(text("op_" + OPERATIONS[operationIndex]));
        value.visible = operationIndex < 2;
        var info = view.details();
        if (info != null) {
            var hover = text("occupancy_stats", info.used(), info.maximum(), info.pools())
                    .append("\n").append(text("drive_stats", info.position(), info.priority(), info.power(), info.bindings()))
                    .append("\n").append(text("sort_stats", text(info.sorting() ? "enabled" : "disabled"), info.sortRate()))
                    .append("\n").append(text("discard_stats", text(info.discardConfigured() ? "enabled" : "disabled"), info.discarded()))
                    .append("\n").append(text("upgrades", String.join(", ", info.upgrades())));
            details.setTooltip(Tooltip.create(hover));
        }
        apply.active = canApply() && !menu.operationStatus.equals("working");
    }

    @Override public void drawFG(GuiGraphics g, int left, int top, int x, int y) {
        super.drawFG(g, left, top, x, y);
        for (int i = 0; i < icons.length; i++) {
            if (!icons[i].isEmpty()) g.renderFakeItem(icons[i], 8, 38 + 15 * i);
        }
        var info = view.details();
        if (showDetails && info != null) {
            var detailLines = java.util.List.of(
                    text("occupancy_stats", info.used(), info.maximum(), info.pools()),
                    text("drive_stats", info.position(), info.priority(), info.power(), info.bindings()),
                    text("sort_stats", text(info.sorting() ? "enabled" : "disabled"), info.sortRate()),
                    text("discard_stats", text(info.discardConfigured() ? "enabled" : "disabled"), info.discarded()),
                    text("upgrades", String.join(", ", info.upgrades())),
                    text("active_filter", info.filter()),
                    text("active_limits", info.limits()));
            for (int i = 0; i < detailLines.size(); ++i) {
                g.drawString(font, font.plainSubstrByWidth(detailLines.get(i).getString(), 236), 8, 39 + i * 12,
                        style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);
            }
        }
        var footer = !view.online() ? text("offline") : view.cell().isEmpty() ? text("results", view.total())
                : Component.literal(view.cell());
        g.drawString(font, font.plainSubstrByWidth(footer.getString(), 236), 8, 153,
                style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);
        var status = switch (menu.operationStatus) {
            case "working", "cancelled" -> text(menu.operationStatus);
            case "" -> Component.empty();
            default -> text("applied", menu.operationStatus);
        };
        g.drawString(font, status, 8, 211, style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);
    }

    static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable("gui.advanced_bulk_storage.manager." + key, args);
    }
}
