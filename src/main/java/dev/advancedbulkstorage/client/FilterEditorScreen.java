package dev.advancedbulkstorage.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.menu.FilterEditorMenu;
import dev.advancedbulkstorage.menu.BulkFilterHost;
import dev.advancedbulkstorage.menu.FilterEditorMenu.Mode;
import dev.advancedbulkstorage.menu.FilterEditorMenu.Query;
import dev.advancedbulkstorage.menu.FilterEditorMenu.Response;
import dev.advancedbulkstorage.menu.FilterEditorMenu.Result;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class FilterEditorScreen extends AEBaseScreen<FilterEditorMenu> {
    private static final Gson JSON = new Gson();
    private static final int MAX_WIDTH = 344;
    private static final int MAX_HEIGHT = 268;
    private static final String[] QUANTITY_UNITS = {"", "K", "M", "G", "T", "P", "E"};
    private final ArrayDeque<FilterConfig> undo = new ArrayDeque<>();
    private final AE2Button[] rows = new AE2Button[FilterEditorMenu.PAGE_SIZE];
    private final ItemStack[] rowIcons = new ItemStack[FilterEditorMenu.PAGE_SIZE];
    private FilterConfig draft;
    private Component localMessage = Component.empty();
    private AETextField expression;
    private AETextField limits;
    private AETextField search;
    private AE2Button limitToggle;
    private AE2Button autoToggle;
    private AE2Button undoButton;
    private AE2Button modeButton;
    private AE2Button previous;
    private AE2Button next;
    private AE2Button sort;
    private AE2Button stockButton;
    private Rect2i ghostArea;
    private int rowHeight;
    private int listWidth;
    private boolean quantityOrder;
    private ItemStack ghost = ItemStack.EMPTY;
    private Response current = new Response(0, 0, 0, "", java.util.List.of());
    private String lastResults = "";
    private String queryItem = "";
    private Mode mode = Mode.PRESETS;
    private Join join = Join.REPLACE;
    private int debounce;
    private int requestId;
    private int requestWait;
    private int page;
    private int lastStatus;
    private boolean applyingUndo;
    private boolean editingLimits;
    private String lastLimits = "";

    public FilterEditorScreen(FilterEditorMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        draft = menu.initial();
        if (menu.getTarget() instanceof BulkFilterHost host
                && host.filterStack().getItem() instanceof dev.advancedbulkstorage.storage.BulkCellItem cell
                && cell.tier() == dev.advancedbulkstorage.CellTier.MOD) {
            mode = Mode.MODS;
        }
        setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Override
    protected void init() {
        imageWidth = Math.min(MAX_WIDTH, Math.max(248, width - 24));
        imageHeight = Math.min(MAX_HEIGHT, Math.max(218, height - 4));
        style.getGeneratedBackground().setWidth(imageWidth);
        style.getGeneratedBackground().setHeight(imageHeight);
        super.init();
        listWidth = imageWidth - 118;
        rowHeight = Math.clamp((imageHeight - 164) / 6, 9, 14);
        int expressionY = imageHeight - 119;
        int actions = imageHeight - 102;
        expression = field(8, expressionY, imageWidth - 16, FilterConfig.MAX_INPUT_LENGTH, draft.expression(), "expression");
        expression.setResponder(this::editExpression);
        limits = field(8, expressionY, imageWidth - 16, dev.advancedbulkstorage.storage.LimitProfile.MAX_LENGTH,
                menu.limitText, "limits_example");
        lastLimits = menu.limitText;
        button(8, actions, 38, "save", () -> {
            if (editingLimits) menu.submitLimits(limits.getValue());
            else menu.submit(draft);
        });
        undoButton = button(48, actions, 36, "undo", this::undo);
        button(86, actions, 37, "clear", () -> {
            if (editingLimits) limits.setValue("");
            else edit(FilterConfig.EMPTY);
        });
        button(125, actions, 39, "cancel", this::onClose);
        button(166, actions, 33, "copy", this::copySettings)
                .setTooltip(Tooltip.create(text("profile_copy_help")));
        button(201, actions, 39, "paste", this::pasteSettings)
                .setTooltip(Tooltip.create(text("profile_paste_help")));
        search = field(75, 18, listWidth - 66, 256, "", "search_hint");
        search.setResponder(value -> {
            if (mode == Mode.PRESETS || mode == Mode.SEARCH || mode == Mode.MODS || mode == Mode.ITEMS || mode == Mode.STOCK) {
                debounce = 3;
            }
        });
        modeButton = button(8, 18, 64, "presets", this::cycleSearchMode);
        int right = listWidth + 14;
        stockButton = button(right, 18, 50, "stock", () -> setMode(mode == Mode.STOCK ? defaultMode() : Mode.STOCK));
        button(right + 52, 18, 44, "preview", () -> setMode(Mode.PREVIEW));
        ghostArea = new Rect2i(leftPos + right, topPos + 37, 18, 18);
        var tags = addRenderableWidget(new AE2Button(ghostArea.getX(), ghostArea.getY(), 18, 18,
                Component.literal("+"), ignored -> queryCarriedItem()));
        tags.setTooltip(Tooltip.create(text("ghost_help")));
        button(right, 60, 96, "favorite", () -> {
            String name = search.getValue().strip();
            menu.favorite(name.substring(0, Math.min(32, name.length())), draft);
        });
        sort = button(right, 75, 96, "sort_name", () -> {
            quantityOrder = !quantityOrder;
            query(0);
        });
        limitToggle = button(right, 75, 46, "limits_open", () -> {
            editingLimits = !editingLimits;
            localMessage = Component.empty();
            lastStatus = menu.saveVersion;
            showLimitControls();
        });
        autoToggle = button(right + 48, 75, 48, "autosort_off", () -> menu.requestAutoSort(!menu.autoSortConfigured));
        for (int i = 0; i < rows.length; i++) {
            int index = i;
            rows[i] = addRenderableWidget(new AE2Button(leftPos + 25, topPos + 35 + i * rowHeight, listWidth - 15,
                    rowHeight - 1, Component.empty(), ignored -> select(index)));
        }
        int navigation = 36 + rows.length * rowHeight;
        previous = button(8, navigation, 18, "prev", () -> query(current.page() - 1));
        next = button(28, navigation, 18, "next", () -> query(current.page() + 1));
        var joinButton = button(48, navigation, 52, "replace", () -> {
            var values = Join.values();
            join = values[(join.ordinal() + 1) % values.length];
        });
        joinButton.setMessage(text(join.name().toLowerCase(Locale.ROOT)));
        updateResults();
        showLimitControls();
    }

    private void showLimitControls() {
        limitToggle.visible = menu.hasLimitCard && mode != Mode.STOCK;
        autoToggle.visible = (menu.autoSortAvailable || menu.autoSortConfigured) && mode != Mode.STOCK;
        autoToggle.setMessage(text(menu.autoSortConfigured ? "autosort_on" : "autosort_off"));
        autoToggle.active = menu.autoSortConfigured || menu.autoSortAvailable && menu.storedRuleError.isEmpty();
        if (!limitToggle.visible) {
            editingLimits = false;
        }
        limitToggle.setMessage(text(editingLimits ? "limits_back"
                : menu.discardConfigured ? "limits_discard" : "limits_open"));
        undoButton.active = !editingLimits;
        expression.setVisible(!editingLimits);
        limits.setVisible(editingLimits);
    }

    private void copySettings() {
        String copied = hasShiftDown() && menu.stockAvailable
                ? JSON.toJson(new FilterEditorMenu.CellProfile(1, draft, limits.getValue(), menu.autoSortConfigured))
                : editingLimits ? limits.getValue() : JSON.toJson(draft);
        minecraft.keyboardHandler.setClipboard(copied);
    }

    private void pasteSettings() {
        if (!hasShiftDown() || !menu.stockAvailable) {
            if (editingLimits) limits.setValue(minecraft.keyboardHandler.getClipboard());
            else paste();
            return;
        }
        String copied = minecraft.keyboardHandler.getClipboard();
        try {
            var profile = copied.length() > 16_000 ? null : JSON.fromJson(copied, FilterEditorMenu.CellProfile.class);
            if (profile == null || profile.version() != 1 || profile.filter() == null || profile.filter().expression() == null
                    || profile.limits() == null) {
                localMessage = text("profile_invalid");
                return;
            }
            edit(profile.filter());
            menu.submitProfile(profile);
        } catch (JsonParseException error) {
            localMessage = text("profile_invalid");
        }
    }

    private Mode defaultMode() {
        return menu.allowsNamespaces && !menu.allowsTags ? Mode.MODS : Mode.PRESETS;
    }

    private void cycleSearchMode() {
        var modes = new ArrayList<Mode>();
        if (menu.allowsTags) {
            modes.add(Mode.PRESETS);
            modes.add(Mode.SEARCH);
        }
        if (menu.allowsNamespaces) {
            modes.add(Mode.MODS);
        }
        if (menu.allowsItems) {
            modes.add(Mode.ITEMS);
        }
        modes.add(Mode.HISTORY);
        modes.add(Mode.FAVORITES);
        setMode(modes.get((modes.indexOf(mode) + 1) % modes.size()));
    }

    private AETextField field(int x, int y, int width, int limit, String value, String placeholder) {
        var field = new AETextField(style, font, leftPos + x, topPos + y, width, 14);
        field.setMaxLength(limit);
        field.setValue(value);
        field.setPlaceholder(text(placeholder));
        return addRenderableWidget(field);
    }

    private AE2Button button(int x, int y, int width, String key, Runnable action) {
        return addRenderableWidget(new AE2Button(leftPos + x, topPos + y, width, 13, text(key), ignored -> action.run()));
    }

    private void edit(FilterConfig nextDraft) {
        if (draft.equals(nextDraft)) {
            return;
        }
        if (!applyingUndo) {
            undo.addLast(draft);
            if (undo.size() > 32) {
                undo.removeFirst();
            }
        }
        draft = nextDraft;
        localMessage = Component.empty();
        lastStatus = menu.saveVersion;
        if (!expression.getValue().equals(nextDraft.expression())) {
            expression.setValue(nextDraft.expression());
        }
    }

    private void editExpression(String value) {
        boolean manualChange = !draft.expression().equals(value);
        edit(new FilterConfig(value, manualChange ? false : draft.blacklist()));
        if (manualChange && (mode == Mode.SEARCH || mode == Mode.MODS || mode == Mode.ITEMS)
                && search != null && expression.isFocused()) {
            int end = Math.min(value.length(), expression.getCursorPosition());
            int start = findTokenStart(value, end);
            var prefix = value.substring(start, end);
            if (prefix.length() <= 256) {
                search.setValue(prefix);
            }
        }
    }

    private static int findTokenStart(String value, int end) {
        int start = end;
        while (start > 0 && !Character.isWhitespace(value.charAt(start - 1))
                && "()&|!".indexOf(value.charAt(start - 1)) < 0) {
            start--;
        }
        return start;
    }

    private void undo() {
        if (!undo.isEmpty()) {
            applyingUndo = true;
            edit(undo.removeLast());
            applyingUndo = false;
        }
    }

    private void setMode(Mode value) {
        mode = value;
        current = new Response(0, 0, requestId, "", java.util.List.of());
        updateResults();
        query(0);
    }

    private void queryCarriedItem() {
        var carried = menu.getCarried();
        if (!carried.isEmpty()) {
            inspectIngredient(carried);
        } else if (!search.getValue().isBlank()) {
            queryItem = search.getValue().strip();
            setMode(Mode.TAGS);
        }
    }

    public Rect2i ghostArea() {
        return ghostArea;
    }

    public void inspectIngredient(ItemStack ingredient) {
        if (!ingredient.isEmpty()) {
            ghost = ingredient.getItem().getDefaultInstance();
            queryItem = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
            setMode(Mode.TAGS);
        }
    }

    private void query(int page) {
        this.page = Math.max(0, page);
        requestId++;
        sendQuery();
    }

    private void sendQuery() {
        String input = mode == Mode.STOCK && quantityOrder ? "~ " + search.getValue() : search.getValue();
        menu.request(new Query(mode, page, input, queryItem, mode == Mode.PREVIEW ? draft : null, requestId));
        requestWait = 24;
    }

    private void paste() {
        var clipboard = minecraft.keyboardHandler.getClipboard();
        if (clipboard.length() > FilterConfig.MAX_INPUT_LENGTH * 6 + 128) {
            localMessage = text("error.too_long");
            return;
        }
        try {
            var value = clipboard.stripLeading().startsWith("{")
                    ? JSON.fromJson(clipboard, FilterConfig.class) : new FilterConfig(clipboard.trim(), false);
            if (value != null && value.expression() != null && value.expression().length() <= FilterConfig.MAX_INPUT_LENGTH) {
                edit(value);
            } else {
                localMessage = text("error.too_long");
            }
        } catch (JsonParseException error) {
            localMessage = text("error.syntax");
        }
    }

    private void select(int index) {
        if (mode == Mode.STOCK || index >= current.results().size()) {
            return;
        }
        var row = current.results().get(index);
        if (!row.error().isEmpty()) {
            return;
        }
        String incoming = row.config().expression();
        if ((mode == Mode.SEARCH || mode == Mode.MODS || mode == Mode.ITEMS) && join == Join.REPLACE && !search.getValue().isBlank()
                && draft.expression().endsWith(search.getValue()) && incoming.startsWith(search.getValue())) {
            int start = findTokenStart(draft.expression(), draft.expression().length());
            if (draft.expression().substring(start).equals(search.getValue())) {
                edit(new FilterConfig(draft.expression().substring(0, start) + incoming, false));
                return;
            }
        }
        if (join == Join.REPLACE || draft.expression().isBlank()) {
            edit(row.config());
            return;
        }
        String operator = join == Join.OR ? " | " : " & ";
        String combined = "(" + draft.expression() + ")" + operator
                + (join == Join.EXCLUDE ? "!" : "") + "(" + incoming + ")";
        if (combined.length() <= FilterConfig.MAX_INPUT_LENGTH) {
            edit(new FilterConfig(combined, false));
        } else {
            localMessage = text("error.too_long");
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (debounce > 0 && --debounce == 0) {
            query(0);
        }
        if (!lastResults.equals(menu.resultsJson)) {
            lastResults = menu.resultsJson;
            var response = menu.results();
            if (response.requestId() == requestId) {
                current = response;
                requestWait = 0;
                updateResults();
            }
        }
        if (requestWait > 0 && --requestWait == 0) {
            sendQuery();
        }
        modeButton.setMessage(text(mode.name().toLowerCase(Locale.ROOT)));
        sort.visible = mode == Mode.STOCK;
        stockButton.visible = menu.stockAvailable;
        sort.setMessage(text(quantityOrder ? "sort_quantity" : "sort_name"));
        if (!lastLimits.equals(menu.limitText)) {
            lastLimits = menu.limitText;
            limits.setValue(lastLimits);
        }
        showLimitControls();
    }

    private void updateResults() {
        for (int i = 0; i < rows.length; i++) {
            var row = rows[i];
            row.visible = i < current.results().size();
            rowIcons[i] = ItemStack.EMPTY;
            if (row.visible) {
                var result = current.results().get(i);
                var icon = result.icon().isEmpty() ? null : ResourceLocation.tryParse(result.icon());
                if (icon != null) {
                    rowIcons[i] = BuiltInRegistries.ITEM.getOptional(icon)
                            .map(item -> item.getDefaultInstance()).orElse(ItemStack.EMPTY);
                }
                row.setMessage(label(result));
                row.active = result.error().isEmpty();
                Component tip = result.amount() > 0 ? Component.literal(result.name() + "\n" + result.amount())
                        : result.description().isEmpty() ? Component.literal(result.config().expression())
                        : Component.translatable(result.description()).append("\n").append(result.config().expression());
                if (!result.section().isEmpty()) {
                    tip = tip.copy().append("\n").append(text(result.section()));
                }
                row.setTooltip(Tooltip.create(!result.error().isEmpty() ? text("error." + result.error()) : tip));
            }
        }
        previous.active = current.page() > 0;
        next.active = (current.page() + 1) * FilterEditorMenu.PAGE_SIZE < current.total();
    }

    private static Component label(Result result) {
        if (result.name().isBlank()) {
            return text("error." + result.error());
        }
        var name = result.translated() ? Component.translatable(result.name()) : Component.literal(result.name());
        if (result.amount() > 0) {
            return name.append("  × ").append(compactQuantity(result.amount()));
        }
        return result.section().isEmpty() ? name : text(result.section()).copy().append("  ").append(name);
    }

    private static String compactQuantity(long amount) {
        long scale = 1;
        int unit = 0;
        while (unit + 1 < QUANTITY_UNITS.length && amount / scale >= 1000) {
            scale *= 1000;
            unit++;
        }
        return amount / scale + QUANTITY_UNITS[unit];
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= leftPos + 8 && x <= leftPos + listWidth + 10
                && y >= topPos + 35 && y < topPos + 35 + rowHeight * rows.length && vertical != 0) {
            if (vertical > 0 && previous.active || vertical < 0 && next.active) {
                query(current.page() + (vertical < 0 ? 1 : -1));
            }
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, left, top, mouseX, mouseY, partialTicks);
        var blitter = Icon.SLOT_BACKGROUND.getBlitter();
        for (var slot : menu.slots) {
            blitter.dest(left + slot.x - 1, top + slot.y - 1).blit(graphics);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        super.drawFG(graphics, left, top, mouseX, mouseY);
        var color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        var status = localMessage;
        if (status.getString().isEmpty() && menu.saveVersion != lastStatus && !menu.status.isEmpty()) {
            status = switch (menu.status) {
                case "saved", "favorited", "limits_saved", "limits_invalid", "limits_confirm_discard",
                        "profile_invalid", "profile_imported", "profile_confirm_discard", "filter_confirm_discard" -> text(menu.status);
                default -> text("error_at", text("error." + menu.status), menu.position + 1);
            };
        }
        if (status.getString().isEmpty() && !current.info().isEmpty()) {
            status = text(current.info());
        }
        if (status.getString().isEmpty()) {
            if (editingLimits) {
                status = text("limits_example");
            } else if (mode == Mode.STOCK) {
                status = text("stock_capacity", menu.storedTypes, menu.maximumTypes, current.total());
            } else if (!menu.storedRuleError.isEmpty()) {
                status = text("old_rule", text("error." + menu.storedRuleError));
            } else if (mode == Mode.MODS || menu.allowsNamespaces && !menu.allowsTags) {
                status = text("mod_capacity", draft.expression().length(), menu.expressionLimit, menu.namespaceLimit, current.total());
            } else {
                status = text("capacity", draft.expression().length(), menu.expressionLimit, menu.tagLimit, current.total());
            }
        }
        int statusX = 16 + font.width(Component.translatable("container.inventory"));
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), imageWidth - statusX - 8),
                statusX, imageHeight - 87, color, false);
        int ghostX = ghostArea.getX() - left;
        int ghostY = ghostArea.getY() - top;
        if (!ghost.isEmpty()) {
            graphics.renderFakeItem(ghost, ghostX + 1, ghostY + 1);
            graphics.drawString(font, font.plainSubstrByWidth(ghost.getHoverName().getString(), 72),
                    ghostX + 22, ghostY + 1, color, false);
            graphics.drawString(font, font.plainSubstrByWidth(queryItem, 72), ghostX + 22, ghostY + 10, color, false);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(8, 35, 0);
        float iconScale = Math.min(0.75F, rowHeight / 16F);
        graphics.pose().scale(iconScale, iconScale, 1);
        for (int i = 0; i < rowIcons.length; i++) {
            if (rowIcons[i] != null && !rowIcons[i].isEmpty()) {
                graphics.renderFakeItem(rowIcons[i], 0, Math.round(i * rowHeight / iconScale));
            }
        }
        graphics.pose().popPose();
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.advanced_bulk_storage." + key, arguments);
    }

    private enum Join { REPLACE, AND, OR, EXCLUDE }
}
