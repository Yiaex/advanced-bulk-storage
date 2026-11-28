# MEGA Cells: Advanced Bulk Storage

[English](README.en.md)

这是 Applied Energistics 2 和 MEGA Cells 的扩展，适用于 Minecraft **1.21.1** 和 NeoForge **21.1.265 至 21.2 之前**。Mod ID 为 `advanced_bulk_storage`。四种大宗元件可在原生 ME 驱动器内按完整物品键存储任意精度的库存，鼠标悬停可查看真实库存图标。扩展标签卡可解锁更多过滤语法，手持元件可管理白名单并分页查看当前库存。

`1.0.0` 汇总了大宗接口性能优化、配方调整、空盘锻造样板和界面修复。升级时在客户端和服务端同时替换 Mod JAR。[1.0.0 更新日志](CHANGELOG-1.0.0.md) · [版本历史](CHANGELOG.md) · [功能清单与手动验收](RELEASE-0.6.0.md)

| 环境 | 已构建和测试的版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 |
| NeoForge | 21.1.265 |
| Applied Energistics 2 | 19.2.20 |
| MEGA Cells | 4.11.0 |
| GuideME | 21.1.22 |
| JEI（可选） | 19.50.0.414 |
| Jade（可选） | 15.10.6 |

客户端和服务端均需安装本模组，以及必需前置 Applied Energistics 2、MEGA Cells 和 GuideME。JEI 和 Jade 提供可选集成。

## 主要功能

- 四级大宗存储元件支持标签、模组和精确物品过滤，提供可搜索、排序的库存界面。
- 大宗存储接口支持管道和自动化访问，三级迁移器可在盘之间批量转移物品。
- 压缩、限额、节能和自动归类升级卡可调整盘的行为，工作台、驱动器、管理器和监视器提供配置、挂载和红石阈值功能。

## 使用

1. 在工作台用空白 MEGA 大宗物品存储元件、逻辑处理器、工程处理器和 1 枚 64M 存储组件制作标签大宗盘。之后在锻造台使用上一级盘升级，具体的模板位和附加位材料可在指南的“大宗存储元件”章查看。
2. 手持元件右键打开过滤编辑器，选择适用预设或输入白名单。标签大宗盘可直接使用 `#c:ingots`，模组盘通常使用 `@ae2`。量子大宗盘可组合标签、命名空间和精确物品 ID。点击保存提交规则。
3. 将元件装入供电且有频道的 ME 驱动器，通过 ME 存储终端存取。AE2 元件工作台的精确分区和存储过滤规则同时生效。
4. 在 AE2 元件工作台安装扩展标签卡可解锁完整语法。移除后原表达式会保留。超出当前权限的规则暂停接收，仍可提取已有库存。
5. 点击“库存”查看当前元件。可按名称、注册 ID、`@命名空间` 或 `#标签` 查找，按名称或数量排序。悬停查看详细数量。
6. 安装 JEI 后，可将列表物品拖到编辑器的“+”槽，查看真实注册标签及支持的筛选条件。点击条件将其填入草稿，保存后才会改变盘的白名单。
7. 鼠标悬停大宗盘可预览数量最多的 9 种物品，按住 Shift 默认最多显示 36 种。每行最多 9 个图标，空间不足时会缩减可用行数。
8. 普通或高级大宗存储接口接入供电的 ME 网络后，右键选择该网络已挂载的大宗盘。普通接口绑定 1 张盘，基础耗电 4 AE/t。高级接口绑定最多 16 张盘，基础耗电 16 AE/t，支持绑定优先级、独立面模式、二次白名单和红石控制。两者各占 1 个频道。
9. 将来源盘与目标盘放入已供电的迁移器 A、B 槽，设好方向后手动开始。普通、超频、量子级满配分别为 1,048,576、16,777,216、2,147,483,647 件/t。额定值受实际库存、规则、供电和全服任务预算约束。
10. 多类型压缩卡按可逆配方建立独立资源池。GUI 和 Tooltip 按物理最小单位展示池库存。限额卡在同一盘的过滤编辑器中配置。节能卡默认每张乘 0.8。归类卡须在过滤编辑器中启用，目前从同一驱动器内其他适用盘定向归集。
11. 高级元件工作台正常右键安装升级卡，空手潜行右键打开盘的统一编辑器。大宗驱动器默认 32 槽。大宗管理器分页查找同网元件。监视器可对物品、标签或模组的库存数量设置红石阈值。

| 等级 | 默认类型 | 升级槽 | 驱动器待机耗电 |
| --- | ---: | ---: | ---: |
| 标签 | 64 | 2 | 8 AE/t |
| 高级标签 | 256 | 4 | 16 AE/t |
| 模组 | 1,024 | 4 | 32 AE/t |
| 量子 | 4,096 | 6 | 64 AE/t |

盘内库存和压缩池使用 `BigInteger`。服务端配置 `itemsPerType` 的原默认值 `9223372036854775807` 代表不设单键数量上限。低于该值的配置继续生效，限额卡也可设置明确的有限上限。改变规则和调低数量或类型上限时，现有库存仍可提取。

支持有限的 `#c:ingots/*` 一类通配符，匹配项按加载后的注册表和标签计算。单独的 `*`、可以覆盖全部已注册物品的规则和旧黑名单都会停用入库。旧规则保留原文，重新保存有效的白名单后才恢复入库。宽泛匹配默认排除装备类。适用盘权限及服务端设置允许时，可用明确的物品 ID 添加特定装备。

## 构建与测试

安装 **JDK 21** 后，在仓库根目录运行自带的 **Gradle 9.2.1 Wrapper**。首次构建需要联网下载 Gradle 和依赖。

```powershell
./gradlew.bat build
./gradlew.bat runGameTestServer
./gradlew.bat runClient
./gradlew.bat runClient -PwithJei
```

Linux 或 macOS 使用 `./gradlew` 执行相同任务。游戏运行目录分别是 `run-gametest`、`run-client` 和 `run-server`。构建包位于 `build/libs`，使用 `advanced_bulk_storage-1.0.0.jar` 安装模组。`src/main` 包含主源码和发布资源，`src/gameTest` 包含游戏测试。

## 客户端配置

`config/advanced_bulk_storage-client.toml` 中的 `inventoryTooltip.normalItems` 默认 9，范围 1–27。`inventoryTooltip.shiftItems` 默认 36，范围 1–54。受游戏窗口大小和 GUI 缩放限制，Tooltip 可能显示更少的图标。

## 服务端配置

目标版本的 NeoForge 在实例的 `config/advanced_bulk_storage-server.toml` 生成配置。

| 键 | 用途 |
| --- | --- |
| `cells.<注册 ID>.types` | 按完整物品键计算的类型上限 |
| `cells.<注册 ID>.itemsPerType` | 每个键的数量上限 |
| `cells.<注册 ID>.upgrades` | AE2 元件工作台的升级槽数量 |
| `cells.<注册 ID>.tags` | 元件初始可配置标签数 |
| `cells.<注册 ID>.idleDrain` | AE2 驱动器计算待机耗电时使用的数值 |
| `filter.identifierLength` | 单个标识符的字符数上限 |
| `filter.expressionLength` | 整条表达式的字符数上限 |
| `filter.expressionDepth` | 括号和非操作的嵌套深度 |
| `filter.conditions` | 原子条件数上限 |
| `filter.namespaceRules` | 单条规则允许使用的命名空间条件数 |
| `filter.globMatches` | 每个通配符允许展开的注册项数 |
| `filter.allowExactEquipment` | 按明确物品 ID 接收单件特定装备，默认开启 |
| `filter.extendedTags` | 前三级装卡后的标签上限 |
| `filter.quantumExtendedTags` | 量子级装卡后的标签上限 |
| `background.microsecondsPerTick` | 全服后台软时间预算，默认 2000 µs |
| `background.keysPerTick` | 迁移和归类共用的物品键处理预算，默认 2048 |
| `background.adaptiveMspt` | 高 MSPT 时自适应降低后台预算，默认开启 |
| `migration.aePerThousandItems` | 批量模式每 1000 件附加能耗，默认 1 AE |
| `migration.compatibilityAePerItem` | AE2 兼容模式每件附加能耗，默认 1 AE |
| `migration.<迁移器 ID>IdleAe` | 对应迁移器的基础 AE/t，依次默认 32、128、512 |
| `upgrades.energyCardMultiplier` | 每张节能卡的耗电倍率，默认 0.8 |
| `upgrades.minimumEnergyFraction` | 节能卡的最低耗电比例，默认 0.1 |
| `bulkDrive.activeSlots` | 可插入槽位数，默认 32 |
| `bulkDrive.baseIdleAe` | 大宗驱动器基础耗电，默认 4 AE/t |

降低升级槽数量时，元件会保留已占用槽位，直到其中的卡被取出。修改运行中的待机耗电后可重新装入元件，促使 AE2 驱动器刷新待机耗电。

服务端操作员可使用 `/absdiagnostics` 查看排队与休眠任务、当前预算比例、上一 Tick 的实际耗时和处理键数。

整合包可以通过 `#advanced_bulk_storage:equipment` 和 `#advanced_bulk_storage:equipment_exceptions` 调整装备识别。规则索引随数据包标签重载而失效。

## 整合包预设

在数据包中放置 `data/<namespace>/advanced_bulk_storage/filter_preset/<id>.json`。使用相同命名空间和路径可覆盖已有预设，设置 `"enabled": false` 可关闭该预设。

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

`name` 和 `description` 可指向资源包的语言键。设置 `requirement` 为 `full` 会要求元件具备完整过滤权限。预设引用的数据包和 KubeJS 标签按服务器实际加载的注册信息校验。

## 接入库存

AE2 挂载端可使用 `StorageCells` 和 `StorageCell`。AE2 单次请求最多可存取 `Long.MAX_VALUE` 件，超出该值的库存按 `Long.MAX_VALUE` 上报，可继续分批提取。普通物流可按六面访问 NeoForge ItemHandler。服务端 `BulkItemAccess.CAPABILITY` 按完整 AEItemKey 查询和传输，可见量与单次调用上限均为 `Integer.MAX_VALUE`。普通 ItemHandler 返回符合物品最大堆叠数的正常栈。相邻 AE2 存储总线无法重新挂载接口投影。

## 验证范围

当前版本为 21 个核心 ID 均提供可执行的基础功能。迁移器当前支持机内实体盘配对。归类的安全边界是同一驱动器内的本 Mod 非压缩库存盘。限额当前拒绝超出的资源，管理器提供只读远程库存。完整覆盖、具体测试与仍待验收的项目见版本报告。

负载数据见 [PERFORMANCE.md](PERFORMANCE.md)。版本和许可见 [CHANGELOG.md](CHANGELOG.md) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
