# Changelog

## 1.0.0

- 发布首个 1.0 正式版。汇总 0.6.1-dev 至 0.6.8-dev 的大宗接口性能优化、配方与指南调整，以及库存界面和空盘锻造自动合成修复。[1.0.0 累积更新日志](CHANGELOG-1.0.0.md)

## 0.6.8-dev

- 加宽迁移器 A、B 槽之间的间距，并将每个标签居中放在对应槽位上方。

## 0.6.7-dev

- 修正大宗驱动器、接口、迁移器和过滤编辑器中背包第三排与快捷栏重叠的槽位坐标。
- AE2 锻造样板会把仅带盘 UUID 的空白大宗盘视为可批量制作的空白材料。旧样板同样按空白类型解析，带库存或其他配置的大宗盘按原数据精确匹配。

## 0.6.6-dev

- 缩短大宗接口的悬停等待和首份库存摘要回传间隔。后台迁移因 MSPT 暂停时，悬停摘要仍能在独立的小预算内继续处理。刷新同一张盘时保留上一份摘要。
- 加高大宗接口选择列表的行距，为坐标、已用类型及容量预留行内空间。

## 0.6.5-dev

- 提高四级大宗存储元件的造价。标签盘配方加入 64M 存储组件，后续锻造分别消耗 256M、64M 加 256M、两枚 256M 存储组件。
- 中英文指南按存储元件、接口、迁移器、升级卡和辅助设备分章，保留各物品的指南入口。

## 0.6.4-dev

- 物品能力写入时只创建一次物品键。按绑定顺序验证挂载盘，写满当前批次后停止查盘。
- 仅在 ME 网格实例改变时重新查询大宗盘索引服务，分裂或合并后按当前网格定位绑定盘。
- 持续入库期间仅在确有已发布预览时使其失效，库存和预览修订仍按每次实际写入更新。

## 0.6.3-dev

- 大宗接口复用在线绑定盘的解析结果。驱动器可用状态、挂载关系和接口绑定变化时重新解析。
- 逐槽读取仅查询可能持有该物品的已绑定盘，按物品堆叠上限停止计数。
- 新增回归覆盖同 Tick 在 16 张绑定盘之间移动库存，以及 ME 网格分裂后重新合并。

## 0.6.2-dev

- 使用用户提供的 v3 贴图，覆盖 12 张物品和 16 张方块贴图。
- 修复持续抽取使自动整理反复重扫、靠后来源盘得不到处理的问题。
- 复用自动整理的工作缓冲与驱动器排序结果，减少普通盘扫描和大宗接口逐槽读库存的临时分配。

## 0.6.1-dev

- 切换迁移范围、方向和端点时暂停，暂存盘提交前复核插槽身份。
- 压缩迁移完成两端库存更新后才通知观察者，新增压缩库存会唤醒相应压缩形式的归类。
- 批量配置会跳过换盘或排队后配置已变化的目标。
- 修改启用销毁的盘的白名单时需要重新确认，销毁计数达到 LONG 上限后拒绝继续销毁。

## 0.6.0-dev

- 三级迁移器加入同存档区块的挂载盘传输、来源与目标选择、分批扫描和进度读数。
- 自动归类以区块为单位共享任务，支持在线驱动器之间迁移和压缩池守恒。
- 管理器增加设备详情、配置编辑和按页批量应用。监视器增加单盘、类型占用率、速度、限额与销毁统计。
- 限额卡增加逐规则、二次确认的溢出销毁。工作台可复制和导入完整单盘配置。

## 0.5.1-dev

- 按斜置薄盘的比例重画四种大宗盘，使用银色壳体和四种内嵌芯片图案。
- 普通与高级接口采用亮银框、内凹六面面板和不同端口布局。物品图标使用清晰的彩色面板。

## 0.5.0-dev

- 重做两个大宗接口的选盘列表，增加图标、搜索、滚动、状态及按需真实库存预览。
- 接入全服预算并共享版本化预览，兼容 LONG 库存与存档既有库存组件。
- 向接口物品处理器提供同一压缩池的不同可访问形式，按共享基础单位出库。
- 重绘 21 件核心物品及机器的原生像素资产，更新中英文接口指南。

## 0.4.0-dev

- Added a shared MSPT-adaptive scheduler and three bulk migrators with adjustable power, whitelist and redstone settings.
- Added two-slot transactions for bulk, native AE2 and MEGA item cells. Direct bulk movements update both balances in one server-thread operation. Compatible native endpoints are staged on detached cell copies.
- Added verified reversible compression pools, storage limit cards, energy saving cards, and four event-driven auto-sorting cards.
- Added a 32-slot bulk drive, advanced cell workbench, paged storage manager, and a redstone monitor driven by AE2 storage updates.
- Opened the shared filter editor from the advanced interface, workbench, migrator, and monitor. Added the new blocks, cards, recipes, skins, and localized GuideME pages.
- Continued regression coverage for old saves, ItemStack synchronization, dense drives, shared compression pools and INT-scale migration.

## 0.3.0-dev

- Added a cached icon-and-quantity tooltip to all four bulk storage cell tiers, with a larger Shift preview and a reconfiguration hint for persisted all-item rules.
- Reused the existing lossless ItemStack payload for client-only preview data. Tooltip rendering makes no inventory requests.
- Added basic and advanced ME Bulk Storage Interfaces, recipes, six-sided item access, a batch capability, and GuideME pages. The advanced interface supports prioritized bindings, secondary filtering, side modes and a redstone gate.
- Added a shared AE2 drive mount index with UUID conflict protection. Interface access suspends when the drive or channel becomes unavailable.
- Added tooltip bounds and exact quantity tests, and interface tests for shared inventories, per-key integer limits and UUID conflicts.

## 0.2.0-dev

- Limited AE2 IO port extraction to the energy-budgeted insertion amount in both directions and preserved stock during bulk-cell compensation.
- Shared live state between storage handles for one ItemStack and saturated positive AE2 inventory counts at the LONG limit.
- Added lossless per-cell wire compression, preserving item components, old saves and creative slot packet round trips.
- Enforced whitelist-only admission with bounded globs, positive filter branches and explicit equipment rules. Existing unsupported filter expressions and stock are preserved.
- Added an adaptive AE2-style editor, optional JEI ghost ingredients, categorized registered tags and a paged single-cell stock view.
- Updated the English and Simplified Chinese filter guide and added regression coverage for shared handles, IO port conservation, aggregate count overflow and multi-cell packet size.

## 0.1.1-dev

- Fixed disconnects while synchronizing filled bulk cells through AE2 Drive menus, dropped item entities, and Jade inventory tooltips.
- Made inventory components round-trip losslessly through creative slot packets. Existing inventory format 1, item keys and LONG quantities carry over directly.
- Added a targeted diagnostic when a cell's persisted inventory is quarantined and preserved for recovery.
- Added network, drop and pickup, AE2 menu, Jade and legacy-save regression tests, including two ten-slot drive snapshots with 4,096 entries per cell.

## 0.1.0-dev

- Added four multi-type bulk storage cells for AE2 drives and terminals with exact data-component identity and LONG quantities.
- Added a shared filtering engine and cell editor with namespaced rules, tag and item search, personal rules, preset composition, and on-demand preview.
- Added the Extended Tag Card, data pack presets, server configuration, two languages, and GuideME pages in the AE2 guide.
- Added conservative MEGA Cells conversion and stock-preserving upgrades between the new cell tiers.
- Added dedicated server GameTests for storage boundaries, data persistence, AE2 drive behavior, rule permissions, recipe loading, and bulk-operation conservation.
