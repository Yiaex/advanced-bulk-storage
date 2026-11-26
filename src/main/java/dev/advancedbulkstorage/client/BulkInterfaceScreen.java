package dev.advancedbulkstorage.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.menu.BulkInterfaceMenu;
import dev.advancedbulkstorage.storage.BulkInventoryPreview;
import dev.advancedbulkstorage.storage.BulkInventoryTooltip;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class BulkInterfaceScreen extends AEBaseScreen<BulkInterfaceMenu> {
    private static final int LIST_X = 8;
    private static final int LIST_Y = 36;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_WIDTH = 253;
    private static final int TRACK_X = 264;
    private static final int TRACK_HEIGHT = ROW_HEIGHT * BulkInterfaceMenu.PAGE_SIZE;
    private static final int STATUS_Y = LIST_Y + TRACK_HEIGHT + 1;
    private static final int CONTROLS_Y = STATUS_Y + 9;
    private static final int HOVER_SETTLE_TICKS = 2;
    private static final int HOVER_RETRY_TICKS = 8;
    private static final String[] SCOPES = {
            "all", "bound", "unbound", "online", "TAG", "ADVANCED", "MOD", "QUANTUM"
    };
    private static final String[] ORDERS = {"priority", "name", "tier", "position", "usage"};
    private final EnumMap<CellTier, ItemStack> icons = new EnumMap<>(CellTier.class);
    private final BooleanSupplier expandPreview;
    private BulkInterfaceMenu.Page page;
    private AETextField search;
    private AE2Button scopeButton;
    private AE2Button sortButton;
    private AE2Button accessButton;
    private AE2Button faceButton;
    private AE2Button redstoneButton;
    private String lastResponse = "";
    private String previewWire = "";
    private String pointerRow = "";
    private String requestedHover = "";
    private BulkInventoryPreview preview = BulkInventoryPreview.EMPTY;
    private int hoverTicks;
    private int queryTicks;
    private int scope;
    private int sort;
    private int offset;
    private boolean pendingQuery;
    private boolean dragging;

    public BulkInterfaceScreen(BulkInterfaceMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        this(menu, inventory, title, style, BulkInterfaceScreen::hasShiftDown);
    }

    BulkInterfaceScreen(BulkInterfaceMenu menu, Inventory inventory, Component title, ScreenStyle style,
                        BooleanSupplier expandPreview) {
        super(menu, inventory, title, style);
        this.expandPreview = expandPreview;
        page = menu.response();
        for (var tier : CellTier.values()) {
            icons.put(tier, BulkStorageMod.CELLS.get(tier).get().getDefaultInstance());
        }
        setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Override
    protected void init() {
        String existingSearch = search == null ? "" : search.getValue();
        super.init();
        search = addRenderableWidget(new AETextField(style, font, leftPos + 8, topPos + 21, 109, 15));
        search.setMaxLength(BulkInterfaceMenu.SEARCH_LENGTH);
        search.setPlaceholder(label("search"));
        search.setValue(existingSearch);
        search.setResponder(value -> changedQuery());
        scopeButton = button(121, 21, 72, Component.empty(), () -> {
            scope = (scope + 1) % SCOPES.length;
            updateControls();
            changedQuery();
        });
        sortButton = button(197, 21, 75, Component.empty(), () -> {
            sort = (sort + 1) % ORDERS.length;
            updateControls();
            changedQuery();
        });
        accessButton = button(8, CONTROLS_Y, 63, Component.empty(), () -> menu.request("access", ""));
        if (menu.advanced()) {
            faceButton = button(74, CONTROLS_Y, 44, Component.empty(), () -> menu.request("side", ""));
            redstoneButton = button(121, CONTROLS_Y, 75, Component.empty(), () -> menu.request("redstone", ""));
            button(199, CONTROLS_Y, 73, label("whitelist"), () -> menu.request("editor", ""));
        }
        refresh();
    }

    private AE2Button button(int x, int y, int width, Component text, Runnable action) {
        return addRenderableWidget(new AE2Button(leftPos + x, topPos + y, width, 15, text, button -> action.run()));
    }

    private void changedQuery() {
        offset = 0;
        pendingQuery = true;
        queryTicks = 4;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (!lastResponse.equals(menu.responseJson)) {
            refresh();
        }
        if (pendingQuery && --queryTicks <= 0) {
            var entered = search.getValue();
            var normalized = entered.strip().toLowerCase(Locale.ROOT);
            int localMatches = 0;
            for (var tier : CellTier.values()) {
                if (tierName(tier).getString().toLowerCase(Locale.ROOT).contains(normalized)) {
                    localMatches |= 1 << tier.ordinal();
                }
            }
            menu.requestQuery(new BulkInterfaceMenu.Query(entered, SCOPES[scope], ORDERS[sort], offset, localMatches));
            pendingQuery = false;
        }
        boolean differentRow = !pointerRow.equals(requestedHover);
        int wait = differentRow ? HOVER_SETTLE_TICKS : HOVER_RETRY_TICKS;
        if ((differentRow || !pointerRow.equals(menu.previewId)) && ++hoverTicks >= wait) {
            hoverTicks = 0;
            requestedHover = pointerRow;
            menu.requestHover(requestedHover);
        }
    }

    private void refresh() {
        lastResponse = menu.responseJson;
        page = menu.response();
        if (!pendingQuery) {
            offset = page.offset();
        }
        updateControls();
    }

    private void updateControls() {
        var selectedScope = label("scope_" + SCOPES[scope].toLowerCase(Locale.ROOT));
        var order = label("sort_" + ORDERS[sort]);
        caption(scopeButton, selectedScope, 66, label("scope_help", selectedScope));
        caption(sortButton, order, 69, label("sort_help", order));
        var access = label(page.access());
        caption(accessButton, access, 57, label("access_help", access));
        if (menu.advanced()) {
            var face = label(page.face().isEmpty() ? "down" : page.face());
            var redstone = label(page.redstone());
            caption(faceButton, face, 38, label("face_help", face));
            caption(redstoneButton, redstone, 69, redstone);
        }
    }

    private void caption(AE2Button button, Component text, int width, Component tip) {
        button.setMessage(Component.literal(font.plainSubstrByWidth(text.getString(), width)));
        button.setTooltip(Tooltip.create(tip));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var row = rowAt(mouseX, mouseY);
        var id = row != null && row.online() && !pendingQuery ? row.id() : "";
        if (!pointerRow.equals(id)) {
            pointerRow = id;
            hoverTicks = 0;
        }
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        var row = rowAt(mouseX, mouseY);
        if (row == null || !menu.getCarried().isEmpty()) {
            super.renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(displayName(row)).withColor(0x9fe9ed));
        if (!row.name().isEmpty()) {
            lines.add(Component.literal(defaultName(row)).withColor(0xc4cbd5));
        }
        if (row.slot() > 0) {
            lines.add(label("location", row.position(), row.slot()));
        }
        var state = label(row.online() ? "cell_online" : "cell_offline")
                .withColor(row.online() ? 0x72e6bd : 0xf6b380);
        lines.add(state.append(Component.literal("  ·  ").withColor(0xaaaaaa))
                .append(label(row.bound() ? "bound" : "unbound", row.priority()).withColor(0xffffff)));
        if (row.online()) {
            lines.add(label("usage", row.used(), row.capacity()));
        }
        boolean current = row.online() && menu.previewId.equals(row.id()) && !menu.previewData.isEmpty();
        boolean expanded = expandPreview.getAsBoolean();
        if (expanded) {
            lines.add(label("permission", label(page.access()), label(page.accessEnabled() ? "available" : "paused")));
            String rule = current ? menu.previewFilter : "";
            if (current) {
                lines.add(trim(label("rule", rule.isEmpty() ? label("unset") : rule)).withColor(0xc4cbd5));
            }
            if (minecraft != null && minecraft.options.advancedItemTooltips) {
                lines.add(Component.literal(row.id()).withColor(0xaaaaaa));
            }
        }
        if (mouseX - leftPos >= LIST_X + ROW_WIDTH - 19 && row.bound() && menu.advanced()) {
            lines.add(label("priority"));
        }
        if (!current) {
            lines.add(label(row.online() ? "preview_loading" : "preview_offline").withColor(0xf6c58c));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (!previewWire.equals(menu.previewData) && minecraft != null && minecraft.level != null) {
            previewWire = menu.previewData;
            preview = BulkInventoryPreview.decodeRemote(previewWire, RegistryOps.create(NbtOps.INSTANCE,
                    minecraft.level.registryAccess()));
        }
        graphics.renderTooltip(font, lines, Optional.of(new BulkInventoryTooltip(preview, expanded)), mouseX, mouseY);
    }

    private MutableComponent trim(MutableComponent line) {
        int width = Math.min(this.width - 24, 252);
        var text = line.getString();
        return font.width(text) <= width ? line
                : Component.literal(font.plainSubstrByWidth(text, width - font.width("…")) + "…");
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (listContains(x, y) || trackContains(x, y)) {
            if (vertical != 0) {
                scrollTo(offset + (vertical > 0 ? -1 : 1) * Math.max(1, (int) Math.abs(vertical)));
            }
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && trackContains(x, y)) {
            dragging = true;
            dragTo(y);
            return true;
        }
        if (button == 0 && listContains(x, y)) {
            setFocused(null);
            var row = rowAt(x, y);
            if (!pendingQuery && row != null && (row.online() || row.bound()) && menu.getCarried().isEmpty()) {
                boolean promote = menu.advanced() && row.bound() && x - leftPos >= LIST_X + ROW_WIDTH - 19;
                menu.request(promote ? "priority" : "toggle", row.id());
            }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) {
            dragTo(y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private void dragTo(double mouseY) {
        int thumb = thumbHeight();
        double fraction = (mouseY - topPos - LIST_Y - thumb / 2.0) / Math.max(1, TRACK_HEIGHT - thumb);
        scrollTo((int) Math.round(Math.clamp(fraction, 0.0, 1.0) * maxOffset()));
    }

    private void scrollTo(int wanted) {
        int next = Math.clamp(wanted, 0, maxOffset());
        if (offset != next) {
            offset = next;
            if (!pendingQuery) {
                pendingQuery = true;
                queryTicks = 2;
            }
        }
    }

    private int maxOffset() { return Math.max(0, page.total() - BulkInterfaceMenu.PAGE_SIZE); }

    private int thumbHeight() {
        return maxOffset() == 0 ? TRACK_HEIGHT
                : Math.max(12, TRACK_HEIGHT * BulkInterfaceMenu.PAGE_SIZE / page.total());
    }

    private boolean listContains(double x, double y) {
        return x >= leftPos + LIST_X && x < leftPos + LIST_X + ROW_WIDTH
                && y >= topPos + LIST_Y && y < topPos + LIST_Y + TRACK_HEIGHT;
    }

    private boolean trackContains(double x, double y) {
        return x >= leftPos + TRACK_X - 1 && x < leftPos + TRACK_X + 9
                && y >= topPos + LIST_Y && y < topPos + LIST_Y + TRACK_HEIGHT;
    }

    private BulkInterfaceMenu.Row rowAt(double x, double y) {
        if (!listContains(x, y)) {
            return null;
        }
        int row = (int) (y - topPos - LIST_Y) / ROW_HEIGHT;
        return row < page.rows().size() ? page.rows().get(row) : null;
    }

    @Override
    public void drawBG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, left, top, mouseX, mouseY, partialTicks);
        var slots = Icon.SLOT_BACKGROUND.getBlitter();
        for (var slot : menu.slots) {
            slots.dest(left + slot.x - 1, top + slot.y - 1).blit(graphics);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        super.drawFG(graphics, left, top, mouseX, mouseY);
        graphics.fill(LIST_X, LIST_Y, LIST_X + ROW_WIDTH, LIST_Y + TRACK_HEIGHT, 0xff242d37);
        graphics.fill(TRACK_X, LIST_Y, TRACK_X + 8, LIST_Y + TRACK_HEIGHT, 0xff303c47);
        int thumb = thumbHeight();
        int thumbY = LIST_Y + (maxOffset() == 0 ? 0 : page.offset() * (TRACK_HEIGHT - thumb) / maxOffset());
        graphics.fill(TRACK_X + 1, thumbY + 1, TRACK_X + 7, thumbY + thumb - 1, 0xff819da9);
        for (int i = 0; i < page.rows().size(); i++) {
            drawRow(graphics, page.rows().get(i), i, mouseX, mouseY);
        }
        if (page.rows().isEmpty()) {
            graphics.drawCenteredString(font, label(page.online() ? "empty_results" : "network_unavailable"),
                    LIST_X + ROW_WIDTH / 2, LIST_Y + TRACK_HEIGHT / 2 - 4, 0xffd0dce4);
        }
        var status = menu.message.isEmpty() ? label(page.online() ? "status" : "offline", page.bindings(),
                menu.advanced() ? 16 : 1) : label(menu.message);
        graphics.drawString(font, status, 8, STATUS_Y, 0xff343e48, false);
        var range = page.total() == 0 ? "0" : (page.offset() + 1) + "–" + (page.offset() + page.rows().size())
                + " / " + page.total();
        graphics.drawString(font, range, 271 - font.width(range), STATUS_Y, 0xff343e48, false);
    }

    private void drawRow(GuiGraphics graphics, BulkInterfaceMenu.Row row, int index, int screenMouseX, int screenMouseY) {
        int y = LIST_Y + index * ROW_HEIGHT;
        boolean hovered = row.equals(rowAt(screenMouseX, screenMouseY));
        int edge = row.bound() ? 0xff48c5bc : 0xff435560;
        graphics.fill(LIST_X, y, LIST_X + ROW_WIDTH, y + ROW_HEIGHT, edge);
        graphics.fill(LIST_X + 2, y + 1, LIST_X + ROW_WIDTH - 1, y + ROW_HEIGHT - 1,
                hovered ? 0xff3d5365 : row.bound() ? 0xff263e46 : 0xff303e4b);
        int left = LIST_X + 4;
        if (!row.tier().isEmpty()) {
            graphics.renderItem(icons.get(CellTier.valueOf(row.tier())), left, y + 2);
        }
        var state = label(row.online() ? "cell_online" : "cell_offline").getString();
        String types = row.used() < 0 ? "—" : row.used() + " / " + row.capacity();
        int right = LIST_X + ROW_WIDTH - 23;
        int textX = left + 20;
        int textWidth = right - Math.max(font.width(state), font.width(types)) - textX - 6;
        graphics.drawString(font, font.plainSubstrByWidth(displayName(row), textWidth), textX, y + 1, 0xfff2f7fc, false);
        String location = row.slot() == 0 ? "" : row.position() + "  #" + row.slot();
        graphics.drawString(font, font.plainSubstrByWidth(location, textWidth), textX, y + 10, 0xffc6d2dd, false);
        graphics.drawString(font, state, right - font.width(state), y + 1,
                row.online() ? 0xff85f0c8 : 0xfff3bc8f, false);
        graphics.drawString(font, types, right - font.width(types), y + 10, 0xffc6d2dd, false);
        if (row.bound()) {
            String mark = menu.advanced() ? Integer.toString(row.priority()) : "✓";
            graphics.drawCenteredString(font, mark, LIST_X + ROW_WIDTH - 11, y + 6, 0xff85f0c8);
        }
    }

    private static Component tierName(CellTier tier) {
        return BulkStorageMod.CELLS.get(tier).get().getDescription();
    }

    private static String defaultName(BulkInterfaceMenu.Row row) {
        return (row.tier().isEmpty() ? label("missing") : tierName(CellTier.valueOf(row.tier()))).getString();
    }

    private static String displayName(BulkInterfaceMenu.Row row) {
        return row.name().isEmpty() ? defaultName(row) : row.name();
    }

    private static MutableComponent label(String id, Object... args) {
        return Component.translatable("gui.advanced_bulk_storage.interface." + id, args);
    }
}
