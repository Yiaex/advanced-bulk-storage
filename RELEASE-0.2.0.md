# Advanced Bulk Storage 0.2.0-dev 开发验收

适用于 Minecraft 1.21.1、Java 21、NeoForge 21.1.265、AE2 19.2.20、MEGA Cells 4.11.0 与 GuideME 21.1.22。JEI 19.50.0.414 与 Jade 15.10.6 用于对应兼容性验证。

## P0 根因和修复

| 已复现问题 | 源码与原因 | 结果 |
| --- | --- | --- |
| 打开驱动器、接收掉落物、Jade 预览时断线 | 0.1.0-dev 的 `BulkStorageMod.CONTENTS` 注册了 `StreamCodec.unit(CustomData.EMPTY)`。Minecraft 1.21.1 的 `ItemStack` 编码通过 `DataComponentPatch.encodeComponent` 写入组件，固定值编码器只接受空值。 | 本版的 `CellContentsStream` 能往返编解码真实非空的 `version:1` 库存。 |
| 多个满盘造成容器同步包过大 | 0.1.1-dev 在 ItemStack 网络载荷中写入每张盘完整的原始 NBT，多个大盘在同一容器包累加超过原版解压后大小上限。 | `CellContentsStream.CODEC` 在组件内部压缩 NBT，16 满盘组合已通过原版整包帧的编解码。 |
| IO 端口转移 100 件只留下 12 件 | AE2 `IOPortBlockEntity.transferContents` 遇到能量限制时会向来源写回余量，普通插入会再次执行大宗盘当前收货过滤。 | `IoPortRemainderMixin` 在正反两向提取时以 AE2 的带电模拟限制数量。直接来源为大宗盘且仍有补偿时，通过 `BulkCellInventory.restoreIoPortRemainder` 还原。两个方向均验证目标 12、来源 88。 |
| 同一盘的两个句柄各提取 10 件 | `BulkCellInventory` 先前按句柄各自反序列化了 10 件库存。 | 同一实际 ItemStack 的句柄共享工作库存，第二个句柄实际提取 0。 |
| 网络汇总 Long 最大值加 1 变负 | AE2 `VariantCounter.add` 对普通盘和大宗盘的同键值执行相加。 | `NetworkCountMixin` 对正数加法进行饱和，显示上限为 `Long.MAX_VALUE`，两张盘保存的真实数量分开保留。 |

## 存档及同步

- 原有注册 ID、`contents` 持久化 codec 和 `version:1` 数据格式继续使用。完整 AEItemKey、物品组件、LONG 数量、UUID、精确分区、自定义名称与升级卡沿用原数据。旧版真实非空盘夹具已按原 NBT 加载并比对。无效库存保留原始载荷，日志报告盘等级、UUID 和问题位置。
- 旧黑名单和无条件全收规则保留原文，暂停入库，已有库存继续可读可取。重新保存有效白名单即可恢复。可用的有限通配符按实际注册项计算，每条可成立的分支都必须具备正向约束。宽泛条件排除装备，精确装备规则服从盘权限和服务端配置。
- ItemStack 网络同步采用可逆的压缩库存组件。原版容器移动按服务端库存执行，创造模式可按原版权限复制完整栈并回传。配置、背包、驱动器、掉落和 Jade 的网络组件遵守原版整包限制，客户端与服务端都应使用同版 JAR。
- 单盘库存页按服务端库存快照查询，使用轻量数量摘要和六行页面。变更时刷新当前页。查看页的同步粒度目前为整页，ItemStack 与外部原生容器仍可能携带完整压缩库存。多客户端共用可复用详情视图、摘要专用 ItemStack 同步、行级增量和高熵极端包体仍待验证或实现。

## 回归

`build runGameTestServer` 在目标专用环境通过 20 项。覆盖四种盘的空盘与非空盘编码、原版驱动器双测试玩家、Shift 存取与背包操作、掉落物保存回读与拾取、旧数据、Jade 实际库存采集、创造回传、LONG 临界值、4096 个完整键、双驱动器容器包、真实 IO 端口正反两向能量限制下的资源守恒、句柄共享与过滤限制。

隔离游戏客户端直接打开旧存档中的非空标签盘并对真实库存排序。悬停显示完整的 9,223,372,036,854,775,807 个铁锭。JEI 拖入橡木原木可看到 `minecraft:oak_log`、主要标签 `#minecraft:logs` 和细分标签 `#minecraft:oak_logs`。GuideME 21.1.22 在真实客户端编译了当前全部 14 份中英文页面，检查通过。中文过滤编辑器及量子盘页面已在游戏界面打开。生产 JAR 不包含开发测试类。

双远程客户端联机、专用服务器重启后的实际登录、增强依赖组合压力、异常退出、长时性能、兼容其他核心修改 AE2 的 Mod 和全部未实现设备的回归尚无本版实测。上次独立手动验证见 [P0-SYNC-REGRESSION.md](P0-SYNC-REGRESSION.md)。压力夹具、原始包体测量和局限见 [PERFORMANCE.md](PERFORMANCE.md)。

## 21 种核心物品及设备

| 注册 ID | 当前状态 |
| --- | --- |
| `tag_bulk_storage_cell` | 已注册，原版驱动器、旧盘存量、白名单和真实库存可用 |
| `advanced_tag_bulk_storage_cell` | 已注册，可用 |
| `mod_bulk_storage_cell` | 已注册，默认显示命名空间模式 |
| `quantum_bulk_storage_cell` | 已注册，验证 4096 键和 LONG 边界 |
| `extended_tag_card` | 已注册，权限切换和移除后保留存量已测试 |
| `bulk_storage_interface` | 待实现 |
| `advanced_bulk_storage_interface` | 待实现 |
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

当前可玩的核心项目为 5 个。库存页采用只读列表和滚轮翻页，尚待终端式网格、更多入口及升级卡管理。接口、迁移器、自动归类全局队列、事务与 MSPT 预算、附属设备列入后续开发。NBT 专用盘仍处于推迟阶段。

## 修改文件

| 目录 | 文件 |
| --- | --- |
| 项目根目录 | `build.gradle`、`README.md`、`CHANGELOG.md`、`PERFORMANCE.md`、`RELEASE-0.2.0.md` |
| `src/main/java/dev/advancedbulkstorage` | `BulkConfig.java`、`BulkStorageMod.java` |
| `src/main/java/dev/advancedbulkstorage/client` | `BulkStorageJei.java`、`FilterEditorScreen.java` |
| `src/main/java/dev/advancedbulkstorage/filter` | `EquipmentPolicy.java`、`FilterCapabilities.java`、`FilterEngine.java`、`FilterParser.java`、`FilterPresets.java` |
| `src/main/java/dev/advancedbulkstorage/menu` | `FilterEditorMenu.java` |
| `src/main/java/dev/advancedbulkstorage/mixin` | `IoPortRemainderMixin.java`、`NetworkCountMixin.java` |
| `src/main/java/dev/advancedbulkstorage/storage` | `BulkCellInventory.java`、`BulkCellItem.java`、`CellContentsStream.java` |
| `src/main/resources` | `advanced_bulk_storage.mixins.json`、`META-INF/neoforge.mods.toml`、`assets/ae2/screens/advanced_bulk_storage/filter_editor.json` |
| `src/main/resources/assets/advanced_bulk_storage` | `lang/en_us.json`、`lang/zh_cn.json`，`ae2guide/` 和 `ae2guide/_zh_cn/` 下各自的 `filter_editor.md`、`mod_bulk_storage_cell.md`、`quantum_bulk_storage_cell.md`、`tag_bulk_storage_cell.md` |
| `src/main/resources/data/advanced_bulk_storage/tags/item` | `equipment.json`、`equipment_exceptions.json` |
| `src/gameTest/java/dev/advancedbulkstorage` | `BulkStorageTests.java`、`BulkCellSafetyTests.java`、`BulkCellNetworkTests.java`、`BulkGuideClientCheck.java` |

## 复现

使用 Java 21 启动 Gradle。重新打开已有存档前，在客户端和服务端同时安装同一份 JAR。

```powershell
./gradlew.bat build runGameTestServer --console=plain
./gradlew.bat runClient -PwithJei -PcheckGuide --console=plain
```

第二条命令进入任意隔离世界后自动加载英文和简体中文指南。检查 `run-client/logs/latest.log` 中的 `ABS guide check`。实机测试 JEI 时按住左键超过 JEI 配置的悬停延迟，然后拖入加号槽。默认延迟 150 毫秒。本次自动化鼠标动作采用 0 毫秒的隔离实例设置，实际检测仍由 JEI 完成。
