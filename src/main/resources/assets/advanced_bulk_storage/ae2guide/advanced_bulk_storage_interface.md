---
navigation:
  title: "Advanced ME Bulk Storage Interface"
  parent: storage_interfaces.md
  position: 61
item_ids:
  - advanced_bulk_storage_interface
---

# Advanced ME Bulk Storage Interface

Connect to a powered AE2 network with a free channel. This interface draws 16 AE/t, uses one channel and can bind up to 16 bulk storage cells.

Search by cell name, tier, drive coordinates or filter rule. The cell icon shows its tier. Filter to **Bound** to manage selected cells, then use the mouse wheel or scrollbar to navigate. Click a row to add or clear its binding. The number on a selected row is its access priority. Click that number to move that cell to first priority. Any suspended binding appears offline and can be cleared individually.

Hover any cell, then hold **Shift** for an expanded preview of its actual inventory, filter and access settings. A compression pool shows the base units shared by its extractable forms. The preview reflects quantities above the signed long range. The separate batch capability exposes up to 2,147,483,647 per item key to connected automation.

Choose a face to set its access mode. Open **Whitelist** for the shared filter editor. An empty secondary whitelist leaves the bound cells' own admission rules in effect. Set the redstone gate to signal, no signal, or ignored.

The interface blocks adjacent ME Storage Buses from mounting its projection to avoid duplicate counting and loops.

<RecipeFor id="advanced_bulk_storage:advanced_bulk_storage_interface" />

