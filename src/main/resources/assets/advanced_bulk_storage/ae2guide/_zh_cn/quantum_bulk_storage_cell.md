---
navigation:
  title: "ME 量子大宗存储元件"
  parent: storage_cells.md
  position: 13
item_ids:
  - quantum_bulk_storage_cell
---

# ME 量子大宗存储元件

可组合已注册的标签、模组命名空间和精确物品 ID。默认支持 64 个标签。`#c:ingots/*` 等限定范围的通配符按展开后的标签计数。

默认类型上限为 **4096**，升级槽 **6**，驱动器待机耗电 **64 AE/t**。每个完整物品键可存储超过有符号 long 范围的数量。AE2 对每个物品键最多显示 9,223,372,036,854,775,807 件。手持元件右键配置，然后装入供电且有频道的 AE2 驱动器。在 AE2 元件工作台设置的精确分区会叠加到过滤规则。

鼠标悬停在元件上可查看真实库存图标及数量。按住 Shift 展开。默认预览上限分别为 9 种和 36 种，实际行数受界面空间和 GUI 缩放影响。K、M、G、T、P、E 分别表示 1000 的各次幂。手持元件右键进入“库存”，悬停在条目上可查看精确数量。

<RecipeFor id="advanced_bulk_storage:quantum_bulk_storage_cell" />

[统一过滤编辑器](filter_editor.md)
