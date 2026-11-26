# Third-party notices

The Advanced Bulk Storage code is distributed under the MIT license. Runtime dependencies are installed separately.

- Applied Energistics 2 19.2.20 uses LGPL-3.0 for implementation and MIT for its API sources. The editor calls AE2's screen and style framework, whose widgets and shared style resources resolve from the installed AE2 mod. [AE2 source and licenses](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/neoforge/v19.2.20)
- MEGA Cells 4.11.0 uses LGPL-3.0. Its materials and bulk cell are referenced by recipe ID. [MEGA Cells license](https://github.com/62832/MEGACells/blob/4.11.0/LICENSE)
- GuideME 21.1.22 publishes its LGPL-3.0 license and additional notices for bundled libraries. The installed GuideME renders the add-on pages within the AE2 guide. [GuideME license and notices](https://github.com/AppliedEnergistics/GuideME/blob/v21.1.22/LICENSE.md)
- NeoForge 21.1.265 provides the mod-loader and game integration APIs. [NeoForge license](https://github.com/neoforged/NeoForge/blob/1.21.x/LICENSE.txt)

## Derived textures

The four bulk storage cell item sprites in `assets/advanced_bulk_storage/textures/item/` adapt the Applied Energistics 2 19.2.20 `item_storage_cell_1k.png` silhouette and pixel layout and use shell colors sampled from MEGA Cells 4.11.0 `bulk_item_cell.png`. The adapted sprites recolor each tier's core and clear RGB values outside the silhouette. Credit to AlgorithmX2 et al., Ridanisaurus Rid, and the MEGA Cells contributors. The adapted sprites are distributed under CC BY-NC-SA 3.0. [AE2 texture license](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/neoforge/v19.2.20#license) · [MEGA Cells asset license](https://github.com/62832/MEGACells/tree/4.11.0#license) · [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/)
