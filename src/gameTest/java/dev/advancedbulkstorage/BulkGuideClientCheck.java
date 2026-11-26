package dev.advancedbulkstorage;

import appeng.core.AppEngClient;
import com.mojang.logging.LogUtils;
import guideme.Guide;
import guideme.color.SymbolicColor;
import guideme.document.block.LytVisitor;
import guideme.document.flow.LytFlowContent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = BulkStorageMod.ID, value = Dist.CLIENT)
public final class BulkGuideClientCheck {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<String, List<String>> CHAPTERS = Map.of(
            "storage_cells", List.of("tag_bulk_storage_cell", "advanced_tag_bulk_storage_cell",
                    "mod_bulk_storage_cell", "quantum_bulk_storage_cell", "filter_editor"),
            "storage_interfaces", List.of("bulk_storage_interface", "advanced_bulk_storage_interface"),
            "storage_migrators", List.of("bulk_storage_migrator", "overclocked_bulk_storage_migrator",
                    "quantum_bulk_storage_migrator"),
            "upgrade_cards", List.of("multi_compression_card", "storage_limit_card", "energy_saving_card",
                    "extended_tag_card", "auto_sorting_card", "advanced_auto_sorting_card",
                    "overclocked_auto_sorting_card", "quantum_auto_sorting_card"),
            "support_devices", List.of("advanced_cell_workbench", "bulk_storage_manager",
                    "bulk_storage_drive", "bulk_storage_monitor"));
    private static final int PAGE_COUNT = 1 + CHAPTERS.size() + CHAPTERS.values().stream().mapToInt(List::size).sum();
    private static int ticksInWorld;
    private static boolean started;

    private BulkGuideClientCheck() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (!Boolean.getBoolean("advanced_bulk_storage.checkGuide")
                || started || client.level == null || ++ticksInWorld < 40) {
            return;
        }
        started = true;
        var languages = client.getLanguageManager();
        String original = languages.getSelected();
        selectLanguage(client, "en_us").thenRun(() -> compile("en_us"))
                .thenCompose(unused -> selectLanguage(client, "zh_cn"))
                .thenRun(() -> compile("zh_cn"))
                .whenComplete((unused, failure) -> {
                    if (failure == null) {
                        LOG.info("ABS guide check completed for all {} localized documents in {} chapters",
                                PAGE_COUNT * 2, CHAPTERS.size());
                    } else {
                        LOG.error("ABS guide check failed", failure);
                    }
                    selectLanguage(client, original);
                });
    }

    private static CompletableFuture<Void> selectLanguage(Minecraft client, String language) {
        if (language.equals(client.getLanguageManager().getSelected())) {
            return CompletableFuture.completedFuture(null);
        }
        client.getLanguageManager().setSelected(language);
        return client.reloadResourcePacks();
    }

    private static void compile(String language) {
        var guide = AppEngClient.instance().getGuide();
        compilePage(guide, "index", language);
        verifyChildren(guide, "index", CHAPTERS.keySet());
        CHAPTERS.forEach((chapter, pages) -> {
            compilePage(guide, chapter, language);
            verifyChildren(guide, chapter, pages);
            pages.forEach(name -> compilePage(guide, name, language));
        });
    }

    private static void verifyChildren(Guide guide, String chapter, Collection<String> pages) {
        var id = BulkStorageMod.id(chapter + ".md");
        var node = guide.getNavigationTree().getNodeById(id);
        var expected = pages.stream().map(name -> BulkStorageMod.id(name + ".md")).collect(Collectors.toSet());
        if (node == null || !node.children().stream().map(child -> child.pageId())
                .collect(Collectors.toSet()).equals(expected)) {
            throw new IllegalStateException("Unexpected GuideME navigation under " + id);
        }
    }

    private static void compilePage(Guide guide, String name, String language) {
        var id = BulkStorageMod.id(name + ".md");
        var parsed = guide.getParsedPage(id);
        if (parsed == null || !language.equals(parsed.getLanguage())) {
            throw new IllegalStateException("Guide did not load " + language + " for " + id);
        }
        LOG.info("ABS compiling {} {}", language, id);
        var page = guide.getPage(id);
        if (page == null) {
            throw new IllegalStateException("Guide did not compile " + id);
        }
        page.document().visit(new LytVisitor() {
            @Override
            public Result beforeFlowContent(LytFlowContent content) {
                if (content.getStyle().color() == SymbolicColor.ERROR_TEXT) {
                    throw new IllegalStateException("Guide emitted a compiler error in " + language + " " + id);
                }
                return Result.CONTINUE;
            }
        }, true);
    }
}
