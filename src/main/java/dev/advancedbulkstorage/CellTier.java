package dev.advancedbulkstorage;

public enum CellTier {
    TAG("tag_bulk_storage_cell", 64, 2, 1, 8),
    ADVANCED("advanced_tag_bulk_storage_cell", 256, 4, 16, 16),
    MOD("mod_bulk_storage_cell", 1024, 4, 0, 32),
    QUANTUM("quantum_bulk_storage_cell", 4096, 6, 64, 64);

    private final String id;
    private final int types;
    private final int slots;
    private final int tags;
    private final double drain;

    CellTier(String id, int types, int slots, int tags, double drain) {
        this.id = id;
        this.types = types;
        this.slots = slots;
        this.tags = tags;
        this.drain = drain;
    }

    public String id() { return id; }
    public int defaultTypes() { return types; }
    public int defaultSlots() { return slots; }
    public int defaultTags() { return tags; }
    public double defaultDrain() { return drain; }
}
