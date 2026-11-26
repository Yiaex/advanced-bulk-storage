---
navigation:
  title: "ME Bulk Storage Migrator"
  parent: storage_migrators.md
  position: 65
item_ids:
  - bulk_storage_migrator
---

# ME Bulk Storage Migrator

Base rate 65536 items/t, with 4 AE2 Acceleration Card slots. Each card doubles the rate up to 1048576 items/t. The machine uses one channel and draws 32 AE/t. A tick examines at most 32 item keys.

Connect the powered device with an ME cable. Put your source and target cells in slots A and B, select direction and redstone controls, then start. An optional transfer whitelist narrows the source. The target still enforces its own filter and capacity.

Fixed mode uses idle power. Batch and AE2 compatibility modes also consume transfer energy. All background jobs share a global budget and yield under server load. The screen shows the configured maximum, actual recent throughput, total moved and queued key count.

Use the scope button to choose local slots, slot to ME, ME to slot, or a selected ME pair. Automatic routing visits physical cells in powered drives of the same ME network and save chunk. It supports finite AE2 item cells, MEGA bulk item cells, and ABS cells. Individual mounted choices use ABS UUIDs. Slots A and B still support local migration. Devices in other chunks can be moved using the local slots after safely removing their cells.

Changing the source, target, direction or scope pauses the machine. Check the new route and press Start.

<RecipeFor id="advanced_bulk_storage:bulk_storage_migrator" />

