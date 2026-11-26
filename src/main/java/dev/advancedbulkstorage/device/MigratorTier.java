package dev.advancedbulkstorage.device;

public enum MigratorTier {
    STANDARD("bulk_storage_migrator", 65_536, 4, 32, 32),
    OVERCLOCKED("overclocked_bulk_storage_migrator", 1_048_576, 4, 128, 64),
    QUANTUM("quantum_bulk_storage_migrator", 16_777_216, 7, 512, 128);

    public final String id;
    public final int speed;
    public final int upgrades;
    public final int drain;
    public final int keysPerTick;

    MigratorTier(String id, int speed, int upgrades, int drain, int keysPerTick) {
        this.id = id;
        this.speed = speed;
        this.upgrades = upgrades;
        this.drain = drain;
        this.keysPerTick = keysPerTick;
    }

    public long rate(int cards) {
        return Math.min((long) Integer.MAX_VALUE, (long) speed << Math.clamp(cards, 0, upgrades));
    }
}
