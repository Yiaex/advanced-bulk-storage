# Advanced Bulk Storage 0.3.0-dev 开发验收

本轮交付四种盘的可视化库存 Tooltip，以及普通和高级大宗存储接口。测试以 Minecraft 1.21.1 为基础。独立开发回归采用 NeoForge 21.1.265，整合包实机采用 New Age Science and Technology 2.2.2 的 NeoForge 21.1.267、AE2 19.2.20、MEGA Cells 4.11.0、GuideME 21.1.22、Jade 15.10.6 和 JEI 19.70.0.469。整合包测试使用现有超平坦世界的隔离副本。

## 库存 Tooltip

- 四种大宗盘悬停时直接显示真实 AEItemKey 的物品图标与实际数量。默认 9 种，Shift 默认最多 36 种，每行上限 9。布局按有效窗口宽度和高度减小行列数，余下类型另行提示。空盘直接显示为空。名称、过滤规则、类型占用、已安装升级与 GuideME 入口继续显示。
- 库存按 LONG 数量倒序，其次按物品 ID 与数据组件排序。渲染使用 AE2 的 `AEKeyRendering`。物品图标数量为 1，文字以整数运算格式化。LONG 最大值显示为 `9.2E`，精确值仍可在单盘库存页悬停查看。
- `BulkInventoryPreview` 只从已有持久化组件计算只读摘要。首次遇到一份新载荷时扫描库存行，以固定容量优先队列保留最多 54 个候选，再解码候选的完整物品键。客户端缓存按不可变 `CustomData` 对象身份区分，使用弱键并限制 128 条。盘保存新组件后自然使用新键，注册环境变化时清理缓存。
- 本次增加的 Tooltip 没有 C2S 查询包、摘要数据组件或持久化写入。网络沿用 0.2.0 的无损压缩 ItemStack 库存传输，客户端从已经同步的载荷得到局部预览。既有驱动器、创造模式回传与 Jade 路径继续可逆，原生物品栈仍可能包含完整的压缩库存。
- 旧盘的 `*` 为保留的规则原文。实测该规则不接受新增物品，且现有库存仍可显示及提取。Tooltip 标明需要重新配置，原库存和原规则原文均保留。

## 两种 ME 大宗存储接口

| 项目 | 普通 | 高级 |
| --- | --- | --- |
| 同一 AE2 网内绑定上限 | 1 张 | 16 张 |
| AE/t 待机与频道 | 4 AE/t，1 频道 | 16 AE/t，1 频道 |
| 六面接入 | 全局存取模式 | 各面独立存取模式 |
| 绑定优先级、二次白名单、红石 | — | 已接入 |
| 单键批量可见量及单次操作 | INT 上限 | INT 上限 |

接口只操作实际挂载的大宗盘。AE2 网格服务在驱动器挂载变动及节点增删时维护 UUID 索引。查询仅查所属网格和已绑定 UUID，盘取出、失去供电或频道后暂停访问。匹配到多个相同 UUID 时暂停该绑定。凡是成功的插入均经过目标盘自己的过滤。接口不作为 ME 存储提供者注册，对相邻 AE2 存储总线不公开伪库存，避免重新挂载重复计数。Forge 访问返回合法大小的物品栈。批量接口在服务端线程调用，仿真不会修改实际库存。数量变更沿用 AE2 大宗盘保存回调，只有物品键集合变化或挂载变化才重建 ItemHandler 槽位索引。

当前绑定覆盖安装在原版 ME 驱动器中的本 Mod 大宗盘。高级接口支持在 AE2 风格界面提交同一过滤引擎校验的规则，后续还需接入完整过滤编辑器、JEI 配置交互及压缩资源形式。未扩大 NeoForge 单栈数量，第三方物流速率由其接入方式决定。

## 实际验证

| 验证项 | 结果 |
| --- | --- |
| Gradle build 与 22 项专用 GameTest | 通过 |
| 四种盘空盘、单项、多项与 44 项库存 | 通过真实库存的摘要检查，覆盖刷新、超 INT、万亿和 LONG 上限 |
| 量子盘 4096 键 | 摘要选出最多 54 项，验证真实最高数量和精确物品键 |
| 普通及 Shift 显示预算 | 自动检查 9、36，以及 6 列 2 行裁减 |
| 旧盘、驱动器、掉落、容器、Jade 与创造回传 | 原有自动回归通过 |
| 普通和高级接口 | 真实 AE2 网格覆盖绑定限制、模拟与实际存取、INT 汇总、优先级、独立面模式、二次白名单、红石停用、重复 UUID 临时暂停、换槽恢复和配方加载 |
| GuideME 21.1.22 | 客户端编译全部 18 份中英文页面，未发现编译错误节点 |
| 原有超平坦世界副本 | 使用独立 JAR 登录了实机，悬停已有量子盘显示真实 64G 数量，已放置并打开高级接口，查看两种接口的中文指南和配方 |

仍需验证长时 FPS、MSPT、频繁改写时的冷缓存、远程多人、整合包里高级接口的满阵列实机部署、代理物流路径和 500 台接口压力。Shift 在游戏画面中的截屏单独标记为未取得，已用与渲染相同的限制逻辑测试展开数量。生产环境依据的包体和性能数据见 [PERFORMANCE.md](PERFORMANCE.md)。

## 21 种核心项目逐项状态

| 注册 ID | 当前状态 |
| --- | --- |
| `tag_bulk_storage_cell` | 可用，支持库存 Tooltip |
| `advanced_tag_bulk_storage_cell` | 可用，支持库存 Tooltip |
| `mod_bulk_storage_cell` | 可用，支持库存 Tooltip |
| `quantum_bulk_storage_cell` | 可用，支持库存 Tooltip |
| `extended_tag_card` | 可用 |
| `bulk_storage_interface` | 本轮加入，基础功能已验证 |
| `advanced_bulk_storage_interface` | 本轮加入，基础功能已验证。压缩与完整编辑器联动待补 |
| `bulk_storage_migrator` | 待实现 |
| `overclocked_bulk_storage_migrator` | 待实现 |
| `quantum_bulk_storage_migrator` | 待实现 |
| `multi_compression_card` | 待实现 |
| `storage_limit_card` | 待实现 |
| `energy_saving_card` | 待实现 |
| `auto_sorting_card` | 待实现 |
| `advanced_auto_sorting_card` | 待实现 |
| `overclocked_auto_sorting_card` | 待实现 |
| `quantum_auto_sorting_card` | 待实现 |
| `advanced_cell_workbench` | 待实现 |
| `bulk_storage_manager` | 待实现 |
| `bulk_storage_drive` | 待实现 |
| `bulk_storage_monitor` | 待实现 |

## 修改文件

| 位置 | 变更文件 |
| --- | --- |
| 项目根目录 | `build.gradle`、`README.md`、`CHANGELOG.md`、`PERFORMANCE.md`、`RELEASE-0.3.0.md` |
| `src/main/java/dev/advancedbulkstorage` | `BulkStorageMod.java`、`BulkTooltipConfig.java` |
| `client` | `BulkStorageClient.java`、`BulkInventoryTooltipRenderer.java`、`BulkInterfaceScreen.java` |
| `storage` | `BulkCellItem.java`、`BulkCellInventory.java`、`BulkInventoryPreview.java`、`BulkInventoryTooltip.java`、`BulkCellIndex.java` |
| `device`、`menu`、`mixin` | `BulkInterfaceBlock.java`、`BulkInterfaceEntity.java`、`BulkItemAccess.java`、`BulkInterfaceMenu.java`、`DriveCellIndexMixin.java` |
| `src/main/resources` | `advanced_bulk_storage.mixins.json`、接口 AE2 界面定义、两种接口的方块状态、方块及物品模型、战利品表、配方与镐子采集标签 |
| `assets/advanced_bulk_storage` | `lang/en_us.json`、`lang/zh_cn.json`，四种盘和两种接口的英文及简体中文 GuideME 页面 |
| `src/gameTest/java/dev/advancedbulkstorage` | `BulkStorageTests.java`、`BulkInterfaceTests.java`、`BulkGuideClientCheck.java` |

复现时使用 Java 21 执行 `./gradlew.bat build runGameTestServer --console=plain`。使用 `./gradlew.bat runClient -PwithJei -PcheckGuide` 进入独立测试世界，可触发 GuideME 中英文编译检查。
