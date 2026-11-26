---
navigation:
  title: "ME 大宗存储管理器"
  parent: support_devices.md
  position: 12
item_ids:
  - bulk_storage_manager
---

# ME 大宗存储管理器

接入供电的 ME 网络，需要一个频道，耗电 4 AE/t。按名称、UUID、位置或过滤规则搜索，可按盘型和状态筛选，按位置、名称或类型占用率排序。打开记录查看盘内实际库存，点击 **设备详情** 查看升级卡、优先级、耗电、接口绑定、压缩池和销毁记录。星号表示以基础单位计量的压缩池。每次只同步当前页。

配置时选中单盘，或点击 **选择本页** 后选定白名单、拒绝限额或自动归类操作。点击 **应用** 后，服务端逐盘验证权限和升级卡，并显示成功数量。销毁规则需在单盘过滤编辑器内确认。

<RecipeFor id="advanced_bulk_storage:bulk_storage_manager" />
