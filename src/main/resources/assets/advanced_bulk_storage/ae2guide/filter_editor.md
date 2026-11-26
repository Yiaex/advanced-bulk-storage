---
navigation:
  title: "Unified Filter Editor"
  parent: storage_cells.md
  position: 3
---

# Unified Filter Editor

Use the cell in hand. Edit a draft, then click **Save** to request validation on the server. The editor can leave an existing rule in place while you test a draft. **Close** discards changes made since the last valid save.

| Syntax | Meaning |
| --- | --- |
| `#c:ingots` | Items in a registered tag |
| `@ae2` | Items whose registry IDs are in a namespace |
| `minecraft:iron_ingot` | All variants of this registry ID |
| `#c:ingots/*` | Registered subtags of metal ingots |
| `!`, `&`, `\|`, `(…)` | NOT, AND, OR and grouping |

Every successful branch of a rule needs a positive condition. The rule `#c:ingots & !#c:ingots/iron` allows ingots other than iron. A blank rule, missing tag, or unsupported legacy rule pauses insertion. Already stored resources remain extractable.

Drag an item from JEI onto the slot marked **+** to inspect its tags, exact item ID and namespace. You can also click the slot with an item on the cursor. **Preview** lists the registered item IDs matching the draft. **Inventory** shows the current cell's stock. Hover over an entry to read its exact quantity.

**Favorite** saves the draft as a personal preset using the search text as an optional title. **History** lists recent saved rules. **Copy** and **Paste** exchange rules as text. Broad filters skip equipment. Exact equipment IDs depend on the cell and server configuration.

Default limits are 1,024 characters, sixteen levels of nesting and 128 conditions. The tag allowance is shown in the editor and depends on the cell and the [Extended Tag Card](extended_tag_card.md). Data packs and KubeJS can supply tags. Missing tags are reported after the server reloads its data.
