---
navigation:
  title: "ME Mod Bulk Storage Cell"
  parent: storage_cells.md
  position: 12
item_ids:
  - mod_bulk_storage_cell
---

# ME Mod Bulk Storage Cell

Select one or more item namespaces with a rule such as `@ae2 | @megacells`. A namespace rule excludes equipment unless an exact equipment ID is permitted by the cell and the server.

Defaults are **1024** types, **4** upgrade slots and **32 AE/t** of idle drain in a drive. Each complete item key can store quantities beyond the signed long range. AE2 reports up to 9,223,372,036,854,775,807 per key. Use the cell in hand to configure it, then mount it in a powered, online AE2 Drive. Exact AE2 Cell Workbench partitions apply alongside the filter.

Hover over the cell to see the actual items and their quantities. Hold Shift to show more. The preview shows up to 9 or 36 item types by default, depending on GUI scale and available space. K, M, G, T, P, and E represent powers of 1,000. Use the cell and select Inventory, then hover over an entry to see its precise count.

<RecipeFor id="advanced_bulk_storage:mod_bulk_storage_cell" />

[Unified Filter Editor](filter_editor.md)
