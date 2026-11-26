---
navigation:
  title: "Storage Limit Card"
  parent: upgrade_cards.md
  position: 11
item_ids:
  - storage_limit_card
---

# Storage Limit Card

Install the card and open **Limits** in the cell editor. For example, use `100 each; 1000 total; 300 #c:ingots; 64 minecraft:iron_ingot`. Counts use base units for compression pools. Default rules reject excess. Lowering a limit never removes existing stock.

To enable disposal for a specific limit, append `!void` after its selector, for example `100 minecraft:iron_ingot !void`. Save the same rules twice to confirm. A direct bulk interface tries every bound disk before disposing of leftovers. Reject-only limits, type capacity, energy, permission and the whitelist still apply. ME routing, auto-sort and migration use rejection. The manager and monitor show the cumulative discarded count. The server option `upgrades.allowOverflowDiscard` disables disposal globally.

Changing the whitelist on a disk with disposal configured requires confirmation. When the disposal counter reaches its 64-bit limit the interface refuses further disposal.

<RecipeFor id="advanced_bulk_storage:storage_limit_card" />
