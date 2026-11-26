package dev.advancedbulkstorage.filter;

import dev.advancedbulkstorage.BulkConfig;
import dev.advancedbulkstorage.CellTier;

public record FilterCapabilities(boolean tags, boolean namespaces, boolean items,
                                 boolean and, boolean or, boolean not, boolean groups,
                                 int tagLimit, int namespaceLimit, int length, int identifierLength, int depth,
                                 int conditions, int globMatches, boolean exactEquipment) {
    public static FilterCapabilities forCell(CellTier tier, boolean extended) {
        boolean full = extended || tier == CellTier.QUANTUM;
        boolean advanced = tier == CellTier.ADVANCED;
        boolean mod = tier == CellTier.MOD;
        int baseTags = BulkConfig.cell(tier).tags().get();
        int tags = extended ? Math.max(baseTags, (tier == CellTier.QUANTUM
                ? BulkConfig.QUANTUM_EXTENDED_TAGS : BulkConfig.EXTENDED_TAGS).get()) : baseTags;
        return new FilterCapabilities(full || !mod, full || mod, full,
                full || advanced, full || advanced || mod, full || advanced, full || advanced, tags,
                BulkConfig.NAMESPACE_RULES.get(),
                BulkConfig.EXPRESSION_LENGTH.get(), BulkConfig.IDENTIFIER_LENGTH.get(),
                BulkConfig.EXPRESSION_DEPTH.get(), BulkConfig.CONDITIONS.get(),
                BulkConfig.GLOB_MATCHES.get(), BulkConfig.EXACT_EQUIPMENT.get());
    }
}
