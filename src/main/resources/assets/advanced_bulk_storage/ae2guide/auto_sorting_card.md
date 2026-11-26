---
navigation:
  title: "Auto-Sorting Card"
  parent: upgrade_cards.md
  position: 20
item_ids:
  - auto_sorting_card
---

# Auto-Sorting Card

Rated for up to 65536 items/t. Install exactly one sorting card in the cell workbench, configure a valid whitelist, and turn sorting on in the cell's filter editor.

Sorting consolidates matching stock from ABS cells and compression pools in powered drives within the same ME network and save chunk. Higher storage priority wins. Drive position and slot break ties, and higher-ranked destinations do not feed lower-ranked ones.

A shared chunk task combines stock-change notifications for the same item key, using the same server-wide budget as migrators. Filters, limits, capacity, energy and channel availability affect actual throughput.

<RecipeFor id="advanced_bulk_storage:auto_sorting_card" />

