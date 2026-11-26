package dev.advancedbulkstorage.menu;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.advancedbulkstorage.BulkConfig;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.filter.FilterCapabilities;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.filter.FilterEngine;
import dev.advancedbulkstorage.filter.FilterPresets;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.BulkCellItem;
import dev.advancedbulkstorage.storage.LimitProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class FilterEditorMenu extends AEBaseMenu {
    public static final int PAGE_SIZE = 6;
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_FAVORITES = 16;
    private static final int MAX_HISTORY = 10;
    private static final String PERSONAL = BulkStorageMod.ID + "_personal_rules";
    private final BulkFilterHost host;
    private final UUID id;
    private final ItemStack openedStack;
    private FilterConfig initial;
    private int nextQuery;
    private int nextSave;
    private String pendingDiscardProfile = "";
    private int discardConfirmationExpires;

    @GuiSync(1) public String status = "";
    @GuiSync(2) public int position;
    @GuiSync(3) public int saveVersion;
    @GuiSync(4) public String resultsJson = "";
    @GuiSync(5) public int expressionLimit;
    @GuiSync(6) public int tagLimit;
    @GuiSync(7) public boolean allowsTags;
    @GuiSync(8) public boolean allowsNamespaces;
    @GuiSync(9) public boolean allowsItems;
    @GuiSync(10) public int namespaceLimit;
    @GuiSync(11) public String storedRuleError = "";
    @GuiSync(12) public int storedTypes;
    @GuiSync(13) public int maximumTypes;
    @GuiSync(14) public boolean hasLimitCard;
    @GuiSync(15) public String limitText = "";
    @GuiSync(16) public boolean autoSortAvailable;
    @GuiSync(17) public boolean autoSortConfigured;
    @GuiSync(18) public boolean stockAvailable;
    @GuiSync(19) public boolean discardConfigured;
    private FilterConfig lastPreview;
    private List<Item> preview = new ArrayList<>();
    private Iterator<Item> previewWork;
    private FilterEngine.Compiled previewFilter;
    private Query pendingPreview;
    private int previewGeneration = -1;
    private ItemStack viewedStack = ItemStack.EMPTY;
    private BulkCellInventory viewedInventory;
    private List<StockEntry> stockView = List.of();
    private List<StockEntry> matchingStock = List.of();
    private String stockSearch;
    private int stockSearchGeneration = -1;
    private long stockRevision = Long.MIN_VALUE;
    private Query activeStock;

    public record CellProfile(int version, FilterConfig filter, String limits, boolean autoSort) {}

    public static MenuType<FilterEditorMenu> createType() {
        return MenuTypeBuilder.create(FilterEditorMenu::new, BulkFilterHost.class)
                .withMenuTitle(BulkFilterHost::filterTitle)
                .withInitialData((host, buffer) -> FilterConfig.STREAM_CODEC.encode(buffer, host.filterConfig()),
                        (host, menu, buffer) -> menu.initial = FilterConfig.STREAM_CODEC.decode(buffer))
                .buildUnregistered(BulkStorageMod.id("filter_editor"));
    }

    private FilterEditorMenu(MenuType<FilterEditorMenu> type, int window, Inventory inventory, BulkFilterHost host) {
        super(type, window, inventory, host);
        this.host = host;
        this.openedStack = host.filterStack();
        this.id = openedStack.get(BulkStorageMod.CELL_ID);
        this.initial = host.filterConfig();
        createPlayerInventorySlots(inventory);
        registerClientAction("save", FilterConfig.class, this::save);
        registerClientAction("limits", String.class, this::saveLimits);
        registerClientAction("autosort", Boolean.class, this::setAutoSort);
        registerClientAction("profile", CellProfile.class, this::saveProfile);
        registerClientAction("query", Query.class, this::query);
        registerClientAction("favorite", Favorite.class, this::saveFavorite);
        if (isServerSide()) {
            refreshCapabilities();
            publish(new Query(allowsTags ? Mode.PRESETS : Mode.MODS, 0, "", "", null, 0));
        }
    }

    public FilterConfig initial() { return initial; }

    @Override
    public boolean stillValid(Player player) {
        var stack = host.filterStack();
        return super.stillValid(player) && host.filterAccessible(player) && (isClientSide()
                || openedStack.isEmpty() && stack.isEmpty()
                || stack == openedStack && id != null && id.equals(stack.get(BulkStorageMod.CELL_ID)));
    }

    private boolean canEdit() {
        return isServerSide() && getPlayer().containerMenu == this && stillValid(getPlayer());
    }

    private FilterCapabilities capabilities() {
        return host.filterCapabilities();
    }

    private void refreshCapabilities() {
        var capabilities = capabilities();
        expressionLimit = capabilities.length();
        tagLimit = capabilities.tagLimit();
        allowsTags = capabilities.tags();
        allowsNamespaces = capabilities.namespaces();
        allowsItems = capabilities.items();
        namespaceLimit = capabilities.namespaceLimit();
        var stack = host.filterStack();
        stockAvailable = stack.getItem() instanceof BulkCellItem;
        if (stack.getItem() instanceof BulkCellItem cell) {
            maximumTypes = BulkConfig.cell(cell.tier()).types().get();
            var upgrades = cell.getUpgrades(stack);
            hasLimitCard = upgrades.isInstalled(BulkStorageMod.STORAGE_LIMIT_CARD.get());
            autoSortAvailable = BulkStorageMod.SORTING_CARDS.values().stream()
                    .filter(card -> upgrades.isInstalled(card.get())).count() == 1;
            autoSortConfigured = stack.getOrDefault(BulkStorageMod.AUTO_SORT_ENABLED, false);
            limitText = stack.getOrDefault(BulkStorageMod.LIMITS, "");
            discardConfigured = hasLimitCard && LimitProfile.parse(limitText).hasOverflow();
        }
        var stored = host.filterConfig();
        var error = FilterEngine.compile(stored, capabilities).error();
        storedRuleError = error == null || !stockAvailable && stored.expression().isBlank() ? "" : error;
    }

    public void submit(FilterConfig config) {
        sendClientAction("save", config);
    }

    public void submitLimits(String profile) {
        sendClientAction("limits", profile);
    }

    public void requestAutoSort(boolean enabled) {
        sendClientAction("autosort", enabled);
    }

    public void submitProfile(CellProfile profile) {
        sendClientAction("profile", profile);
    }

    private void setAutoSort(Boolean enabled) {
        if (!canEdit() || !stockAvailable || enabled == null || getPlayer().tickCount < nextSave) {
            return;
        }
        refreshCapabilities();
        if (enabled && (!autoSortAvailable || !storedRuleError.isEmpty())) {
            return;
        }
        nextSave = getPlayer().tickCount + 5;
        host.filterStack().set(BulkStorageMod.AUTO_SORT_ENABLED, enabled);
        host.filterChanged(getPlayer());
        refreshCapabilities();
    }

    private void saveLimits(String profile) {
        if (!canEdit() || profile == null || profile.length() > LimitProfile.MAX_LENGTH
                || getPlayer().tickCount < nextSave) {
            return;
        }
        refreshCapabilities();
        if (!hasLimitCard) {
            return;
        }
        nextSave = getPlayer().tickCount + 5;
        String normalized = profile.strip();
        var limits = LimitProfile.parse(normalized);
        boolean valid = limits.valid();
        boolean needsConfirmation = valid && limits.hasOverflow()
                && !normalized.equals(host.filterStack().getOrDefault(BulkStorageMod.LIMITS, ""));
        if (needsConfirmation && !confirmDiscard("limits:" + normalized, "limits_confirm_discard")) return;
        pendingDiscardProfile = "";
        status = valid ? "limits_saved" : "limits_invalid";
        saveVersion++;
        if (valid) {
            if (profile.isBlank()) {
                host.filterStack().remove(BulkStorageMod.LIMITS);
            } else {
                host.filterStack().set(BulkStorageMod.LIMITS, normalized);
            }
            host.filterChanged(getPlayer());
            refreshCapabilities();
        }
    }

    private boolean confirmDiscard(String key, String confirmation) {
        if (key.equals(pendingDiscardProfile) && getPlayer().tickCount <= discardConfirmationExpires) return true;
        pendingDiscardProfile = key;
        discardConfirmationExpires = getPlayer().tickCount + 200;
        status = confirmation;
        saveVersion++;
        return false;
    }

    private void saveProfile(CellProfile profile) {
        if (!canEdit() || !stockAvailable || profile == null || profile.version() != 1
                || !bounded(profile.filter()) || profile.limits() == null || profile.limits().length() > LimitProfile.MAX_LENGTH
                || getPlayer().tickCount < nextSave) return;
        nextSave = getPlayer().tickCount + 5;
        refreshCapabilities();
        var filter = profile.filter();
        var validation = FilterEngine.compile(filter, capabilities());
        String limits = profile.limits().strip();
        var limit = LimitProfile.parse(limits);
        boolean blank = filter.expression().isBlank() && !filter.blacklist() && !profile.autoSort();
        if (!(blank || validation.error() == null) || !limit.valid()
                || !limits.isEmpty() && !hasLimitCard || profile.autoSort() && !autoSortAvailable) {
            status = "profile_invalid";
            saveVersion++;
            return;
        }
        if (limit.hasOverflow() && (!limits.equals(limitText) || !filter.equals(host.filterConfig()))
                && !confirmDiscard("profile:" + JSON.toJson(profile), "profile_confirm_discard")) return;
        pendingDiscardProfile = "";
        var stack = host.filterStack();
        stack.set(BulkStorageMod.FILTER, filter);
        if (limits.isEmpty()) stack.remove(BulkStorageMod.LIMITS);
        else stack.set(BulkStorageMod.LIMITS, limits);
        stack.set(BulkStorageMod.AUTO_SORT_ENABLED, profile.autoSort());
        host.filterChanged(getPlayer());
        refreshCapabilities();
        status = "profile_imported";
        saveVersion++;
    }

    public void request(Query query) {
        sendClientAction("query", query);
    }

    public void favorite(String name, FilterConfig config) {
        sendClientAction("favorite", new Favorite(name, config));
    }

    public static void copyPersonalRules(Player original, Player replacement) {
        var originalData = original.getPersistentData();
        if (originalData.contains(PERSONAL, Tag.TAG_COMPOUND)) {
            replacement.getPersistentData().put(PERSONAL, originalData.getCompound(PERSONAL).copy());
        }
    }

    private void save(FilterConfig config) {
        if (!canEdit() || getPlayer().tickCount < nextSave || !bounded(config)) {
            return;
        }
        nextSave = getPlayer().tickCount + 5;
        var compiled = FilterEngine.compile(config, capabilities());
        boolean emptyDeviceRule = !stockAvailable && config.expression().isBlank() && !config.blacklist();
        if (stockAvailable && compiled.error() == null && !config.equals(host.filterConfig())
                && LimitProfile.parse(host.filterStack().getOrDefault(BulkStorageMod.LIMITS, "")).hasOverflow()
                && !confirmDiscard("filter:" + JSON.toJson(config), "filter_confirm_discard")) return;
        pendingDiscardProfile = "";
        status = compiled.error() == null || emptyDeviceRule ? "saved" : compiled.error();
        position = compiled.position();
        saveVersion++;
        if (compiled.error() == null || emptyDeviceRule) {
            host.applyFilter(config, getPlayer());
            store("history", "", config, MAX_HISTORY);
            refreshCapabilities();
        }
    }

    private void saveFavorite(Favorite request) {
        if (!canEdit() || getPlayer().tickCount < nextSave || request == null || request.name() == null
                || request.name().length() > 32 || !bounded(request.config()) || request.config().expression().isBlank()) {
            return;
        }
        nextSave = getPlayer().tickCount + 5;
        if (FilterEngine.compile(request.config(), capabilities()).error() == null) {
            store("favorites", request.name(), request.config(), MAX_FAVORITES);
            status = "favorited";
            saveVersion++;
        }
    }

    private static boolean bounded(FilterConfig config) {
        return config != null && config.expression() != null && config.expression().length() <= FilterConfig.MAX_INPUT_LENGTH;
    }

    private void store(String listName, String name, FilterConfig config, int limit) {
        var data = getPlayer().getPersistentData();
        var personal = data.getCompound(PERSONAL);
        var previous = personal.getList(listName, Tag.TAG_COMPOUND);
        var list = new ListTag();
        var row = new CompoundTag();
        row.putString("name", name);
        row.putString("expression", config.expression());
        row.putBoolean("blacklist", config.blacklist());
        list.add(row);
        for (int i = 0; i < previous.size() && list.size() < limit; i++) {
            var saved = previous.getCompound(i);
            if (!saved.equals(row)) {
                list.add(saved.copy());
            }
        }
        personal.put(listName, list);
        data.put(PERSONAL, personal);
    }

    private void query(Query query) {
        if (!canEdit() || getPlayer().tickCount < nextQuery || query == null || query.mode() == null
                || query.search() == null || query.search().length() > 256
                || query.item() == null || query.item().length() > 256
                || query.expression() != null && !bounded(query.expression())) {
            return;
        }
        nextQuery = getPlayer().tickCount + (query.mode() == Mode.PREVIEW ? 20 : 3);
        refreshCapabilities();
        publish(query);
    }

    private void publish(Query query) {
        var caps = capabilities();
        activeStock = query.mode() == Mode.STOCK ? query : null;
        if (query.mode() == Mode.PREVIEW) {
            schedulePreview(query, caps);
            return;
        }
        stopPreview();
        if (query.mode() == Mode.STOCK) {
            publishStock(query, caps);
            return;
        }
        String search = query.search().strip().toLowerCase(Locale.ROOT);
        List<Result> results = switch (query.mode()) {
            case PRESETS -> FilterPresets.INSTANCE.getPresets().stream()
                    .filter(preset -> preset.id().toString().contains(search)
                            || preset.definition().name().toLowerCase(Locale.ROOT).contains(search)
                            || preset.definition().category().toLowerCase(Locale.ROOT).contains(search)
                            || preset.definition().expression().contains(search))
                    .map(preset -> presetResult(preset, caps)).toList();
            case SEARCH -> caps.tags() ? FilterEngine.suggest(search.startsWith("#") ? search : "#" + search)
                    .stream().map(token -> token(token, caps)).toList() : List.of();
            case MODS -> caps.namespaces() ? FilterEngine.suggest(search.startsWith("@") ? search : "@" + search)
                    .stream().map(token -> namespace(token, caps)).toList() : List.of();
            case ITEMS -> caps.items() ? FilterEngine.suggest(search.isBlank() ? "minecraft:" : search).stream()
                    .filter(token -> !token.startsWith("#") && !token.startsWith("@"))
                    .map(token -> token(token, caps)).toList() : List.of();
            case TAGS -> itemTags(query.item(), caps);
            case HISTORY -> personal("history", caps);
            case FAVORITES -> personal("favorites", caps);
            case STOCK, PREVIEW -> throw new IllegalStateException("Unexpected inventory query mode");
        };
        int page = page(query, results.size());
        int start = page * PAGE_SIZE;
        publishResponse(new Response(page, results.size(), query.requestId(), "",
                results.subList(start, Math.min(start + PAGE_SIZE, results.size()))));
    }

    private void publishResponse(Response response) {
        String encoded = JSON.toJson(response);
        resultsJson = encoded.length() <= 30000 ? encoded
                : JSON.toJson(new Response(response.page(), response.total(), response.requestId(), "results_too_large", List.of()));
    }

    private static int page(Query query, int results) {
        return Math.clamp(query.page(), 0, Math.max(0, (results - 1) / PAGE_SIZE));
    }

    private static Result presetResult(FilterPresets.Preset preset, FilterCapabilities caps) {
        var definition = preset.definition();
        return new Result(definition.name().isEmpty() ? preset.id().toString() : definition.name(), true,
                definition.description(), definition.icon().toString(), new FilterConfig(definition.expression(), false),
                definition.unavailable(caps).orElse(""));
    }

    private static Result token(String token, FilterCapabilities caps) {
        var config = new FilterConfig(token, false);
        var error = FilterEngine.compile(config, caps).error();
        return new Result(token, false, "", "", config, error == null ? "" : error);
    }

    private static Result namespace(String token, FilterCapabilities caps) {
        var result = token(token, caps);
        String modId = token.substring(1);
        String name = ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName()).orElse(modId);
        return new Result(name + " (" + modId + ")", false, "", "", result.config(), result.error(), "mod_group", 0);
    }

    private static List<Result> itemTags(String requested, FilterCapabilities caps) {
        var id = ResourceLocation.tryParse(requested);
        if (id == null) {
            return List.of();
        }
        return BuiltInRegistries.ITEM.getHolder(id).map(holder -> {
            var rows = new ArrayList<Result>();
            if (caps.tags()) {
                var memberTags = holder.tags().map(net.minecraft.tags.TagKey::location).sorted().toList();
                var primary = FilterPresets.INSTANCE.getPresets().stream()
                        .filter(preset -> preset.definition().category().equals("basic"))
                        .map(preset -> preset.definition().expression())
                        .filter(expression -> expression.startsWith("#"))
                        .map(expression -> ResourceLocation.tryParse(expression.substring(1)))
                        .filter(memberTags::contains).distinct().toList();
                for (var tag : primary) {
                    rows.add(tagResult(tag, caps, "primary_tags"));
                }
                for (var tag : memberTags) {
                    if (!primary.contains(tag)) {
                        String group = primary.stream().anyMatch(parent -> FilterEngine.isNarrowerTag(tag, parent))
                                ? "material_tags" : "other_tags";
                        rows.add(tagResult(tag, caps, group));
                    }
                }
                rows.sort(Comparator.comparingInt(row -> switch (row.section()) {
                    case "primary_tags" -> 0;
                    case "material_tags" -> 1;
                    default -> 2;
                }));
            }
            if (caps.namespaces()) {
                rows.addFirst(namespace("@" + id.getNamespace(), caps));
            }
            if (caps.items()) {
                var result = token(id.toString(), caps);
                rows.addFirst(new Result(result.name(), false, "", id.toString(), result.config(), result.error(), "exact_item", 0));
            }
            return rows.stream().limit(128).toList();
        }).orElseGet(List::of);
    }

    private static Result tagResult(ResourceLocation id, FilterCapabilities caps, String section) {
        var result = token("#" + id, caps);
        return new Result(result.name(), false, "", "", result.config(), result.error(), section, 0);
    }

    private void publishStock(Query query, FilterCapabilities capabilities) {
        var stack = host.filterStack();
        if (stack != viewedStack) {
            viewedStack = stack;
            viewedInventory = (BulkCellInventory) StorageCells.getCellInventory(stack, null);
            stockRevision = Long.MIN_VALUE;
        }
        if (viewedInventory == null) {
            publishResponse(new Response(0, 0, query.requestId(), "", List.of()));
            return;
        }
        if (stockRevision != viewedInventory.revision()) {
            stockRevision = viewedInventory.revision();
            var snapshot = new ArrayList<StockEntry>();
            for (var entry : viewedInventory.getAvailableStacks()) {
                var key = (AEItemKey) entry.getKey();
                snapshot.add(new StockEntry(key, entry.getLongValue(), key.getDisplayName().getString()));
            }
            stockView = List.copyOf(snapshot);
            stockSearch = null;
            storedTypes = stockView.size();
        }
        String search = query.search().toLowerCase(Locale.ROOT);
        int generation = FilterEngine.generation();
        if (!search.equals(stockSearch) || stockSearchGeneration != generation) {
            stockSearch = search;
            stockSearchGeneration = generation;
            matchingStock = matchStock(search);
        }
        int page = page(query, matchingStock.size());
        int start = page * PAGE_SIZE;
        var compiled = FilterEngine.compile(stack.getOrDefault(BulkStorageMod.FILTER, FilterConfig.EMPTY), capabilities);
        var rows = matchingStock.subList(start, Math.min(start + PAGE_SIZE, matchingStock.size())).stream()
                .map(entry -> new Result(abbreviate(entry.name()), false, "", entry.key().getId().toString(),
                        FilterConfig.EMPTY, "", compiled.matches(entry.key()) ? "" : "stock_filtered", entry.amount()))
                .toList();
        publishResponse(new Response(page, matchingStock.size(), query.requestId(), "", rows));
    }

    private List<StockEntry> matchStock(String search) {
        boolean quantityOrder = search.startsWith("~ ");
        String term = (quantityOrder ? search.substring(2) : search).strip();
        var tagId = term.startsWith("#") ? ResourceLocation.tryParse(term.substring(1)) : null;
        var tag = tagId == null ? null : net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagId);
        var order = Comparator.comparing(StockEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.key().getId().toString());
        return stockView.stream()
                .filter(entry -> term.startsWith("@") ? entry.key().getId().getNamespace().contains(term.substring(1))
                        : term.startsWith("#") ? tag != null && entry.key().isTagged(tag)
                        : entry.name().toLowerCase(Locale.ROOT).contains(term)
                                || entry.key().getId().toString().contains(term))
                .sorted(quantityOrder ? Comparator.comparingLong(StockEntry::amount).reversed().thenComparing(order) : order)
                .toList();
    }

    private static String abbreviate(String name) {
        return name.substring(0, Math.min(name.length(), 128));
    }

    private List<Result> personal(String listName, FilterCapabilities caps) {
        var rows = getPlayer().getPersistentData().getCompound(PERSONAL).getList(listName, Tag.TAG_COMPOUND);
        return java.util.stream.IntStream.range(0, Math.min(MAX_FAVORITES, rows.size())).mapToObj(rows::getCompound)
                .map(row -> {
                    var config = new FilterConfig(row.getString("expression"), row.getBoolean("blacklist"));
                    var error = FilterEngine.compile(config, caps).error();
                    var name = row.getString("name");
                    return new Result(abbreviate(name.isBlank() ? config.expression() : name), false, "", "", config,
                            error == null ? "" : error);
                }).toList();
    }

    private void schedulePreview(Query query, FilterCapabilities caps) {
        var config = query.expression();
        if (config == null) {
            stopPreview();
            resultsJson = JSON.toJson(new Response(0, 0, query.requestId(), "", List.of()));
            return;
        }
        int generation = FilterEngine.generation();
        if (!config.equals(lastPreview) || generation != previewGeneration) {
            lastPreview = config;
            previewGeneration = generation;
            previewFilter = FilterEngine.compile(config, caps);
            preview = new ArrayList<>();
            previewWork = previewFilter.error() == null ? BuiltInRegistries.ITEM.iterator() : null;
        }
        if (previewWork == null) {
            publishPreview(query);
        } else {
            pendingPreview = query;
            resultsJson = JSON.toJson(new Response(0, 0, query.requestId(), "scanning", List.of()));
        }
    }

    @Override
    public void broadcastChanges() {
        if (activeStock != null && canEdit() && viewedInventory != null
                && (viewedStack != host.filterStack() || stockRevision != viewedInventory.revision()
                    || stockSearchGeneration != FilterEngine.generation())) {
            publish(activeStock);
        }
        if (previewWork != null && pendingPreview != null && canEdit()) {
            if (previewGeneration != FilterEngine.generation()) {
                schedulePreview(pendingPreview, capabilities());
            }
            if (previewWork != null) {
                long deadline = System.nanoTime() + 100_000;
                for (int examined = 0; examined < 256 && previewWork.hasNext(); examined++) {
                    var item = previewWork.next();
                    if (previewFilter.testUncached(item)) {
                        preview.add(item);
                    }
                    if ((examined & 15) == 15 && System.nanoTime() >= deadline) {
                        break;
                    }
                }
                if (!previewWork.hasNext()) {
                    previewWork = null;
                    publishPreview(pendingPreview);
                    pendingPreview = null;
                }
            }
        }
        super.broadcastChanges();
    }

    private void publishPreview(Query query) {
        int page = page(query, preview.size());
        int start = page * PAGE_SIZE;
        var rows = preview.subList(start, Math.min(start + PAGE_SIZE, preview.size())).stream().map(item -> {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            return new Result(item.getDescriptionId(), true, "", id, new FilterConfig(id, false), "");
        }).toList();
        String info = previewFilter.error() == null ? "" : "error." + previewFilter.error();
        resultsJson = JSON.toJson(new Response(page, preview.size(), query.requestId(), info, rows));
    }

    private void stopPreview() {
        if (lastPreview != null) {
            lastPreview = null;
            previewWork = null;
            pendingPreview = null;
            previewFilter = null;
            preview = new ArrayList<>();
        }
    }

    public Response results() {
        return resultsJson.isEmpty() ? new Response(0, 0, 0, "", List.of()) : JSON.fromJson(resultsJson, Response.class);
    }

    public enum Mode { PRESETS, SEARCH, MODS, ITEMS, TAGS, HISTORY, FAVORITES, PREVIEW, STOCK }
    public record Query(Mode mode, int page, String search, String item, FilterConfig expression, int requestId) {}
    public record Favorite(String name, FilterConfig config) {}
    public record Result(String name, boolean translated, String description, String icon, FilterConfig config, String error,
                         String section, long amount) {
        public Result(String name, boolean translated, String description, String icon, FilterConfig config, String error) {
            this(name, translated, description, icon, config, error, "", 0);
        }
    }
    private record StockEntry(AEItemKey key, long amount, String name) {}
    public record Response(int page, int total, int requestId, String info, List<Result> results) {}
}
