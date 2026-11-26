# Advanced Bulk Storage 0.4.0-dev 验收

Minecraft 1.21.1，Java 21。专用回归使用 NeoForge 21.1.265、AE2 19.2.20、MEGA Cells 4.11.0、GuideME 21.1.22、Jade 15.10.6。整合包实机使用 New Age Science and Technology 2.2.2 的 NeoForge 21.1.267、JEI 19.70.0.469 与现有超平坦世界副本。

## 调度、迁移事务及兼容性

- 所有迁移器与自动归类共用同一服务端调度器。每 Tick 默认预算为 2 ms 和 2048 个物品键。每个任务同一 Tick 运行至多一个批次。重复唤醒已合并，普通任务可与优先任务轮转，睡眠任务等待实际事件。设备离开区块时取消任务。
- MSPT 用指数滑动平均与 2 ms 下行滞回。约 25、35、45、50 ms 四档逐步将预算从 100% 降至 0%。检查位于批次和物品键之间，属于软时间限制。同步保存、容器交互和被动接口不受这条任务队列限制。
- 两端都为本 Mod 非压缩盘时，在服务端主线程就地计算容量并同时更新两份数量。原生 AE2、MEGA 或压缩池参与时，通过两张独立 ItemStack 快照操作；模拟和实际结果不一致即放弃暂存批次。完成后单个变更点将两张盘提交到同一个迁移器方块实体，由同一保存记录存盘。
- 当前支持机内实体盘对。全网库存到指定盘、跨方块及区块的事务恢复、任意第三方可变存储端仍需实现和验证。自动归类仅在同一驱动器中操作本 Mod 非压缩盘，槽序稳定区分同优先级目标，活动归类目标不会向更低优先顺序回搬。
- 数值保持原设计。普通、超频、量子迁移器基础速率分别为 65,536、1,048,576 和 16,777,216 件/t，满配分别为 1,048,576、16,777,216 和 2,147,483,647。固定、批量和 AE2 兼容能耗均可在设备配置中选择。压缩、限额及挂载状态实际约束最终转移量。

## 压缩、限额和服务端数据

- 配方索引只接受精确、同类输入、有正反配方且无副产物的安全线性换算链。配方或数据包重载时重建索引，每次存取查缓存。通用配方实现和有歧义的分支会被排除。
- 压缩池以正 LONG 最小单位持久化，并记下原配方各形式的整数比率。提取压缩形式扣减同一份池。压缩比例变化或拆卡时只开放冻结的最小单位，已有资源可继续提取。实际入库形式须匹配盘白名单。普通网络、物品栈和 Tooltip 展示物理最小单位。大宗接口的批量能力可按完整物品键访问合法的共享形式。
- 限额配置支持 `each`、`total`、精确物品、标签、命名空间与合法白名单组合。压缩池按最小单位记账。每条规则的统计是增量维护的精确整数。超出限额返回未接收量，原库存始终可提取。本版还没有玩家确认后销毁的入口，归类与迁移均不会销毁溢出。
- 节能卡默认每张乘 0.8，最低为原耗电的 10%。驱动器挂载时重算盘耗电，迁移器装卸卡时刷新节点耗电。现有 UUID、过滤、升级槽与组件 ID 保留。旧盘库存版本 1 继续读取，实际创建压缩池才使用库存版本 2。已有池的盘需继续使用支持版本 2 的 JAR。
- 高级接口与迁移器的二次规则、监视器筛选、高级工作台盘配置可进入同一过滤编辑器。已有预设、合法通配符与 JEI 幽灵物品交互继续复用。高级工作台复用 AE2 元件工作台的分区与升级槽。

## 21 种核心物品状态

下列每个 ID 均有注册、物品模型、配方、中英文翻译和 GuideME 页面。方块有实际方块实体与可用交互。表内“可用”指已实现并验证的范围，边界以相应行说明为准。

| ID | 本版已实现的功能及尚待补齐范围 |
| --- | --- |
| `tag_bulk_storage_cell` | 可用，原有容量、过滤、Tooltip 与库存页 |
| `advanced_tag_bulk_storage_cell` | 可用，原有容量、过滤、Tooltip 与库存页 |
| `mod_bulk_storage_cell` | 可用，原有容量、过滤、Tooltip 与库存页 |
| `quantum_bulk_storage_cell` | 可用，4096 键与 LONG 数据回归 |
| `extended_tag_card` | 可用，权限与旧盘行为保留 |
| `bulk_storage_interface` | 可用，绑定原版及本模组驱动器，批量压缩形式 |
| `advanced_bulk_storage_interface` | 可用，16 盘、独立面、白名单、红石、统一编辑器与共享池 |
| `bulk_storage_migrator` | 机内配对可用，跨宿主源和网级迁移待补 |
| `overclocked_bulk_storage_migrator` | 机内配对可用，跨宿主源和网级迁移待补 |
| `quantum_bulk_storage_migrator` | 实测机内 INT 批量，跨宿主源和网级迁移待补 |
| `multi_compression_card` | 可用，多池整数计量及冻结配方。GUI 以最小单位展示 |
| `storage_limit_card` | 拒绝溢出可用，主动销毁及销毁统计待补 |
| `energy_saving_card` | 大宗盘与迁移器可用 |
| `auto_sorting_card` | 同驱动器归类可用，跨驱动器、原生盘及压缩归类待补 |
| `advanced_auto_sorting_card` | 同驱动器归类可用，跨驱动器、原生盘及压缩归类待补 |
| `overclocked_auto_sorting_card` | 同驱动器归类可用，跨驱动器、原生盘及压缩归类待补 |
| `quantum_auto_sorting_card` | 同驱动器实测 INT 批量，跨驱动器、原生盘及压缩归类待补 |
| `advanced_cell_workbench` | 可改名、安装升级卡、配置白名单和限额并查看库存，沿用原工作台入口与独立盘编辑入口 |
| `bulk_storage_manager` | 同网搜索与只读详情可用，批量管理、远程配置和更多指标待补 |
| `bulk_storage_drive` | 32 槽挂载、优先级、接口绑定和真实物品保存。客户端逐盘灯效与极端负载仍待确认 |
| `bulk_storage_monitor` | 物品、标签和模组汇总及红石阈值，指定盘、迁移速率、溢出统计待补 |

## 文件清单

| 范围 | 变更 |
| --- | --- |
| 项目区 | `build.gradle`、`README.md`、`CHANGELOG.md`、`PERFORMANCE.md`、本验收文件 |
| 入口与设置 | `BulkStorageMod.java`、`BulkConfig.java` |
| 批量与库存 | `migration/BulkScheduler.java`、`storage/CellTransfer.java`、`CompressionCatalog.java`、`CompressionPool.java`、`LimitProfile.java`、`AutoSortTier.java`、`DriveAutoSort.java`、`BulkCellIndex.java`、`BulkCellInventory.java`、`BulkCellItem.java`、`BulkInventoryPreview.java`、`mixin/DriveCellIndexMixin.java` |
| 设备 | `device/BulkMigratorBlock.java`、`BulkMigratorEntity.java`、`MigratorTier.java`、`BulkDriveBlock.java`、`BulkDriveEntity.java`、`BulkWorkbenchBlock.java`、`BulkWorkbenchEntity.java`、`BulkManagerBlock.java`、`BulkManagerEntity.java`、`BulkMonitorBlock.java`、`BulkMonitorEntity.java`、现有的 `BulkInterfaceEntity.java` |
| 界面 | `menu/BulkFilterHost.java`、`BulkMigratorMenu.java`、`BulkDriveMenu.java`、`BulkManagerMenu.java`、`BulkMonitorMenu.java`、现有 `BulkInterfaceMenu.java` 与 `FilterEditorMenu.java`；相应客户端 Screen 与 UI 注册 |
| 游戏资源 | 为 14 个新增 ID 添加配方、模型、方块状态及掉落表、升级卡 PNG、AE2 屏幕样式和中英文指南 |
| 测试 | `BulkMigratorTests.java`、`BulkCompressionTests.java`、`BulkUpgradeTests.java`、`BulkAutoSortTests.java`、`BulkAuxiliaryTests.java`、扩展 `BulkCellNetworkTests.java` 与 `BulkGuideClientCheck.java` |

## 实际验证

| 场景 | 结果 |
| --- | --- |
| 专用服务器 GameTest | 35 项通过，含已有 22 项回归 |
| GuideME 21.1.22 | 客户端编译全部 46 份中英文页面，通过红色编译错误节点检查 |
| 量子迁移器 | 真实 AE2 节点、全服调度和 7 张加速卡，首 Tick 移动 2,147,483,647 件，来源余量 852,516,353 件 |
| 量子自动归类 | 首 Tick 搬移 2,147,483,647 件。相同白名单槽序稳定，任务闲置后可由新库存事件唤醒 |
| 压缩资源池 | 筛选、拆卡、比例不匹配、组件网络往返和两台接口依次取同一份资源均通过 |
| 32 槽大宗驱动器 | 索引全 32 槽，接口从最后一槽提取，重读保存保留 UUID 与优先级，拆机回收的总量守恒 |
| 满载驱动器同步 | 32 张盘各含 4096 个带独立组件的物品键，原版整包和帧编码解码通过 |
| 现有整合包测试存档 | 在阶段 JAR 中放置并打开了量子迁移器和共用过滤编辑器。最终 JAR 与交付文件的 SHA-256 一致，已再次登录同一超平坦世界副本。高速输送量取自专用服务器测试 |

## 复现

用 Java 21 执行 `./gradlew.bat build runGameTestServer --console=plain`。GuideME 检查运行 `./gradlew.bat runClient -PwithJei -PcheckGuide` 再进入隔离世界。完整压力计划的 P95/P99、长期运行、跨区块故障恢复、代理物流、高熵最大包和并发设备数量未全部实测。Shift 展开已有自动化验证，暂未取得实机截图。仅凭单机 GameTest 结果无法认证整服 MSPT 目标。
