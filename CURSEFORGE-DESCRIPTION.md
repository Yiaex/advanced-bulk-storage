# MEGA Cells: Advanced Bulk Storage

## English

**Advanced Bulk Storage** is an item-storage add-on for Applied Energistics 2 and MEGA Cells. Use tags and filters to keep related resources together, then access them from your existing ME terminals, automation, and drives.

### Bulk Storage Cells

Four tiers support **64, 256, 1,024, and 4,096 item types** by default. Each distinct item and data-component combination has its own quantity, up to **9,223,372,036,854,775,807** items. Renamed or enchanted variants can share a cell while keeping separate counts.

Set a whitelist by tag, mod namespace, or exact item ID, depending on the cell tier and its upgrades. For example, a Tag Bulk Storage Cell configured with `#c:ingots` can hold iron, gold, and copper ingots together. Hover over a cell for an inventory preview, or open its inventory page to search and check exact quantities.

### ME Automation

- **Bulk Storage Interfaces** let pipes and machines access selected cells. The basic interface binds one cell. The advanced interface binds up to **16** and adds binding priority, per-side access, a secondary whitelist, and redstone control.
- **Bulk Storage Migrators** move stock in batches between compatible cells. Choose the direction and endpoints, and apply a filter when you only want to move part of an inventory.
- **Upgrade cards** support reversible compression, storage limits with optional overflow disposal, energy savings, broader tag filters, and automatic sorting.
- **Storage equipment** includes a 32-slot Bulk Storage Drive, an Advanced Cell Workbench for editing and copying cell settings, a Bulk Storage Manager for network-wide browsing, and a Bulk Storage Monitor with redstone thresholds.

### Getting Started

1. Craft a Tag Bulk Storage Cell using an empty MEGA Bulk Item Storage Cell, a **64M Storage Component**, a Logic Processor, and an Engineering Processor.
2. Right-click while holding the cell to configure its filter, then install it in a powered ME Drive.
3. Open the AE2 guide for upgrade recipes and setup examples. The mod and its GuideME pages are available in English and Simplified Chinese.

### Compatibility and License

- **Minecraft 1.21.1**, **Java 21**, and **NeoForge 21.1.265 or later in the 21.1 series**.
- Requires **Applied Energistics 2**, **MEGA Cells**, and **GuideME**. Install matching versions on the client and server. JEI and Jade integrations are optional.
- The mod code is available under **MIT**. Textures derived from AE2 and MEGA Cells are distributed under **CC BY-NC-SA 3.0**. The JAR includes third-party notices and credits.

---

## 简体中文

**高级大宗存储**是 Applied Energistics 2 与 MEGA Cells 的物品存储附属模组。用标签和过滤规则把同类资源放进一张盘，再通过现有 ME 终端、自动化设备和驱动器存取。

### 大宗存储元件

四级存储元件默认可分别容纳 **64、256、1,024 和 4,096 种物品**。每个完整的物品与数据组件组合独立计数，单个库存键最多可存 **9,223,372,036,854,775,807** 件。带自定义名称或附魔的变体可以存进同一张盘，并分别记录数量。

可以按元件等级和已安装的升级卡，用标签、模组命名空间或精确物品 ID 设置白名单。例如给标签大宗盘设置 `#c:ingots`，就能把铁锭、金锭和铜锭放在一起。鼠标悬停可预览库存，打开库存页则可搜索并查看精确数量。

### ME 自动化

- **大宗存储接口**让管道和机器访问选定的存储元件。普通接口可绑定 1 张盘，高级接口最多绑定 **16 张**，并支持绑定优先级、分面存取、二次白名单和红石控制。
- **大宗存储迁移器**可在兼容的盘之间批量搬运库存。可以选择方向、端点和过滤规则，只搬运指定的资源。
- **升级卡**提供可逆压缩、库存限额与可选的溢出销毁、节能、扩展标签过滤和自动归类。
- **辅助设备**包括 32 槽大宗驱动器、可编辑和复制单盘配置的高级元件工作台、分页浏览整个网络存储元件的大宗存储管理器，以及支持红石阈值的大宗存储监视器。

### 快速上手

1. 使用空白 MEGA 大宗物品存储元件、**64M 存储组件**、逻辑处理器和工程处理器制作标签大宗盘。
2. 手持存储元件右键配置过滤规则，再装入已供电的 ME 驱动器。
3. 打开 AE2 指南查看后续升级配方与设备用法。模组界面和 GuideME 指南均提供英文及简体中文。

### 兼容性与许可

- **Minecraft 1.21.1**、**Java 21** 和 **NeoForge 21.1.265 起的 21.1.x 版本**。
- 需要 **Applied Energistics 2**、**MEGA Cells** 和 **GuideME**。客户端与服务端请安装相同版本，JEI 与 Jade 联动为可选。
- Mod 代码采用 **MIT** 许可，基于 AE2 与 MEGA Cells 改绘的贴图采用 **CC BY-NC-SA 3.0** 许可。JAR 内附第三方授权和署名说明。
