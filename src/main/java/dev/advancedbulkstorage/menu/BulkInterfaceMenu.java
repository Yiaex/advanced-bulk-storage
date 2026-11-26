package dev.advancedbulkstorage.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocators;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.device.BulkInterfaceEntity;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.migration.BulkScheduler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public final class BulkInterfaceMenu extends AEBaseMenu {
    public static final int PAGE_SIZE = 5;
    public static final int SEARCH_LENGTH = 80;
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Query INITIAL_QUERY = new Query("", "all", "priority", 0, 0);
    private static final Page INITIAL_PAGE = new Page(0, 0, 0, "both", "north", "ignore", false, false, List.of());
    private final BulkInterfaceEntity host;
    private final Map<UUID, Cell> knownBindings = new HashMap<>();
    private List<Cell> catalog = List.of();
    private Query query = INITIAL_QUERY;
    private Page page = INITIAL_PAGE;
    private UUID hovered;
    private BulkCellInventory previewInventory;
    private BulkCellInventory.PreviewWork previewWork;
    private int selectedSide;
    private int nextAction;
    private int nextQuery;
    private int nextHover;
    private int nextCatalogRefresh;
    private int nextPreviewRefresh;
    @GuiSync(1) public String responseJson = "";
    @GuiSync(2) public String message = "";
    @GuiSync(4) public String previewId = "";
    @GuiSync(5) public String previewData = "";
    @GuiSync(6) public String previewFilter = "";

    public record Row(String id, String tier, String name, String position, int slot, int used, int capacity,
                      boolean online, int priority) {
        public boolean bound() { return priority > 0; }
    }

    public record Page(int offset, int total, int bindings, String access, String face, String redstone,
                       boolean online, boolean accessEnabled, List<Row> rows) {}
    public record Edit(String action, String value) {}
    public record Query(String text, String scope, String order, int offset, int localNameMatches) {}

    private record Cell(UUID id, CellTier tier, String name, String defaultName, String filter,
                        String dimension, int x, int y, int z, int slot, int used, int capacity, boolean online,
                        int priority) {
        private String position() { return slot < 0 ? "" : x + ", " + y + ", " + z; }

        private Row row() {
            return new Row(id.toString(), tier == null ? "" : tier.name(), name,
                    position(), slot + 1, used, capacity, online, priority);
        }

        private Cell offline(int priority) {
            return new Cell(id, tier, name, defaultName, filter, dimension, x, y, z, slot, -1, capacity, false, priority);
        }

        private boolean matches(Query query, String search) {
            boolean scopeMatches = switch (query.scope()) {
                case "bound" -> priority > 0;
                case "unbound" -> priority == 0;
                case "online" -> online;
                case "all" -> true;
                default -> tier != null && tier.name().equals(query.scope());
            };
            return scopeMatches && (search.isEmpty() || contains(name, search) || contains(defaultName, search)
                    || contains(filter, search) || contains(position(), search)
                    || position().replace(" ", "").contains(search.replace(" ", ""))
                    || tier != null && (tier.id().contains(search) || (query.localNameMatches() & (1 << tier.ordinal())) != 0));
        }
    }

    public static MenuType<BulkInterfaceMenu> createType() {
        return MenuTypeBuilder.create(BulkInterfaceMenu::new, BulkInterfaceEntity.class)
                .withMenuTitle(BulkInterfaceEntity::getName)
                .buildUnregistered(BulkStorageMod.id("bulk_storage_interface"));
    }

    private BulkInterfaceMenu(MenuType<BulkInterfaceMenu> type, int id, Inventory inventory, BulkInterfaceEntity host) {
        super(type, id, inventory, host);
        this.host = host;
        createPlayerInventorySlots(inventory);
        registerClientAction("edit", Edit.class, this::edit);
        registerClientAction("query", Query.class, this::query);
        registerClientAction("hover", String.class, this::hover);
        if (isServerSide()) {
            refreshCatalog();
        }
    }

    public boolean advanced() { return host.advanced(); }

    public Page response() {
        return responseJson.isEmpty() ? INITIAL_PAGE : JSON.fromJson(responseJson, Page.class);
    }

    public void request(String action, String value) { sendClientAction("edit", new Edit(action, value)); }
    public void requestQuery(Query query) { sendClientAction("query", query); }
    public void requestHover(String id) { sendClientAction("hover", id); }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && !host.isRemoved()
                && player.level() == host.getLevel() && player.distanceToSqr(host.getBlockPos().getCenter()) <= 64.0;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && stillValid(getPlayer())) {
            int now = getPlayer().tickCount;
            if (now >= nextCatalogRefresh) {
                refreshCatalog();
            }
            if (hovered != null && now >= nextPreviewRefresh) {
                syncPreview();
            }
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        releasePreview();
        super.removed(player);
    }

    private void query(Query request) {
        if (request == null || request.text() == null || request.scope() == null || request.order() == null
                || request.text().length() > SEARCH_LENGTH || request.scope().length() > 12 || request.order().length() > 12
                || !stillValid(getPlayer()) || getPlayer().tickCount < nextQuery) {
            return;
        }
        nextQuery = getPlayer().tickCount + 2;
        query = request;
        publish();
        if (hovered != null && !isVisible(hovered)) syncPreview();
    }

    private void hover(String value) {
        if (value == null || !stillValid(getPlayer()) || getPlayer().tickCount < nextHover) {
            return;
        }
        nextHover = getPlayer().tickCount + 3;
        var id = parseUuid(value);
        hovered = id != null && isVisible(id) ? id : null;
        syncPreview();
    }

    private boolean isVisible(UUID id) {
        String value = id.toString();
        for (var row : page.rows()) {
            if (row.online() && row.id().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void syncPreview() {
        var index = hovered != null && isVisible(hovered) ? host.index() : null;
        var mount = index == null ? null : index.find(hovered);
        if (mount == null || !(getPlayer() instanceof ServerPlayer player)) {
            releasePreview();
            hovered = null;
            previewInventory = null;
            previewId = "";
            previewData = "";
            previewFilter = "";
            return;
        }
        var inventory = mount.inventory();
        if (inventory != previewInventory) {
            releasePreview();
            previewInventory = inventory;
            previewData = "";
        } else if (previewWork != null && !previewWork.active()) {
            releasePreview();
        }
        var cached = inventory.readyPreview();
        if (cached == null) {
            if (previewWork == null) {
                previewWork = inventory.requestPreview(BulkScheduler.get(player.server));
            }
            nextPreviewRefresh = getPlayer().tickCount + 1;
        } else {
            releasePreview();
            previewData = cached;
            nextPreviewRefresh = getPlayer().tickCount + 10;
        }
        previewId = mount.id().toString();
        previewFilter = inventory.filterExpression();
    }

    private void releasePreview() {
        if (previewWork != null) {
            previewWork.close();
            previewWork = null;
        }
    }

    private void edit(Edit edit) {
        if (edit == null || edit.action() == null || edit.value() == null || !stillValid(getPlayer())
                || edit.value().length() > FilterConfig.MAX_INPUT_LENGTH || getPlayer().tickCount < nextAction) {
            return;
        }
        nextAction = getPlayer().tickCount + 2;
        message = "";
        switch (edit.action()) {
            case "toggle" -> {
                var id = parseUuid(edit.value());
                if (id == null || !host.unbind(id) && (!isVisible(id) || !host.replaceBinding(id))) {
                    message = "binding_unavailable";
                }
            }
            case "priority" -> {
                var id = parseUuid(edit.value());
                if (id != null) {
                    host.prioritize(id);
                }
            }
            case "side" -> selectedSide = (selectedSide + 1) % Direction.values().length;
            case "access" -> host.cycleMode(selectedFace());
            case "redstone" -> host.cycleRedstone();
            case "editor" -> {
                if (host.filterAccessible(getPlayer())) {
                    MenuOpener.open(BulkStorageMod.FILTER_EDITOR.get(), getPlayer(), MenuLocators.forBlockEntity(host));
                    return;
                }
            }
            default -> { return; }
        }
        refreshCatalog();
    }

    private Direction selectedFace() {
        return advanced() ? Direction.values()[selectedSide] : null;
    }

    private static UUID parseUuid(String id) {
        if (id.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private void refreshCatalog() {
        nextCatalogRefresh = getPlayer().tickCount + 20;
        var index = host.catalogIndex();
        var bindings = host.bindings();
        knownBindings.keySet().retainAll(bindings);
        var rows = new ArrayList<Cell>();
        var mounted = new java.util.HashSet<UUID>();
        if (index != null) {
            boolean networkOnline = host.index() != null;
            for (var mount : index.mountedCells()) {
                var inventory = mount.inventory();
                var drive = mount.drive();
                var pos = drive.getBlockPos();
                var tier = inventory.tier();
                int priority = bindings.indexOf(mount.id()) + 1;
                var defaultName = BulkStorageMod.CELLS.get(tier).get().getDescription().getString();
                boolean online = networkOnline && drive.getMainNode().isOnline();
                var cell = new Cell(mount.id(), tier, limit(inventory.customName()), defaultName, inventory.filterExpression(),
                        drive.getLevel().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(),
                        mount.slot(), online ? inventory.types() : -1, inventory.maximumTypes(), online, priority);
                rows.add(cell);
                mounted.add(mount.id());
                if (priority > 0) {
                    knownBindings.put(mount.id(), cell);
                }
            }
        }
        for (int i = 0; i < bindings.size(); ++i) {
            var id = bindings.get(i);
            if (!mounted.contains(id)) {
                var known = knownBindings.get(id);
                rows.add(known == null ? new Cell(id, null, "", "", "", "", 0, 0, 0, -1, -1, 0, false, i + 1)
                        : known.offline(i + 1));
            }
        }
        catalog = rows;
        publish();
        if (hovered != null && !isVisible(hovered)) {
            syncPreview();
        }
    }

    private void publish() {
        var placement = Comparator.comparing(Cell::dimension).thenComparingInt(Cell::x)
                .thenComparingInt(Cell::y).thenComparingInt(Cell::z).thenComparingInt(Cell::slot);
        Comparator<Cell> sort = switch (query.order()) {
            case "name" -> Comparator.comparing(cell -> cell.name().isEmpty() ? cell.defaultName() : cell.name(),
                    String.CASE_INSENSITIVE_ORDER);
            case "tier" -> Comparator.comparing(Cell::tier, Comparator.nullsLast(Comparator.naturalOrder()));
            case "position" -> placement;
            case "usage" -> Comparator.comparingDouble((Cell cell) -> cell.used() < 0 ? -1
                    : (double) cell.used() / Math.max(1, cell.capacity())).reversed();
            default -> Comparator.comparingInt(cell -> cell.priority() == 0 ? Integer.MAX_VALUE : cell.priority());
        };
        String search = query.text().strip().toLowerCase(Locale.ROOT);
        var filtered = catalog.stream().filter(cell -> cell.matches(query, search))
                .sorted(sort.thenComparing(placement).thenComparing(Cell::id)).toList();
        int offset = Math.clamp(query.offset(), 0, Math.max(filtered.size() - PAGE_SIZE, 0));
        var rows = filtered.subList(offset, Math.min(filtered.size(), offset + PAGE_SIZE)).stream().map(Cell::row).toList();
        var face = selectedFace();
        var next = new Page(offset, filtered.size(), host.bindings().size(),
                host.mode(face).name().toLowerCase(Locale.ROOT), face == null ? "" : face.getName(),
                host.redstone().name().toLowerCase(Locale.ROOT), host.index() != null, host.allowsSide(face), rows);
        if (!next.equals(page) || responseJson.isEmpty()) {
            page = next;
            responseJson = JSON.toJson(page);
        }
    }

    private static boolean contains(String value, String lower) {
        return value.toLowerCase(Locale.ROOT).contains(lower);
    }

    private static String limit(String name) {
        int limit = name.offsetByCodePoints(0, Math.min(name.codePointCount(0, name.length()), 96));
        return name.substring(0, limit);
    }
}
