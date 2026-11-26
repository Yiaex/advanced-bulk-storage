# 贴图 / 材质规格 v3（AE2 对齐评审稿）

本文档描述 `art-review/v3/textures/` 里的 12 件物品 + 16 张方块贴图的画法：
调色板、像素布局、每张贴图的记号含义、生成与安装方式。

v3 的画法**直接从原版贴图反推**：用 `art-review/ref/` 里解出来的 AE2
`item_storage_cell_*` / `basic_card` / `io_port_*` / `cell_workbench_*` 作为造型与材质基准，
元件外壳再套 MEGA Cells `bulk_item_cell` 的色壳，最后换成本模组自己的等级色。

> 现状：v3 只渲染到 `art-review/v3/`，**没有覆盖** `src/main/resources` 里的现有素材。
> 满意后执行 `python tools/draw_assets_v3.py --install` 即可整体替换这 28 个 PNG。
> 模型 JSON / blockstate 不动（贴图文件名与现有资源完全一致）。

---

## 1. 交付物

| 路径 | 内容 |
| --- | --- |
| `art-review/v3/textures/item/*.png` | 12 张物品贴图（4 元件 + 8 升级卡） |
| `art-review/v3/textures/block/*.png` | 16 张方块贴图 |
| `art-review/sheets/compare-v3-item.png` / `compare-v3-block.png` | 现状 \| AE2 原版参考 \| v3，6× |
| `art-review/sheets/overview-v3.png` | 全套 6× 带中文名的总览图 |
| `art-review/sheets/v3-scale-item.png` / `v3-scale-block.png` | 1× / 2× / 3× 实机尺寸检查 |
| `art-review/sheets/v3-cells.png` / `v3-item.png` / `v3-block.png` | 14× / 14× / 10× 细节图 |
| `art-review/ref/ae2/textures/{item,block}` | 从 AE2 jar 解出的参考贴图（不随模组发布） |
| `art-review/ref/megacells/megacells/textures/{item,block}` | 从 MEGA Cells 4.11.0 jar 解出的参考贴图（不随模组发布） |
| `art-review/sheets/v3-shell-options.png` | 三套壳色方案（AE2 银 / MEGA bulk / MEGA 近黑）对比 |
| `art-review/abs-textures-v3.zip` | 交接包：28 张 PNG + 生成器 + 校验脚本 + 本规格（给 Codex 直接安装用） |
| `tools/draw_assets_v3.py` | 本套贴图的生成脚本（唯一真源） |
| `tools/v3_sheets.py` | 对比图 / 总览图工具 |

---

## 2. 统一规格

- 画布 16×16 RGBA。物品轮廓外 **alpha=0**，方块满幅不透明。
- 只允许纯色像素块，**不做抗锯齿、渐变、半透明叠加**；每条边都是 1px 硬边。
- 光源固定在左上：受光面用亮色，背光面用暗色，底面比顶面更暗。
- 一套贴图共用同一批颜色，任何一张都不引入新色号。
- **对比度铁律**：凹面用"近黑 + 1px 亮唇"，凸面用"顶/左近白 + 底/右压暗"；
  记号只用两个亮色档，不用中间调画结构。

### 调色板（机壳灰，AE2 灰机族）

| 名称 | HEX | 用途 |
| --- | --- | --- |
| W | `#f2f2f2` | 机壳最顶 1px 亮边 |
| L | `#e8e8ea` | 受光面、卡片高光 |
| M | `#dedfe3` | 机壳主面 |
| B | `#d5d6dc` | 亮边下方的凹槽 |
| S | `#cbccd4` | 下半板接缝 |
| M2 | `#b3b6c6` | 下半板、面板底面 |
| M3 | `#a8acbe` | 次级金属、离线舌片 |
| D | `#9a9fb4` | 背光面、面板下唇 |
| D2 | `#878fa5` | 右下阴影 |
| D3 | `#787e97` | 最底 1px 暗边 |
| INSET | `#696d88` | 深凹边 |
| INSET2 | `#4d4d67` | 凹槽内描边、通风孔 |
| INSET3 | `#413f54` | 最深缝 |
| NAVY | `#262b54` | 插槽底、深色开窗、屏幕 |
| NAVY_DEEP | `#141223` | 插槽最深内阴影 |

### 调色板（升级卡专用灰）

| 名称 | HEX | 用途 |
| --- | --- | --- |
| CARD_FACE | `#a5a7ac` | 卡身正面 |
| CARD_HI | `#dedfe2` | 卡身高光 |
| CARD_MID | `#c1c3c7` | 卡身中调 |
| CARD_SH / SH2 | `#797e84` / `#61666d` | 卡身阴影 / 插脚 |
| CARD_OUT_HI / LO | `#4e535a` / `#353940` | 卡身描边 |

### 调色板（强调色）

| 名称 | HEX | 用途 |
| --- | --- | --- |
| GLINT | `#f7fcff` | 1px 高光、白色脉冲 |
| GOLD_HI / GOLD / GOLD_D / GOLD_DK | `#fad64a` / `#e9b115` / `#dc9613` / `#b26411` | 金端子四档 |
| BLUE_W / BLUE_HI / BLUE / BLUE_D | `#daffff` / `#9cd3ff` / `#7da9d2` / `#708cba` | 在线亮条、屏幕曲线 |
| VIOLET_HI / VIOLET / VIOLET_D / VIOLET_DK | `#c9aee8` / `#a98fd8` / `#8f6fc4` / `#5b3f8a` | 高级/量子件 |
| RED / RED_D | `#d64b3c` / `#8c2820` | 阈值灯亮 / 灭 |

### 调色板（等级色，每色四档 deep / base / light / glow）

| 等级 | deep | base | light | glow | 用于 |
| --- | --- | --- | --- | --- | --- |
| MINT | `#11727a` | `#1c919a` | `#1aa9a7` | `#20c5b5` | 标签元件、基础接口/迁移器、归类卡 0/1 |
| BLUE | `#708cba` | `#7da9d2` | `#9cd3ff` | `#daffff` | 高级标签元件、在线接口、归类卡 1 |
| AMBER | `#b26411` | `#dc9613` | `#e9b115` | `#fad64a` | 模组元件、超频件 |
| VIOLET | `#5b3f8a` | `#8f6fc4` | `#a98fd8` | `#c9aee8` | 量子元件、高级接口、量子件 |
| DESAT | `#5a5e68` | `#787d88` | `#969ba6` | `#b0b5be` | 离线态 |

---

## 3. 方块模板（16 张共用）

所有方块都是同一个机壳 + 一个内凹面板，只有面板内容不同 —— 这是 AE2 机器族的做法。

```
行 0      W        亮边（x%3==1 处用 L 打点，避免死板）
行 1      B        凹槽
行 2-11   面板      见下表；面板底 M2，上/左 INSET2 内阴影，下/右 D 亮唇
行 12     S        下半板接缝
行 13     M2       下半板 + x=4,7,10,13 的 INSET2 通风孔
行 14     D        下沿
行 15     D3       最底暗边
列 0/15   L / M2   左亮右暗
```

面板坐标 `panel(2, 2, 13, 11)`；接口、驱动器里的"槽"用 `slot()`：
`NAVY` 底 + `NAVY_DEEP` 上/左内阴影 + `INSET3` 下唇 + 内部亮条（在线时 `BLUE_HI`、离线时 `M3`）。

| 贴图 | 面板内容 |
| --- | --- |
| `backplane` | 面板 3..12 × 4..9：一条 MINT 走线（亮线 + 暗底）、两个 NAVY 焊盘、两颗金点 |
| `bulk_storage_interface` | 单槽 `slot(3,5,12,9)`，离线=灰舌片 + D2 暗带 |
| `bulk_storage_interface_online` | 同槽，改用 `BLUE_HI` 亮条 + `BLUE_W` 顶线 + 两侧蓝点（lit 状态） |
| `advanced_bulk_storage_interface` | 双竖槽 `slot(3,4,7,9)` / `slot(8,4,12,9)`，中缝 INSET3，离线灰 |
| `advanced_bulk_storage_interface_online` | 双槽改 VIOLET_HI 亮条 + 上下紫点 |
| `bulk_storage_migrator_side` | 上下两条 MINT 传送轨（`rail()` y=3 / y=10，轨上每 3px 一颗 glow）+ 中间 `arrows()` |
| `bulk_storage_migrator_top` | 抬起板 3..12 × 4..9 + 上下轨线 + `arrows()` |
| `overclocked_bulk_storage_migrator_side` | AMBER 双轨 + `processor()`（金属封装、暗 die、2×2 琥珀核、上下金散热脚） |
| `overclocked_bulk_storage_migrator_top` | 抬起板 + `processor()` |
| `quantum_bulk_storage_migrator_side` | VIOLET 双轨 + `quantum_ring()`（r=2 八边环 + GLINT 弧） |
| `quantum_bulk_storage_migrator_top` | 抬起板 + 环 + 上下紫外框线 |
| `bulk_storage_drive` | 4 条 `slot()`（y=4/6/8/10），每条金触点 + MINT 指示灯 |
| `advanced_cell_workbench` | 左：BLUE 元件插座（方框 + 亮心）；右：两条 GOLD 工具轨 + 金点 |
| `bulk_storage_manager` | 3×3 个 2×2 模块（M 面 + GLINT 顶边 + INSET 底边），底行 MINT 灯线 |
| `bulk_storage_monitor` | NAVY 屏幕 + 栅格 + BLUE_W 双行折线 + RED 阈值灯 |
| `bulk_storage_monitor_signal` | 同上，曲线更亮、灯改 RED 亮心（`powered` 状态） |

---

## 4. 物品模板 A：大宗存储元件（4 张）

**结构逐像素临摹 AE2，外壳材质取 MEGA Cells**：`item_storage_cell_1k.png` 的 16×16 像素图
直接搬进 `AE2_CELL` 令牌表（在 `tools/draw_assets_v3.py` 里），轮廓/切面明暗/描边走向一个
像素都没动；外壳的 9 个灰色调换成 MEGA Cells `bulk_item_cell.png` 同一坐标上的颜色；
内芯 3 色换成本模组等级色。

```
① 外壳（116 px）：AE2 银壳 → MEGA bulk 色壳
   AE2        MEGA bulk    角色
   #f2f2f2 → #737173      白色脉冲/镜面
   #dedfe3 → #5a575a      壳高光
   #cbccd4 → #4d494d      银壳主面
   #adb0c4 → #3b393b      中间调
   #9a9fb4 → #4c4143      壳暗面
   #878fa5 → #3c3232      暗面
   #696d88 → #31292a      深暗
   #4d4d67 → #271c1d      描边
   #413f54 → #111111      深描边

② 内芯（34 px）：换成等级色
   #2d69e1（19px 主色） → tier.base
   #3894ff（11px 亮色） → tier.light
   #40c1ff（ 4px 高光） → tier.glow
```

- 外壳映射是**逐坐标抄的**，不是凭感觉配的：在 `bulk_item_cell.png` 的同一 (x,y) 上取色即可，
  116 个壳像素一一对应（三张贴图的 alpha 掩码完全一致，都是 150 个不透明像素）。
- 三角形轮廓是 AE2 的**斜置薄壳**（不是对称菱形），左端尖、右端宽。
- 透明像素：AE2 在 alpha=0 的像素上仍留了 RGB，这边统一清成 `(0,0,0,0)`，
  渲染结果完全一致（Minecraft 不读 alpha=0 像素的颜色）。
- 四张元件除了内芯三色之外**逐像素完全相同**，与 AE2 自身 1k/4k/16k/64k 的做法一致。

三套壳色都在生成器里，用命令行参数切换（默认 `bulk`）：

```
python tools/draw_assets_v3.py            # 默认 MEGA bulk 色壳
python tools/draw_assets_v3.py ae2        # AE2 原版银壳
python tools/draw_assets_v3.py dark       # MEGA mega_item_cell_housing 近黑壳
```

| 壳色方案 | 来源贴图 | 主面色 | 观感 |
| --- | --- | --- | --- |
| `ae2` | AE2 `item_storage_cell_1k` | `#cbccd4` | 银色，最像原版 1k/4k |
| `bulk`（默认） | MEGA `bulk_item_cell` | `#4d494d` | 暖枪灰，深色壳 + 亮内芯 |
| `dark` | MEGA `mega_item_cell_housing` | `#2d2f2d` | 近黑，内芯最抢眼 |

校验（已跑过）：四张贴图 alpha 掩码 = AE2 原图；116 个壳像素 = MEGA `bulk_item_cell` 同坐标原值。

| 元件 | 内芯三色（base / light / glow） |
| --- | --- |
| `tag_bulk_storage_cell` | `#1c919a` / `#1aa9a7` / `#20c5b5` |
| `advanced_tag_bulk_storage_cell` | `#7da9d2` / `#9cd3ff` / `#daffff` |
| `mod_bulk_storage_cell` | `#dc9613` / `#e9b115` / `#fad64a` |
| `quantum_bulk_storage_cell` | `#8f6fc4` / `#a98fd8` / `#c9aee8` |

---

## 5. 物品模板 B：升级卡（8 张）

AE2 升级卡的形状是 10×16 竖卡：浅灰卡身 + 右侧金属触点 + 底部插脚。
v3 完全照此，只在卡身开一块深色窗放功能记号。

```
卡身    x2..11, y0..15：CARD_FACE 面 + CARD_HI 左/上高光 + CARD_SH 右/下暗边
描边    x=2 / x=11 / y=0 / y=15 用 CARD_OUT_HI / LO
开窗    x3..10, y3..10：NAVY 底 + NAVY_DEEP 上/左内阴影 + INSET3 下/右亮唇
触点    x=12..13, y=3..10：等级色四段（亮行 tier.metal_dk/metal，暗行 CARD_OUT_LO/SH2）
插脚    y=11..14：CARD_MID / CARD_SH2 + 每 2px 一根 CARD_SH 分隔（AE2 插脚）
```

记号（白 = GLINT，都画在开窗里）：

| 升级卡 | 记号 |
| --- | --- |
| `extended_tag_card` | 两枚标签：后tag 暗色、前tag 亮色 + 1px 打孔 |
| `multi_compression_card` | 三道由宽到窄的压板（`(5,9)` → `(6,9)` → `(7,9)`） |
| `storage_limit_card` | 顶部封顶条 + 被截短的刻度（两段变短的横条 + 侧向截断线） |
| `energy_saving_card` | 闪电多边形 |
| `auto_sorting_card` 系列 4 张 | 共用"分流"图形：主干 + 左右分叉 + 两个箭头，等级靠 1/2/3 颗灯点区分 |

---

## 6. 命名规范（沿用现有约定，未改动）

- 物品：`textures/item/<item_id>.png`，与 item id 完全同名。
- 方块：`textures/block/<block_id>_side.png` / `_top.png`；状态变体用后缀
  `_online`（接口 `lit`）、`_signal`（监视器 `powered`）。
- 模型 JSON 与 blockstate 无需改动，v3 只替换 PNG。

---

## 7. 与 `ART-DIRECTION.md` 的关系

| ART-DIRECTION 要求 | v3 落实 |
| --- | --- |
| 16×16、直接色块、1px 硬边 | 全部遵守 |
| 灰蓝金属轮廓、左上冷光、右下深蓝灰 | 改为 AE2 灰机壳语言：`W/L/M/M2/D/D2/D3` 九档 + `INSET` 三档 |
| 共享背板 / 共享光照 | `casing()` 16 张共用，`panel()` 位置统一 |
| 元件：斜置薄壳、亮银外沿、近侧端子 | 直接逐像素临摹 AE2 `item_storage_cell_1k`，只换内芯三色 |
| 四等级记号（折线/双线/分块/环） | 保留，画在白色脉冲位置上 |
| 卡片：标签 / 压缩 / 限额 / 闪电 / 分流 | 保留，改为 AE2 卡身 + 深色开窗 |
| 迁移器双向轨、超频处理器、量子环 | 保留，改为 `rail()` / `processor()` / `quantum_ring()` |
| 设备：驱动/工作台/管理器/监视器 | 保留，改为 `slot()` / 插座 / 3×3 模块 / 屏幕 |

---

## 8. 生成与安装

```bash
# 只渲染到 art-review/v3（安全，不动 src）
python tools/draw_assets_v3.py

# 生成对比图 / 总览图
python tools/v3_sheets.py

# 满意后整体安装进模组资源（28 个 PNG）
python tools/draw_assets_v3.py --install
```

---

## 9. 可继续调整的旋钮

- **元件色强度**：改 `cell_core()` 里 `x + y <= 11` 的阈值 —— 数值越小，亮面越少、内芯越沉。
- **元件壳亮度**：`cell()` 的壳循环里 `L if d >= 5 else W` 换成 `M / L` 可让外壳更灰。
- **卡身开窗大小**：`card()` 的 `c.rect(4, 3, 9, 10, NAVY)` 四个数字。
- **机壳色温**：改 `M / M2 / D` 三档即可整体偏冷或偏暖。
- **面板占比**：`panel()` 默认 2..13 × 2..11，改大改小可调"金属感 vs 屏幕感"。
