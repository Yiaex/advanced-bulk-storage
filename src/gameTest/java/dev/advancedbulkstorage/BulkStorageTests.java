package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.filter.FilterCapabilities;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.filter.FilterEngine;
import dev.advancedbulkstorage.filter.FilterPresets;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.BulkCellItem;
import dev.advancedbulkstorage.storage.BulkInventoryPreview;
import dev.advancedbulkstorage.storage.BulkInventoryTooltip;
import gripe._90.megacells.definition.MEGAItems;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkStorageTests {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final Actionable REAL = Actionable.MODULATE;
    private static final Actionable SIM = Actionable.SIMULATE;

    private BulkStorageTests() {}

    @GameTest(template = "empty")
    public static void wideAmountsAndPersistence(GameTestHelper test) {
        var stack = cell(CellTier.QUANTUM, "@minecraft");
        var changes = new AtomicInteger();
        var inventory = open(stack, changes::incrementAndGet);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        test.assertTrue(inventory.insert(iron, Long.MAX_VALUE, SIM, SOURCE) == Long.MAX_VALUE, "Simulation rejected capacity");
        test.assertTrue(changes.get() == 0 && inventory.amount(iron) == 0, "Simulation mutated inventory");
        test.assertTrue(inventory.insert(iron, Long.MAX_VALUE - 3, REAL, SOURCE) == Long.MAX_VALUE - 3, "Large insert failed");
        test.assertTrue(inventory.insert(iron, 10, REAL, SOURCE) == 10, "Insertion stopped at Long.MAX_VALUE");
        test.assertTrue(inventory.insert(gold, 1_000_000_000_000L, REAL, SOURCE) == 1_000_000_000_000L, "Independent insert failed");
        test.assertTrue(!stack.has(BulkStorageMod.CONTENTS) && changes.get() == 3, "Write-back was not deferred");
        inventory.persist();
        var saved = stack.save(test.getLevel().registryAccess());
        var restored = ItemStack.parse(test.getLevel().registryAccess(), saved).orElseThrow();
        var loaded = open(restored, null);
        var expected = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(7));
        test.assertTrue(inventory.id() != null && inventory.id().equals(loaded.id()), "UUID changed after serialization");
        test.assertTrue(loaded.amountExact(iron).equals(expected) && loaded.amount(iron) == Long.MAX_VALUE
                && loaded.amount(gold) == 1_000_000_000_000L, "Amounts changed on load");
        test.assertTrue(loaded.extract(iron, Long.MAX_VALUE, SIM, SOURCE) == Long.MAX_VALUE
                && loaded.amountExact(iron).equals(expected),
                "Extraction simulation modified inventory");
        test.assertTrue(loaded.extract(iron, Long.MAX_VALUE, REAL, SOURCE) == Long.MAX_VALUE && loaded.amount(iron) == 7,
                "Withdrawal lost the units beyond the signed long range");
        test.assertTrue(restored.get(BulkStorageMod.CONTENTS).copyTag().getInt("version") == 1,
                "Narrow ordinary stock no longer used the legacy format");
        test.assertTrue(loaded.insert(gold, -1, REAL, SOURCE) == 0 && loaded.extract(gold, -1, REAL, SOURCE) == 0,
                "Rejected quantities changed the inventory");
        var counter = new KeyCounter();
        counter.set(gold, Long.MAX_VALUE - 1);
        loaded.getAvailableStacks(counter);
        test.assertTrue(counter.get(gold) == Long.MAX_VALUE, "Aggregate count overflowed");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void fullKeyTypes(GameTestHelper test) {
        var stack = cell(CellTier.QUANTUM, "@minecraft");
        var inventory = open(stack, () -> {});
        for (int i = 0; i < 4096; i++) {
            test.assertTrue(inventory.insert(namedKey(i), i + 1, REAL, SOURCE) == i + 1, "Distinct key rejected at " + i);
        }
        test.assertTrue(inventory.insert(namedKey(4096), 1, REAL, SOURCE) == 0, "Cell accepted a 4097th type");
        test.assertTrue(inventory.insert(namedKey(4095), 1, REAL, SOURCE) == 1, "Cell rejected an existing key");
        inventory.persist();
        var saved = stack.save(test.getLevel().registryAccess());
        var loaded = open(ItemStack.parse(test.getLevel().registryAccess(), saved).orElseThrow(), null);
        test.assertTrue(loaded.types() == 4096, "Distinct components were lost after persistence");
        for (int i = 0; i < 4096; i++) {
            test.assertTrue(loaded.amount(namedKey(i)) == (i == 4095 ? 4097 : i + 1), "Component key mismatch at " + i);
        }
        var started = System.nanoTime();
        var preview = BulkInventoryPreview.read(stack.get(BulkStorageMod.CONTENTS),
                RegistryOps.create(NbtOps.INSTANCE, test.getLevel().registryAccess()));
        LogUtils.getLogger().info("ABS tooltip 4096-key cache miss: entries={}, elapsedNs={}",
                preview.entries().size(), System.nanoTime() - started);
        test.assertTrue(!preview.invalid() && preview.total() == 4096 && preview.entries().size() == BulkInventoryPreview.MAX_ENTRIES
                && preview.entries().getFirst().key().equals(namedKey(4095)) && preview.entries().getFirst().amount() == 4097,
                "Quantum tooltip selection did not preserve its first exact key or bounded count");
        var ops = RegistryOps.create(NbtOps.INSTANCE, test.getLevel().registryAccess());
        started = System.nanoTime();
        var firstWire = collectRemotePreview(loaded);
        test.assertTrue(firstWire == loaded.readyPreview(), "Repeated readers did not share the current summary");
        var remote = BulkInventoryPreview.decodeRemote(firstWire, ops);
        LogUtils.getLogger().info("ABS remote 4096-key preview: bytes={}, entries={}, elapsedNs={}",
                firstWire.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, remote.entries().size(),
                System.nanoTime() - started);
        test.assertTrue(remote.equals(preview) && firstWire.length() < 24_000, "Remote preview sent the wrong stock or exceeded its bound");
        loaded.insert(namedKey(0), Long.MAX_VALUE, REAL, SOURCE);
        test.assertTrue(loaded.readyPreview() == null, "Inventory mutation did not invalidate the preview");
        var revised = BulkInventoryPreview.decodeRemote(collectRemotePreview(loaded), ops);
        test.assertTrue(revised.total() == 4096 && revised.entries().getFirst().key().equals(namedKey(0))
                && revised.entries().getFirst().amount() == Long.MAX_VALUE
                && revised.entries().getFirst().exactAmount().equals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))
                && revised.entries().getFirst().label().equals("9.2E"), "Live changes did not refresh the shared preview");
        test.succeed();
    }

    static String collectRemotePreview(BulkCellInventory inventory) {
        var scheduler = new dev.advancedbulkstorage.migration.BulkScheduler();
        try (var first = inventory.requestPreview(scheduler)) {
            try (var second = inventory.requestPreview(scheduler)) {
                if (first != second) {
                    throw new IllegalStateException("Viewers queued duplicate work for one cell");
                }
            }
            for (int tick = 0; tick < 40 && inventory.readyPreview() == null; ++tick) {
                scheduler.advance(0, 50_000_000L, 1_024, false);
            }
        }
        if (scheduler.status().registered() != 0 || inventory.readyPreview() == null) {
            throw new IllegalStateException("Remote inventory preview did not complete and clean up");
        }
        return inventory.readyPreview();
    }

    @GameTest(template = "empty")
    public static void smallHoverPreviewSurvivesAdaptivePause(GameTestHelper test) {
        var inventory = open(cell(CellTier.QUANTUM, "@minecraft"), () -> {});
        var iron = AEItemKey.of(Items.IRON_INGOT);
        long amount = 64_000_000_000L;
        test.assertTrue(inventory.insert(iron, amount, REAL, SOURCE) == amount, "Could not seed the preview");
        var scheduler = new dev.advancedbulkstorage.migration.BulkScheduler();
        var backgroundRuns = new AtomicInteger();
        dev.advancedbulkstorage.migration.BulkScheduler.Job background = allowance -> {
            backgroundRuns.incrementAndGet();
            return false;
        };
        scheduler.wake(background, true);
        try (var work = inventory.requestPreview(scheduler)) {
            for (int tick = 0; tick < 12 && work.active(); tick++) {
                scheduler.advance(55, 2_000_000, 2048, true);
                var status = scheduler.status();
                test.assertTrue(status.bandPercent() == 0 && status.keys() <= 64
                        && status.budgetNanos() <= 250_000 && backgroundRuns.get() == 0,
                        "The hover window exceeded its pause budget or ran a background task");
            }
            var wire = inventory.readyPreview();
            test.assertTrue(!work.active() && wire != null, "The adaptive pause starved a one-key hover preview");
            var preview = BulkInventoryPreview.decodeRemote(wire, RegistryOps.create(NbtOps.INSTANCE,
                    test.getLevel().registryAccess()));
            test.assertTrue(preview.total() == 1 && preview.entries().getFirst().amount() == amount
                    && preview.entries().getFirst().label().equals("64G"), "The paused preview lost a LONG quantity");
        }
        scheduler.cancel(background);
        test.assertTrue(scheduler.status().registered() == 0 && inventory.amount(iron) == amount,
                "The hover preview leaked a ticket or changed real stock");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void tooltipPreservesAllCellTiersAndLongAmounts(GameTestHelper test) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, test.getLevel().registryAccess());
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        for (var tier : CellTier.values()) {
            var stack = cell(tier, tier == CellTier.MOD ? "@minecraft" : "#c:ingots");
            var item = stack.getItem();
            var empty = (BulkInventoryTooltip) item.getTooltipImage(stack).orElseThrow();
            test.assertTrue(BulkInventoryPreview.read(empty.contents(), ops).equals(BulkInventoryPreview.EMPTY),
                    "An empty cell exposed its whitelist as inventory");
            var inventory = open(stack, () -> {});
            inventory.insert(iron, Long.MAX_VALUE, REAL, SOURCE);
            inventory.persist();
            var single = (BulkInventoryTooltip) item.getTooltipImage(stack).orElseThrow();
            var first = BulkInventoryPreview.read(single.contents(), ops);
            test.assertTrue(!first.invalid() && first.total() == 1 && first.entries().getFirst().key().equals(iron)
                    && first.entries().getFirst().amount() == Long.MAX_VALUE
                    && first.entries().getFirst().label().equals("9.2E"), "Single LONG inventory was truncated");
            inventory.insert(gold, 1_000_000_000_000L, REAL, SOURCE);
            for (int i = 0; i < 42; i++) {
                inventory.insert(namedKey(i), 2_147_483_648L + i, REAL, SOURCE);
            }
            stack.set(BulkStorageMod.FILTER, new FilterConfig("*", false));
            inventory.persist();
            var updated = (BulkInventoryTooltip) item.getTooltipImage(stack).orElseThrow();
            var full = BulkInventoryPreview.read(updated.contents(), ops);
            test.assertTrue(updated.contents() != single.contents() && full.total() == 44
                    && full.entries().get(1).key().equals(gold) && full.entries().get(1).label().equals("1T")
                    && full.entries().getLast().amount() == 2_147_483_648L
                    && inventory.amount(iron) == Long.MAX_VALUE && inventory.insert(iron, 1, SIM, SOURCE) == 0,
                    "A refreshed tooltip did not preserve actual stock or legacy whitelist safety");
            test.assertTrue(full.visibleCount(false, 9, 36, 9, 4) == 9
                    && full.visibleCount(true, 9, 36, 9, 4) == 36
                    && full.visibleCount(true, 9, 36, 6, 2) == 12,
                    "Ordinary, Shift or constrained-width preview count was incorrect");
        }
        test.assertTrue(BulkInventoryPreview.formatAmount(999).equals("999")
                && BulkInventoryPreview.formatAmount(1000).equals("1K")
                && BulkInventoryPreview.formatAmount(Integer.MAX_VALUE).equals("2.1G"),
                "Abbreviated quantities failed at a scale boundary");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void filterPermissionsAndExtraction(GameTestHelper test) {
        var stack = cell(CellTier.TAG, "");
        var item = (dev.advancedbulkstorage.storage.BulkCellItem) stack.getItem();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var diamond = AEItemKey.of(Items.DIAMOND);
        test.assertTrue(!FilterPresets.INSTANCE.getPresets().isEmpty(), "Datapack presets not loaded");
        test.assertTrue(open(stack, null).insert(iron, 1, SIM, SOURCE) == 0, "Empty configuration accepted an item");
        stack.set(BulkStorageMod.FILTER, new FilterConfig("#c:ingots", false));
        var inventory = open(stack, null);
        test.assertTrue(inventory.insert(iron, 17, REAL, SOURCE) == 17 && inventory.insert(diamond, 1, SIM, SOURCE) == 0,
                "Stock ingot filter incorrect");
        stack.set(BulkStorageMod.FILTER, new FilterConfig("minecraft:diamond", false));
        test.assertTrue(inventory.insert(diamond, 2, SIM, SOURCE) == 0 && inventory.extract(iron, 17, SIM, SOURCE) == 17,
                "Invalid rule affected extraction or allowed insertion");
        test.assertTrue(item.getUpgrades(stack).addItems(BulkStorageMod.EXTENDED_TAG_CARD.get().getDefaultInstance()).isEmpty(),
                "Extended Tag Card was rejected");
        inventory = open(stack, null);
        test.assertTrue(inventory.insert(diamond, 2, SIM, SOURCE) == 2, "Extended Tag Card failed to unlock precise item filtering");
        item.getUpgrades(stack).setItemDirect(0, ItemStack.EMPTY);
        inventory = open(stack, null);
        test.assertTrue(inventory.insert(diamond, 2, SIM, SOURCE) == 0 && inventory.amount(iron) == 17,
                "Removing the card failed to freeze the rule or altered stock");
        var full = FilterCapabilities.forCell(CellTier.QUANTUM, false);
        var precedence = FilterEngine.compile(new FilterConfig("minecraft:diamond | #c:ingots & !#c:ingots/iron", false), full);
        test.assertTrue(precedence.matches(diamond) && !precedence.matches(iron) && precedence.matches(Items.GOLD_INGOT),
                "Compound precedence incorrect");
        var invalid = FilterEngine.compile(new FilterConfig("@minecraft & !#advanced_bulk_storage:missing", false), full);
        test.assertTrue("unknown_tag".equals(invalid.error()) && !invalid.matches(diamond), "Unknown tag matched under negation");
        test.assertTrue(FilterEngine.compile(new FilterConfig("@minecraft", false), full).matches(iron), "Namespace index missing");
        test.assertTrue(FilterEngine.compile(new FilterConfig("(".repeat(100) + "#c:ingots" + ")".repeat(100), false), full)
                .error() != null, "Excessive nesting was accepted");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void whitelistGlobsEquipmentAndLegacyInventory(GameTestHelper test) {
        var full = FilterCapabilities.forCell(CellTier.QUANTUM, false);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var sword = AEItemKey.of(Items.DIAMOND_SWORD);
        for (String invalid : java.util.List.of("*", "#*", "@*", "*:*", "!#c:ingots",
                "#c:ingots | !@minecraft", "!(#c:ingots | @minecraft)", "minecraft:*_sword", "#minecraft:swords")) {
            var rule = FilterEngine.compile(new FilterConfig(invalid, false), full);
            test.assertTrue(rule.error() != null && !rule.matches(iron) && !rule.matches(sword), "Unsafe expression accepted: " + invalid);
        }
        test.assertTrue(FilterEngine.compile(new FilterConfig("#c:ingots/*", false), full).matches(iron),
                "Registered tag glob did not accept the ingot");
        test.assertTrue(FilterEngine.compile(new FilterConfig("minecraft:*_ingot", false), full).matches(iron),
                "Item ID glob did not accept the ingot");
        var namespace = FilterEngine.compile(new FilterConfig("@mine*", false), full);
        test.assertTrue(namespace.matches(iron) && !namespace.matches(sword), "Broad namespace did not protect equipment");
        var exact = FilterEngine.compile(new FilterConfig("minecraft:diamond_sword | #c:ingots", false), full);
        test.assertTrue(exact.matches(sword) && exact.matches(iron), "A positive exact equipment ID did not grant access");
        var scoped = FilterEngine.compile(new FilterConfig("#c:ingots & !(#c:ingots/iron | @megacells)", false), full);
        test.assertTrue(!scoped.matches(iron) && scoped.matches(Items.GOLD_INGOT), "Scoped exclusion regression");
        var capacity = FilterEngine.compile(new FilterConfig("#c:ingots/*", false), FilterCapabilities.forCell(CellTier.TAG, false));
        test.assertTrue("tag_limit".equals(capacity.error()), "Tag glob bypassed the tier's expansion limit");
        var stack = cell(CellTier.QUANTUM, "#c:ingots");
        var inventory = open(stack, null);
        test.assertTrue(inventory.insert(iron, 42, REAL, SOURCE) == 42, "Could not seed the legacy test cell");
        var id = inventory.id();
        for (var legacy : java.util.List.of(new FilterConfig("*", false), new FilterConfig("#c:ingots", true))) {
            stack.set(BulkStorageMod.FILTER, legacy);
            inventory = open(stack, null);
            test.assertTrue(inventory.insert(iron, 1, SIM, SOURCE) == 0
                    && inventory.extract(iron, 42, SIM, SOURCE) == 42
                    && legacy.equals(stack.get(BulkStorageMod.FILTER)) && id.equals(inventory.id()),
                    "Legacy rule caused a data change or accepted a new item");
        }
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void damagedPayloadIsPreserved(GameTestHelper test) {
        var stack = cell(CellTier.QUANTUM, "@minecraft");
        var data = new CompoundTag();
        data.putInt("version", 999);
        data.putLong("unrecognized_payload", Long.MAX_VALUE);
        stack.set(BulkStorageMod.CONTENTS, CustomData.of(data));
        var inventory = open(stack, null);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        test.assertTrue(inventory.insert(iron, 1, REAL, SOURCE) == 0 && inventory.extract(iron, 1, REAL, SOURCE) == 0,
                "Unsupported payload was writable");
        inventory.persist();
        test.assertTrue(stack.get(BulkStorageMod.CONTENTS).copyTag().equals(data), "Unsupported payload was overridden");
        var wide = cell(CellTier.QUANTUM, "@minecraft");
        var wideInventory = open(wide, null);
        wideInventory.insert(iron, Long.MAX_VALUE, REAL, SOURCE);
        wideInventory.insert(iron, 1, REAL, SOURCE);
        var invalid = wide.get(BulkStorageMod.CONTENTS).copyTag();
        invalid.getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .putByteArray("amount", new byte[] {-1});
        wide.set(BulkStorageMod.CONTENTS, CustomData.of(invalid));
        var quarantined = open(wide, null);
        test.assertTrue(quarantined.quarantined() && quarantined.extract(iron, 1, REAL, SOURCE) == 0
                && quarantined.insert(iron, 1, REAL, SOURCE) == 0
                && wide.get(BulkStorageMod.CONTENTS).copyTag().equals(invalid),
                "An invalid wide amount was accepted or its original data was overwritten");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void driveMountPriorityAndRemoval(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        test.setBlock(pos, AEBlocks.DRIVE.block());
        test.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        DriveBlockEntity drive = test.getBlockEntity(pos);
        var stack = cell(CellTier.TAG, "#c:ingots");
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var item = (dev.advancedbulkstorage.storage.BulkCellItem) stack.getItem();
        item.getConfigInventory(stack).setStack(0, new GenericStack(iron, 0));
        drive.getInternalInventory().setItemDirect(0, stack);
        test.startSequence().thenWaitUntil(() -> test.assertTrue(drive.isPowered(), "Drive has no channel or power"))
                .thenExecute(() -> {
                    var grid = drive.getMainNode().getGrid();
                    var network = grid.getStorageService().getInventory();
                    test.assertTrue(network.insert(iron, 1_000_000_000_000L, REAL, SOURCE) == 1_000_000_000_000L,
                            "Mounted storage did not accept a trillion items");
                    test.assertTrue(network.insert(gold, 1, SIM, SOURCE) == 0, "Native AE2 partition was bypassed");
                    drive.setPriority(7);
                    test.assertTrue(network.getAvailableStacks().get(iron) == 1_000_000_000_000L,
                            "Rebuilding the drive at a new priority lost dirty inventory");
                    test.assertTrue(network.extract(iron, 3, REAL, SOURCE) == 3, "Native AE2 extraction failed");
                    var removed = drive.getInternalInventory().extractItem(0, 1, false);
                    var reopened = open(removed, null);
                    test.assertTrue(reopened.amount(iron) == 999_999_999_997L, "Extraction from the drive lost pending inventory");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void thousandMountedCells(GameTestHelper test) {
        var drives = new ArrayList<DriveBlockEntity>();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                var pos = new BlockPos(2 + 2 * x, 2, 2 + 2 * z);
                test.setBlock(pos, AEBlocks.DRIVE.block());
                test.setBlock(pos.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
                DriveBlockEntity drive = test.getBlockEntity(pos);
                for (int slot = 0; slot < drive.getCellCount(); slot++) {
                    drive.getInternalInventory().setItemDirect(slot, cell(CellTier.TAG, "#c:ingots"));
                }
                drives.add(drive);
            }
        }
        test.startSequence()
                .thenWaitUntil(() -> test.assertTrue(drives.stream().allMatch(DriveBlockEntity::isPowered),
                        "Some drives lack power or a channel"))
                .thenExecute(() -> {
                    var iron = AEItemKey.of(Items.IRON_INGOT);
                    var ids = new java.util.HashSet<java.util.UUID>();
                    for (var drive : drives) {
                        for (int slot = 0; slot < drive.getCellCount(); slot++) {
                            var inventory = (BulkCellInventory) drive.getOriginalCellInventory(slot);
                            test.assertTrue(inventory != null && inventory.id() != null && ids.add(inventory.id()),
                                    "Drive slot has no cell or reused an ID");
                            test.assertTrue(inventory.insert(iron, 1, REAL, SOURCE) == 1, "Mounted cell rejected iron");
                        }
                        var network = drive.getMainNode().getGrid().getStorageService().getInventory();
                        test.assertTrue(network.getAvailableStacks().get(iron) == 10, "Network aggregated unexpected stock");
                    }
                    test.assertTrue(ids.size() == 1000, "Expected 1000 simultaneously mounted cells");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void loweredCapacityDoesNotDeleteStock(GameTestHelper test) {
        var settings = BulkConfig.cell(CellTier.QUANTUM);
        int previousTypes = settings.types().get();
        long previousLimit = settings.itemsPerType().get();
        var stack = cell(CellTier.QUANTUM, "@minecraft");
        var inventory = open(stack, () -> {});
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var diamond = AEItemKey.of(Items.DIAMOND);
        test.assertTrue(inventory.insert(iron, 30, REAL, SOURCE) == 30
                && inventory.insert(gold, 31, REAL, SOURCE) == 31, "Test stock was not seeded");
        try {
            settings.types().set(1);
            settings.itemsPerType().set(20L);
            test.assertTrue(inventory.amount(iron) == 30 && inventory.amount(gold) == 31,
                    "Lower limits destroyed existing stock");
            test.assertTrue(inventory.insert(iron, 1, REAL, SOURCE) == 0
                    && inventory.insert(diamond, 1, REAL, SOURCE) == 0, "Lower limits did not apply");
            test.assertTrue(inventory.extract(iron, 30, REAL, SOURCE) == 30
                    && inventory.extract(gold, 31, REAL, SOURCE) == 31, "Over-limit inventory could not be withdrawn");
            test.assertTrue(inventory.insert(diamond, 25, REAL, SOURCE) == 20, "New capacity was not applied");
        } finally {
            settings.types().set(previousTypes);
            settings.itemsPerType().set(previousLimit);
        }
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void recipesRegisteredAndPreserveStock(GameTestHelper test) {
        var recipes = test.getLevel().getRecipeManager();
        for (var tier : CellTier.values()) {
            test.assertTrue(recipes.byKey(BulkStorageMod.id(tier.id())).isPresent(), "Missing recipe for " + tier);
        }
        test.assertTrue(recipes.byKey(BulkStorageMod.id("extended_tag_card")).isPresent(), "Missing Extended Tag Card recipe");
        var level = test.getLevel();
        var baseRecipe = (CraftingRecipe) recipes.byKey(BulkStorageMod.id(CellTier.TAG.id())).orElseThrow().value();
        var blankMega = MEGAItems.BULK_ITEM_CELL.stack();
        var logic = AEItems.LOGIC_PROCESSOR.stack();
        var engineering = AEItems.ENGINEERING_PROCESSOR.stack();
        var component64m = MEGAItems.CELL_COMPONENT_64M.stack();
        var materials = CraftingInput.of(2, 2, List.of(blankMega, logic, engineering, component64m));
        test.assertTrue(baseRecipe.matches(materials, level)
                && baseRecipe.assemble(materials, level.registryAccess()).is(BulkStorageMod.CELLS.get(CellTier.TAG).get()),
                "The blank MEGA cell and a 64M component did not craft the Tag cell");
        test.assertTrue(!baseRecipe.matches(CraftingInput.of(2, 2, List.of(blankMega, logic, engineering,
                MEGAItems.CELL_COMPONENT_16M.stack())), level), "The Tag cell accepted a lower-tier component");
        test.assertTrue(!baseRecipe.matches(CraftingInput.of(2, 2, List.of(blankMega, logic, engineering,
                ItemStack.EMPTY)), level), "The old Tag cell recipe still worked without a 64M component");
        blankMega.set(DataComponents.CUSTOM_NAME, Component.literal("Configured source"));
        test.assertTrue(!baseRecipe.matches(CraftingInput.of(2, 2, List.of(blankMega, logic, engineering, component64m)),
                level), "Modified MEGA cell could be consumed");
        var original = cell(CellTier.TAG, "#c:ingots");
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Warehouse cell"));
        ((BulkCellItem) original.getItem()).getUpgrades(original).setItemDirect(
                0, BulkStorageMod.ENERGY_SAVING_CARD.get().getDefaultInstance());
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var before = open(original, null);
        test.assertTrue(before.insert(iron, 1_000_000_000_000L, REAL, SOURCE) == 1_000_000_000_000L, "Cannot seed upgrade");
        var templates = List.of(engineering, component64m, MEGAItems.CELL_COMPONENT_256M.stack());
        var oldAdditions = List.of(MEGAItems.ACCUMULATION_PROCESSOR.stack(), MEGAItems.CELL_COMPONENT_1M.stack(),
                MEGAItems.CELL_COMPONENT_16M.stack());
        var source = original;
        for (int i = 0; i < templates.size(); i++) {
            var tier = CellTier.values()[i + 1];
            var recipe = (SmithingTransformRecipe) recipes.byKey(BulkStorageMod.id(tier.id())).orElseThrow().value();
            var template = templates.get(i);
            var addition = MEGAItems.CELL_COMPONENT_256M.stack();
            test.assertTrue(!recipe.matches(new SmithingRecipeInput(template, source, oldAdditions.get(i)), level),
                    "An old upgrade cost still worked for " + tier);
            test.assertTrue(!recipe.matches(new SmithingRecipeInput(ItemStack.EMPTY, source, addition), level),
                    "A missing template upgraded a cell to " + tier);
            var upgrade = new SmithingRecipeInput(template, source, addition);
            test.assertTrue(recipe.matches(upgrade, level), "Configured cell could not upgrade to " + tier);
            var result = recipe.assemble(upgrade, level.registryAccess());
            var after = open(result, null);
            test.assertTrue(result.is(BulkStorageMod.CELLS.get(tier).get())
                    && result.getHoverName().equals(original.getHoverName())
                    && result.get(BulkStorageMod.FILTER).equals(original.get(BulkStorageMod.FILTER))
                    && result.get(AEComponents.UPGRADES).equals(original.get(AEComponents.UPGRADES))
                    && after.id().equals(before.id()) && after.amount(iron) == before.amount(iron),
                    "Smithing did not preserve stock, filter, name, upgrades and UUID for " + tier);
            source = result;
        }
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void millionBulkOperations(GameTestHelper test) {
        var stack = cell(CellTier.QUANTUM, "@minecraft");
        var inventory = open(stack, () -> {});
        var iron = AEItemKey.of(Items.IRON_INGOT);
        long moved = Integer.MAX_VALUE;
        var start = System.nanoTime();
        for (int i = 0; i < 500_000; i++) {
            if (inventory.insert(iron, moved, REAL, SOURCE) != moved || inventory.extract(iron, moved, REAL, SOURCE) != moved) {
                test.fail("Bulk operation returned an incorrect quantity at " + i);
            }
        }
        long elapsed = System.nanoTime() - start;
        test.assertTrue(inventory.amount(iron) == 0 && inventory.types() == 0, "Million-operation test was not conservative");
        LogUtils.getLogger().info("ABS isolated workload: 1000000 operations, batch={}, elapsedNs={}", moved, elapsed);
        test.succeed();
    }

    private static AEItemKey namedKey(int i) {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Test iron " + i));
        return AEItemKey.of(stack);
    }

    private static ItemStack cell(CellTier tier, String expression) {
        var stack = BulkStorageMod.CELLS.get(tier).get().getDefaultInstance();
        stack.set(BulkStorageMod.FILTER, new FilterConfig(expression, false));
        return stack;
    }

    private static BulkCellInventory open(ItemStack stack, appeng.api.storage.cells.ISaveProvider host) {
        return (BulkCellInventory) StorageCells.getCellInventory(stack, host);
    }
}
