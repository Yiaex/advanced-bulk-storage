# MEGA Cells: Advanced Bulk Storage 1.0.0

## English Changelog

This release covers the changes from `0.6.1-dev` to `1.0.0`. It targets Minecraft 1.21.1, Java 21, and NeoForge 21.1.265 or later in the 21.1 series. It was built against AE2 19.2.20, MEGA Cells 4.11.0, and GuideME 21.1.22.

### Performance and ME Networks

- ME Bulk Storage Interfaces cache their online bound cells. Slot reads check only the cells that can hold the requested item and stop counting at the item's stack limit.
- Each insertion creates one item key. The interface checks mounted cells in binding order and stops once the entire batch is accepted. Bindings are resolved against the current ME grid after a split, merge, or change in drive availability.
- Inventory previews in the cell selection list appear sooner. Hover previews have a dedicated budget when server MSPT pauses background migration. A cell's previous preview remains visible while it refreshes.
- Auto-sorting reuses scan results and working buffers. Source cells later in the queue continue to get processing time during sustained extraction.

### Recipes and Autocrafting

| Bulk Storage Cell | Ingredients |
| --- | --- |
| ME Tag Bulk Storage Cell | Blank MEGA Bulk Item Storage Cell, Logic Processor, Engineering Processor, and one 64M Storage Component |
| ME Advanced Tag Bulk Storage Cell | ME Tag Bulk Storage Cell, Engineering Processor, and one 256M Storage Component |
| ME Mod Bulk Storage Cell | ME Advanced Tag Bulk Storage Cell, one 64M Storage Component, and one 256M Storage Component |
| ME Quantum Bulk Storage Cell | ME Mod Bulk Storage Cell and two 256M Storage Components |

- Fixed AE2 smithing patterns that could break autocrafting chains by including a blank cell's UUID. Patterns for cells whose only added data component is a UUID now match blank cells, including existing patterns. Cells with stored items or settings still match by their full data components. Manual smithing upgrades preserve inventory and settings.

### Interface, Textures, and Guide

- Increased row spacing in the interface's cell selection list so coordinates and type counts fit inside each row.
- Fixed the overlap between the third inventory row and the hotbar in the Bulk Storage Drive, Interface, Migrator, and Filter Editor screens.
- Spaced the migrator's Cell A and Cell B slots farther apart and centered each label above its slot.
- Updated 12 item textures and 16 block textures.
- Reorganized the English and Simplified Chinese GuideME guides into five chapters covering storage cells, interfaces, migrators, upgrade cards, and storage equipment. Items still link directly to their guide pages and recipes.

### 0.6.1-dev Baseline Fixes

- Migration pauses when its direction, scope, source, or destination changes. Before committing a staged transfer, the migrator checks that the same cells are still in place.
- Compressed transfers update both cells before notifying listeners. New compressed stock wakes auto-sorting jobs for the applicable item forms.
- Bulk edits in the storage manager skip cells that were replaced or reconfigured while the edit was queued.
- Changing the whitelist of a cell with overflow disposal enabled requires a second confirmation. Once the disposal counter reaches the signed 64-bit limit, further overflow is rejected.
- Filtering or scrolling cancels hover requests for cells that leave the list view. Preview errors are shown in the interface and recorded in the server log.

---

## 简体中文更新日志

本次发布汇总从 `0.6.1-dev` 到 `1.0.0` 的变更，适用于 Minecraft 1.21.1、Java 21 和从 21.1.265 起的 NeoForge 21.1.x。构建依赖 AE2 19.2.20、MEGA Cells 4.11.0 和 GuideME 21.1.22。

### 性能与网络

- 大宗接口复用在线绑定盘的查询结果。逐槽读取只检查可能持有该物品的盘，计数达到物品堆叠上限后提前结束。
- 大宗接口入库只创建一次物品键，按绑定顺序检查实际挂载盘，接收完本批物品后停止查盘。ME 网格分裂、合并或驱动器可用状态变化后，接口根据当前网络重新定位绑定盘。
- 缩短选盘列表中库存预览的等待时间。后台迁移因服务器 MSPT 暂停时，悬停预览仍有独立预算。同一张盘刷新期间会显示上一份摘要。
- 自动归类复用扫描结果和工作缓冲。持续抽取时，排在后面的来源盘也能获得处理机会。

### 配方与自动合成

| 大宗存储元件 | 制作材料 |
| --- | --- |
| 标签大宗盘 | 空白 MEGA 大宗物品存储元件、逻辑处理器、工程处理器和 1 枚 64M 存储组件 |
| 高级标签大宗盘 | 标签大宗盘、工程处理器和 1 枚 256M 存储组件 |
| 模组大宗盘 | 高级标签大宗盘、1 枚 64M 和 1 枚 256M 存储组件 |
| 量子大宗盘 | 模组大宗盘和 2 枚 256M 存储组件 |

- 修复 AE2 锻造样板因空盘 UUID 导致的自动合成断链。只带 UUID 的空盘样板会按空白元件匹配，已有的这类样板可继续使用。带库存或配置的盘按完整数据组件匹配，手动锻造升级会保留盘的库存和配置。

### 界面、美术与指南

- 调整大宗接口选盘列表行距，修正坐标和类型容量数字贴边的问题。
- 修正大宗驱动器、接口、迁移器和过滤编辑器中背包第三排与快捷栏重叠的问题。
- 拉开迁移器 A、B 槽位，将两个标签分别居中置于对应槽位上方。
- 更新 12 张物品和 16 张方块贴图。
- 中英文 GuideME 指南统一分为存储元件、接口、迁移器、升级卡和辅助设备五章，可从物品直接打开对应说明与配方。

### 0.6.1-dev 基线修复

- 切换迁移方向、范围、来源或目标时暂停任务，暂存盘提交前复核是否为原来的盘。
- 压缩迁移完成两端库存更新后再通知观察者。新的压缩库存会唤醒相关形式的自动归类。
- 管理器批量编辑会跳过排队过程中被替换或配置已变更的盘。
- 修改已启用溢出销毁的盘的白名单时需要二次确认。累计销毁计数达到 LONG 上限后，后续超额物品会被拒收。
- 筛选或滚动后取消已离开视图的悬停预览，预览异常时会在界面显示状态并写入服务端日志。
