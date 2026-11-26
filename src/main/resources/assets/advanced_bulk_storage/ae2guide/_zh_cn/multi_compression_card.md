---
navigation:
  title: "多类型压缩卡"
  parent: upgrade_cards.md
  position: 10
item_ids:
  - multi_compression_card
---

# 多类型压缩卡

在 AE2 元件工作台为任意本模组大宗盘安装一张。使用铁、金、钻石或铜等经过验证的可逆配方，每条独立压缩链共用一份池库存。入库物品本身仍需符合该盘白名单。

例如，9 个铁粒等价于 1 个铁锭，81 个铁粒等价于 1 个铁块。提取一种形式即扣减同池最小单位。库存和 Tooltip 以最小单位展示。支持批量 API 的大宗接口可按注册物品键访问共享形式。池的容量按最小单位计，最多为 LONG 上限。

旧版独立库存保留。移除卡或配方改变后，可按旧池最小单位提取剩余物品。为保护数量，只有当前配方与存档记录完全一致时允许换算压缩形式。

<RecipeFor id="advanced_bulk_storage:multi_compression_card" />

