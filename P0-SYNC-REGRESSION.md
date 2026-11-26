# 0.1.1-dev 非空大宗盘网络同步修复

## 根因与修复

`BulkStorageMod.CONTENTS` 在 0.1.0-dev 将 `advanced_bulk_storage:contents` 注册为 `StreamCodec.unit(CustomData.EMPTY)`。Minecraft 1.21.1 的 `ItemStack$1.encode` 将组件差异交给 `DataComponentPatch$1.encodeComponent`。后者把非空的 `CustomData` 实例交给 `StreamCodec$3.encode`，其固定值校验要求实际值与空 NBT 相等，导致 `Can't encode '{items:[...],version:1}', expected '{}'`。旧版在回归测试的 `ClientboundContainerSetContentPacket` 上复现了异常。

修复使用 Minecraft 的 CompoundTag 流编码器，保留现有 NBT 内容，并为单个库存组件设置 8 MiB NBT 解码计量上限。使用普通 `CustomData.STREAM_CODEC` 的第一轮验证中，含名称和耐久组件的 4,096 键盘已经超过默认 2 MiB 的 NBT 解码计量上限。上限仅用于为网络 NBT 解码限制内存分配。类型上限、单键 LONG 数量上限和磁盘持久化行为沿用原设定。

AE2 19.2.20 的普通存储元件将 `AEComponents.STORAGE_CELL_INV` 里的 `GenericStack` 列表经流编码器完整同步。MEGA Cells 4.11.0 单类型大宗盘以完整键、单位数量和压缩参数分别编码。Jade 15.10.6 的存储预览使用 `ViewGroup.listCodec(ItemStack.OPTIONAL_STREAM_CODEC)`，会调用相同的 ItemStack 组件编码链。

`filter` 与 `cell_id` 已使用可往返的专用网络编码器，保持原有实现。当前版本已注册的待查数据组件共三种。

## 存档与数据权威

磁盘继续使用同一个组件 ID、`CustomData.CODEC` 和 `version:1`。原物品键、LONG 数量、自定义名称、UUID、过滤及升级卡随 ItemStack 读取，无迁移步骤。存档组件版本或库存记录无效时，库存对象暂停读写，原始载荷保留，服务端告警含盘等级、UUID 和原因。

容器点击的客户端预测栈用于比较同步状态。真实物品移动仍由服务端容器执行。原版创造模式可把回传的栈写入服务端槽位，因此 ItemStack 走完整可逆同步。创造权限保留原版复制行为。客户端和服务端都需更新 JAR，因为旧版和新版使用不同的网络组件格式。

## 性能范围

库存编码直接读取 `CustomData` 的 NBT，编码过程中不改写它，也不为发送额外深拷贝。数量仍是单个 LONG 字段。大盘数据的写回沿用 AE2 的存盘机制。完整库存只随需要同步的实际 ItemStack 发送，打开有大盘的驱动器、掉落物跟踪及 Jade 库存预览会包含对应实际库存。接收端按组件解码，创建新的 `CustomData`。

此补丁使用完整编解码，专用界面的摘要、分页和增量同步仍待实现。网络开销取决于键数和组件数据大小。原版整包大小、压缩状态及第三方载荷大小约束仍适用。含大量长文本或大自定义组件的极端装包不在已验证范围内。

2026-11-25 的增强组合加载测试中，两名测试玩家同时持有各自的驱动器菜单。每台驱动器的 10 张量子盘各含 4,096 个带自定义名称及耐久组件的键。每台驱动器的初始化数据包约 5.81 MB，经过 Minecraft 默认 256 字节压缩阈值和长度封帧后约 424–428 KiB。此计量覆盖该测试的同步载荷，未测量实际整服 MSPT 或客户端帧率。

## 实际回归结果

原专用 GameTest 环境与附加 JEI、spark、FerriteCore 的增强环境各通过 15 项 GameTest，其中 6 项为本补丁新增。

| 场景 | 验证内容 |
| --- | --- |
| 四种空盘和非空盘编码 | 原版 ItemStack、完整容器内容、携带物与创造模式回传。非空盘含两种组件变体，数量分别为 2,147,483,648 和 9,223,372,036,854,775,807。 |
| 原版 ME 驱动器菜单 | 两个服务端测试玩家查看同一驱动器，覆盖 Shift 存入、Shift 取出、背包移动和缺少库存组件的容器点击预测。实际服务端盘保持完整。 |
| 拆驱动器、掉落物同步及拾取 | 四种非空盘各掉落一张。经实体数据包解码、实体和驱动器 NBT 保存回读及拾取，逐盘比较完整 ItemStack。 |
| Jade 15.10.6 | 在真实 ME 驱动器上使用 Jade 的 Item Collector 和库存预览编码器。四种盘的完整组件都通过往返校验。 |
| 两台十槽满载驱动器 | 两个菜单同时保持打开。使用 Minecraft 压缩、帧编码及解码，两台驱动器共 20 张量子盘一致。 |
| 旧版磁盘数据 | 直接加载在 0.1.0-dev 复现异常时保存的真实物品 NBT。经网络往返后再次保存，NBT 与原始夹具相等。 |

另在隔离的真实 Minecraft 客户端中打开包含旧盘的 AE2 驱动器，查看了 Jade 预览，取出并放回盘，再拆除驱动器。关闭并重启客户端及集成服务器后，旧盘仍以掉落物存在。用存档 NBT 条件验证掉落物和拾取后的背包盘，库存键和两项 LONG 数量一致。还在离玩家较远的独立区块装入另一张盘，验证区块实际卸载，然后重新加载并核对盘数据。

多人验证使用同一专用测试服务器内的测试玩家和真实容器处理器、数据包编解码器。两台实际远程客户端的联机端到端、专用服务器重启后的玩家登录仍待验证。本 Mod 专用大宗驱动器、管理器、接口及高速迁移器在该开发线尚未实现，缺少可测试入口。

## 修改文件

| 文件 | 修改 |
| --- | --- |
| `build.gradle` | 更新 Mod 版本，加入开发运行和回归测试使用的 Jade 依赖。 |
| `src/main/java/dev/advancedbulkstorage/BulkStorageMod.java` | 修复 `CONTENTS` 网络编解码注册。 |
| `src/main/java/dev/advancedbulkstorage/storage/BulkCellInventory.java` | 保留损坏数据隔离，并记录具体原因。 |
| `src/gameTest/java/dev/advancedbulkstorage/BulkCellNetworkTests.java` | 新增真实非空库存的六项回归测试。 |
| `src/gameTest/resources/advanced_bulk_storage/legacy/p0-0.1.0-dev-cell.snbt` | 留存旧版生成的真实非空盘 NBT。 |
| `CHANGELOG.md`、`README.md`、`P0-SYNC-REGRESSION.md` | 记录修复、升级方式和验证范围。 |

## 复现命令

```powershell
./gradlew.bat build runGameTestServer --console=plain
```

依赖组合为 Minecraft 1.21.1、NeoForge 21.1.265、AE2 19.2.20、MEGA Cells 4.11.0、GuideME 21.1.22 和仅在开发运行中默认加载的 Jade 15.10.6。
