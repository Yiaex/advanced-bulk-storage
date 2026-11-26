package dev.advancedbulkstorage.storage;

public enum AutoSortTier {
    STANDARD("auto_sorting_card", 65_536),
    ADVANCED("advanced_auto_sorting_card", 1_048_576),
    OVERCLOCKED("overclocked_auto_sorting_card", 16_777_216),
    QUANTUM("quantum_auto_sorting_card", Integer.MAX_VALUE);

    public final String id;
    public final int rate;

    AutoSortTier(String id, int rate) {
        this.id = id;
        this.rate = rate;
    }
}
