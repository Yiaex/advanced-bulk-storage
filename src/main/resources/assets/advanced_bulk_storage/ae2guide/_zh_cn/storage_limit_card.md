---
navigation:
  title: "限额卡"
  parent: upgrade_cards.md
  position: 11
item_ids:
  - storage_limit_card
---

# 限额卡

在大宗盘安装限额卡，打开过滤编辑器的 **限额**。例如输入 `100 each; 1000 total; 300 #c:ingots; 64 minecraft:iron_ingot`。压缩池按基础单位计量。默认拒绝超额，降低限额会保留已有库存。

需要按规则主动销毁时，在条件后追加 `!void`，例如 `100 minecraft:iron_ingot !void`，并再次保存同一规则确认。直连大宗接口先尝试所有绑定盘，再处理超额余量。拒绝规则、类型上限、供电、权限和白名单仍然生效。普通 ME 路由、自动归类和迁移只拒绝超额。可在管理器和监视器查看累计销毁数量。服务器可通过 `upgrades.allowOverflowDiscard` 全局禁用。

盘已启用销毁时，修改白名单仍需二次确认。累计销毁量达到 LONG 上限后，接口拒绝继续销毁。

<RecipeFor id="advanced_bulk_storage:storage_limit_card" />
