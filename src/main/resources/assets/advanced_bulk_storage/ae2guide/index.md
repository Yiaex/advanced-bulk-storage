---
navigation:
  title: "Add-on: Advanced Bulk Storage"
  position: 85
---

# MEGA Cells: Advanced Bulk Storage

An Advanced Bulk Storage cell holds independent quantities for many item types. Items with different names, enchantments, damage or other data components occupy separate entries even if they share a registry ID.

1. Craft a [Tag Bulk Storage Cell](tag_bulk_storage_cell.md) with a blank MEGA Bulk Item Storage Cell, a 64M Storage Component, a logic processor and an engineering processor.
2. Use the new cell in hand and select **Metal ingots** in the [Unified Filter Editor](filter_editor.md).
3. Save the rule and install the cell in a powered AE2 drive. Use a storage terminal to send iron, gold and copper ingots to the network.
4. Use the AE2 Cell Workbench to add an exact item partition or install an [Extended Tag Card](extended_tag_card.md).

Cell quantities use arbitrary-precision integers. AE2 counts and per-call transfers are bounded by the signed long range.

- [Storage Cells and Tier Upgrades](storage_cells.md)
- [Storage Interfaces](storage_interfaces.md)
- [Storage Migrators](storage_migrators.md)
- [Upgrade Cards](upgrade_cards.md)
- [Storage Equipment](support_devices.md)
