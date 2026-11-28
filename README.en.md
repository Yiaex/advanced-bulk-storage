# MEGA Cells: Advanced Bulk Storage

[简体中文](README.md)

**Advanced Bulk Storage** is an add-on for Applied Energistics 2 and MEGA Cells on Minecraft **1.21.1** and NeoForge **21.1.265 up to but excluding 21.2**. Its mod ID is `advanced_bulk_storage`. Four bulk storage cells keep arbitrary-precision quantities per exact item key in standard ME drives. Hold a cell to edit its allowlist or browse its inventory, and hover over it to preview its contents.

Version 1.0.0 includes bulk interface performance improvements, recipe changes, a smithing template for empty cells, and interface fixes. When upgrading, replace the mod JAR on both the client and server. [1.0.0 changelog (Chinese)](CHANGELOG-1.0.0.md) · [Version history (Chinese)](CHANGELOG.md) · [Feature list and manual checks (Chinese)](RELEASE-0.6.0.md)

## Requirements

Install the mod in the `mods` directory on both the Minecraft client and server. Both sides require Applied Energistics 2, MEGA Cells, and GuideME. JEI and Jade are optional integrations.

| Component | Build and test version |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 |
| NeoForge | 21.1.265 |
| Applied Energistics 2 | 19.2.20 |
| MEGA Cells | 4.11.0 |
| GuideME | 21.1.22 |
| JEI (optional) | 19.50.0.414 |
| Jade (optional) | 15.10.6 |

## Features

- Four tiers of bulk storage cells support tag, namespace, and exact item filters, with a searchable and sortable inventory view.
- Bulk storage interfaces connect cells to automation, and three tiers of migrators transfer items in bulk between cells.
- Compression, storage limit, energy saving, and auto-sorting cards customize cell behavior. Dedicated machines make it possible to configure, mount, search, or monitor cells and set redstone thresholds.

## Usage

1. Craft an ME Tag Bulk Storage Cell with an empty MEGA bulk item storage cell, a Logic Processor, an Engineering Processor, and a 64M Storage Component. Upgrade from the preceding tier at a Smithing Table. The **Bulk Storage Cells** section of the guide lists the template and addition for each tier.
2. Right-click while holding a cell to open its filter editor. Choose a preset or enter an allowlist and save it. Tag cells accept filters such as `#c:ingots`, and mod cells accept namespaces such as `@ae2`. Quantum cells can combine tags, namespaces, and exact item IDs.
3. Mount the cell in a powered ME Drive with a channel. Use an ME Storage Terminal to insert and extract items. Exact partitioning from the AE2 Cell Workbench applies alongside the bulk storage filter.
4. Install an Extended Tag Card at the AE2 Cell Workbench to unlock the full filter syntax. Removing the card preserves the expression. A rule that exceeds the cell’s current permissions stops accepting new items, and its existing stock remains extractable.
5. Select **Inventory** to browse the cell. Search by name, registry ID, `@namespace`, or `#tag`. Sort by name or count and hover for the detailed quantity.
6. With JEI installed, drag an item from the JEI list into the editor’s **+** slot to see its registered tags and supported filter conditions. Click a condition to add it to the draft, then save to update the allowlist.
7. Hover over a bulk cell to preview its nine most abundant items. Holding Shift shows up to 36 by default. Each row holds up to nine icons, and the tooltip uses fewer rows when screen space is limited.
8. Connect a regular or advanced ME Bulk Storage Interface to a powered network and right-click it to bind mounted bulk cells from that network. The regular interface binds one cell and has a base draw of 4 AE/t. The advanced interface binds up to 16 and has a base draw of 16 AE/t. It supports binding priorities, independent side modes, a second allowlist, and redstone control. Each interface uses one channel.
9. Place a source and destination cell in slots A and B of a powered migrator. Choose a direction, then start it manually. The standard, overclocked, and quantum migrators have fully upgraded rates of 1,048,576, 16,777,216, and 2,147,483,647 items/tick respectively. Actual throughput depends on stock, filtering, power, and the server-wide task budget.
10. The Multi-Compression Card creates separate resource pools from reversible recipes. The UI and tooltips report each pool in its smallest physical unit. Configure the Storage Limit Card in the cell’s filter editor. Each Energy Saving Card multiplies energy use by 0.8 by default. Enable Auto-Sorting Cards in the filter editor to consolidate matching items from eligible cells in the same drive.
11. Right-click an ME Advanced Cell Workbench to install upgrades. Sneak-right-click with an empty hand to open the cell’s editor. The ME Bulk Storage Drive has 32 slots by default. The ME Bulk Storage Manager provides a paginated view of cells on the same network. The ME Bulk Storage Monitor emits a redstone signal at a configurable stock threshold for an item, tag, or namespace.

| Cell tier | Default item types | Upgrade slots | Drive idle draw |
| --- | ---: | ---: | ---: |
| Tag | 64 | 2 | 8 AE/t |
| Advanced Tag | 256 | 4 | 16 AE/t |
| Mod | 1,024 | 4 | 32 AE/t |
| Quantum | 4,096 | 6 | 64 AE/t |

Cells and compression pools use `BigInteger` quantities. The old `itemsPerType` default of `9223372036854775807` now disables the per-key cap. Lower server config values and explicit storage limit card rules still apply. Existing stock remains extractable after a rule changes or a quantity or type limit is lowered.

Filters support bounded wildcards such as `#c:ingots/*`. Matches are computed from the loaded registries and tags. A bare `*`, any rule that covers every registered item, and legacy blocklists stop new insertions. The old rule text remains available until you save a valid allowlist. Broad filters exclude equipment by default. An exact item ID can admit specific equipment when the cell and server settings permit it.

## Build and test

Install **JDK 21** and run the included **Gradle 9.2.1 Wrapper** from the repository root. The first build needs an internet connection to download Gradle and dependencies.

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
.\gradlew.bat runClient -PwithJei
```

On Linux or macOS, use `./gradlew` for these tasks. The development instances use `run-gametest`, `run-client`, and `run-server`. Build outputs are in `build/libs`. Install `advanced_bulk_storage-1.0.0.jar` in Minecraft. Production code and assets are in `src/main`, and GameTests are in `src/gameTest`.

## Client configuration

In `config/advanced_bulk_storage-client.toml`, `inventoryTooltip.normalItems` defaults to 9 and accepts values from 1 to 27. `inventoryTooltip.shiftItems` defaults to 36 and accepts values from 1 to 54. Window size and GUI scale can reduce the number of icons shown.

## Server configuration

NeoForge generates `config/advanced_bulk_storage-server.toml` in the game instance.

| Key | Purpose |
| --- | --- |
| `cells.<registry ID>.types` | Maximum number of distinct exact item keys |
| `cells.<registry ID>.itemsPerType` | Maximum quantity per key |
| `cells.<registry ID>.upgrades` | Number of upgrade slots in the AE2 Cell Workbench |
| `cells.<registry ID>.tags` | Number of tags the cell can configure without upgrades |
| `cells.<registry ID>.idleDrain` | The cell’s contribution to AE2 drive idle energy use |
| `filter.identifierLength` | Maximum character count for one identifier |
| `filter.expressionLength` | Maximum character count for a full expression |
| `filter.expressionDepth` | Maximum nesting depth for parentheses and negation |
| `filter.conditions` | Maximum number of atomic conditions |
| `filter.namespaceRules` | Maximum number of namespace conditions per rule |
| `filter.globMatches` | Maximum registry entries each wildcard can match |
| `filter.allowExactEquipment` | Allow specific equipment by exact item ID. Enabled by default |
| `filter.extendedTags` | Tag limit for the first three tiers with a card installed |
| `filter.quantumExtendedTags` | Tag limit for the Quantum tier with a card installed |
| `background.microsecondsPerTick` | Server-wide soft time budget. Defaults to 2,000 µs |
| `background.keysPerTick` | Item key budget shared by migration and sorting. Defaults to 2,048 |
| `background.adaptiveMspt` | Lower the background budget at high MSPT. Enabled by default |
| `migration.aePerThousandItems` | Extra energy per 1,000 items in bulk mode. Defaults to 1 AE |
| `migration.compatibilityAePerItem` | Extra energy per item in AE2 compatibility mode. Defaults to 1 AE |
| `migration.<migrator ID>IdleAe` | Migrator base draw. The three tiers default to 32, 128, and 512 AE/t |
| `upgrades.energyCardMultiplier` | Energy draw multiplier per Energy Saving Card. Defaults to 0.8 |
| `upgrades.minimumEnergyFraction` | Minimum energy fraction. Defaults to 0.1 |
| `bulkDrive.activeSlots` | Number of slots available for cells. Defaults to 32 |
| `bulkDrive.baseIdleAe` | Base idle draw of the bulk drive. Defaults to 4 AE/t |

After lowering the upgrade slot count, occupied slots remain available until their cards are removed. If you change the idle draw while a cell is mounted, reinsert the cell to make its AE2 drive refresh the value.

Server operators can use `/absdiagnostics` to see queued and sleeping tasks, the current budget ratio, the previous tick’s runtime, and the number of keys processed.

Modpacks can use `#advanced_bulk_storage:equipment` and `#advanced_bulk_storage:equipment_exceptions` to adjust equipment detection. Reloading data pack tags invalidates the rule index.

## Modpack presets

Place presets at `data/<namespace>/advanced_bulk_storage/filter_preset/<id>.json`. A matching namespace and path overrides an existing preset. Set `"enabled": false` to disable it.

```json
{
  "name": "preset.example.steel",
  "description": "preset.example.steel.description",
  "category": "metals",
  "icon": "minecraft:iron_ingot",
  "expression": "#c:ingots/steel",
  "weight": 10,
  "requirement": "any",
  "enabled": true
}
```

The `name` and `description` values can reference language keys from a resource pack. Set `requirement` to `full` to require full filter permissions. Data pack and KubeJS tags referenced by presets are validated against the registries loaded on the server.

## Inventory integration

AE2 integrations can use `StorageCells` and `StorageCell`. An AE2 call can move up to `Long.MAX_VALUE` items. Counts above that value report `Long.MAX_VALUE` and can be withdrawn in batches. NeoForge transports can access an `ItemHandler` on all six sides. `BulkItemAccess.CAPABILITY` queries and transfers by exact `AEItemKey` on the server, with an `Integer.MAX_VALUE` limit per key and call. The standard item handler respects each item’s maximum stack size. An adjacent AE2 Storage Bus cannot remount storage exposed by a bulk interface.

## Verification scope

The 21 core IDs provide their basic functionality in this release. Migrators pair two physical cells mounted inside the machine, and auto-sorting processes eligible uncompressed bulk cells within the same drive. Storage limits reject excess insertions, and the manager provides read-only remote inventory. See the release notes for test coverage and remaining manual checks.

[Performance report (Chinese)](PERFORMANCE.md) · [Changelog (Chinese)](CHANGELOG.md) · [License](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md)
