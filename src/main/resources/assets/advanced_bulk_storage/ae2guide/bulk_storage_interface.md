---
navigation:
  title: "ME Bulk Storage Interface"
  parent: storage_interfaces.md
  position: 60
item_ids:
  - bulk_storage_interface
---

# ME Bulk Storage Interface

Connect to a powered AE2 network with a free channel. This interface draws 4 AE/t, uses one channel and binds one bulk storage cell.

Open the interface and find a mounted cell on the same ME network. Search by its name, tier, drive coordinates or filter rule. Use the scope and sort buttons to narrow the list, then scroll or drag the scrollbar. The item icon identifies the cell tier. Each row shows the custom name when set, the drive slot, the number of occupied types and the connection status.

Click a row to bind it or click the checked row to unbind it. Selecting a different online cell replaces the current binding. Unmounted, offline and duplicate cells suspend access and retain their bindings.

Hover over a cell for an inventory preview. Hold **Shift** to expand it and see the cell filter and the active access mode. Quantities reflect the actual cell stock up to the 64-bit limit. An asterisk marks a compression pool measured in base units. Automation uses normal item stack sizes, and the separate batch capability exposes at most 2,147,483,647 per item key.

Cycle the access mode to allow both directions, insert only, extract only or read only.

The interface blocks adjacent ME Storage Buses from mounting its projection to avoid duplicate counting and loops.

<RecipeFor id="advanced_bulk_storage:bulk_storage_interface" />

