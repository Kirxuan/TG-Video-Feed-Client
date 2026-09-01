# Stage 23 GitHub 饱和复用审计

## 审计范围与停止条件

- 审计日期：2026-08-29。
- 生产基线：TDLib 1.8.66，固定提交 `022d60202e446ad1287b9fb68e687c8a0760788b`。
- 方法：先读固定提交的协议与实现源码，再读官方客户端/第三方客户端的实际调用，最后审计 issue 和替代路线。没有用 star 数量作为证据。
- 饱和条件：在已覆盖“频道内过滤搜索、全局搜索、计数、日历/稀疏位置、历史暴力并行、直接读 TDLib SQLite”后，连续两轮定向检索都没有产生新的实现类别或关键风险，停止扩展。

定向检索最后两轮使用了这些精确词组：

1. `GetChatMessageCount SearchMessagesFilterVideo history scan TDLib`、`GetChatMessageCalendar SearchMessagesFilterVideo TDLib`、`TDLib SQLite direct read MessageDb`、`TDLib getChatHistory parallel FLOOD_WAIT`。
2. `tdlib issue searchChatMessages next_from_message_id pagination`、`Telegram-X GetChatMessageCalendar media filter video`、`Unigram GetChatSparseMessagePositions video`、`SearchMessagesChatTypeFilterChannel SearchMessagesFilterVideo Kotlin Java`。

两轮只重复确认了已有类别：近似计数、媒体日历/稀疏位置、过滤搜索和并行历史扫描的限流风险，没有出现能替代 `SearchChatMessages` 且同时满足完整性、逐频道范围、官方边界和可恢复游标的新路线。

## 官方 TDLib 固定提交

仓库：[tdlib/td](https://github.com/tdlib/td/tree/022d60202e446ad1287b9fb68e687c8a0760788b)，提交 `022d60202e446ad1287b9fb68e687c8a0760788b`，许可证 Boost Software License 1.0。

实际读取：

- [`td/generate/scheme/td_api.tl`](https://github.com/tdlib/td/blob/022d60202e446ad1287b9fb68e687c8a0760788b/td/generate/scheme/td_api.tl)：`searchChatMessages` 明确允许返回少于 `limit`；`foundChatMessages.total_count` 是近似值；`next_from_message_id == 0` 才表示没有更多结果；`searchMessagesFilterVideo` 与 `searchMessagesFilterVideoNote` 是不同过滤器；`getChatMessageCount` 也是近似值。
- [`td/telegram/MessagesManager.cpp`](https://github.com/tdlib/td/blob/022d60202e446ad1287b9fb68e687c8a0760788b/td/telegram/MessagesManager.cpp)：空 query、非空 filter、无 sender/topic/tag 且启用消息数据库时会优先使用 TDLib MessageDb；本地结果不足时由 TDLib 继续服务器搜索。结果对象携带实现计算的下一游标。
- [`td/telegram/MessageDb.cpp`](https://github.com/tdlib/td/blob/022d60202e446ad1287b9fb68e687c8a0760788b/td/telegram/MessageDb.cpp)：非空媒体 filter 走 TDLib 自己维护的消息过滤索引，而不是让应用扫描并映射所有消息。
- [`td/telegram/cli.cpp`](https://github.com/tdlib/td/blob/022d60202e446ad1287b9fb68e687c8a0760788b/td/telegram/cli.cpp)：读取了 `SearchChatMessages` 命令和历史循环示例。旧示例使用结果末条 id 继续；本阶段采用同一固定提交协议中更明确的 `FoundChatMessages.nextFromMessageId`，不复制该示例循环。

解决的问题：把客户端接收、映射和 Room 落库成本从全部普通消息缩小到普通 `messageVideo` 候选。分页完成条件严格是 `nextFromMessageId == 0`，近似总数只作观测。

理论请求复杂度：`O(ceil(videoMessages / 100))`；零视频仍需一次确认请求。TDLib 在消息数据库命中不足时自行补充网络结果，应用不得把本地数据库命中误当完整历史。

风险：服务器可以返回短页或空页但继续给出下一游标；游标停滞必须由应用检测；服务器限流期限必须跨频道共享。结论：**Adopt**。

## 官方/成熟客户端实际用法

### Telegram-X

- 仓库：[TGX-Android/Telegram-X](https://github.com/TGX-Android/Telegram-X/tree/bf788f72047a12c65a92e1826575a7f5038e89b8)
- 提交：`bf788f72047a12c65a92e1826575a7f5038e89b8`
- 许可证：GPL-3.0
- 实际读取：
  - [`MessagesLoader.java`](https://github.com/TGX-Android/Telegram-X/blob/bf788f72047a12c65a92e1826575a7f5038e89b8/app/src/main/java/org/thunderdog/challegram/component/chat/MessagesLoader.java)
  - [`MediaViewController.java`](https://github.com/TGX-Android/Telegram-X/blob/bf788f72047a12c65a92e1826575a7f5038e89b8/app/src/main/java/org/thunderdog/challegram/ui/MediaViewController.java)
  - [`Tdlib.java`](https://github.com/TGX-Android/Telegram-X/blob/bf788f72047a12c65a92e1826575a7f5038e89b8/app/src/main/java/org/thunderdog/challegram/telegram/Tdlib.java)
  - [`SharedBaseController.java`](https://github.com/TGX-Android/Telegram-X/blob/bf788f72047a12c65a92e1826575a7f5038e89b8/app/src/main/java/org/thunderdog/challegram/ui/SharedBaseController.java)

过滤/查询存在时使用 `SearchChatMessages`，普通历史才使用 `GetChatHistory`；媒体分页保存 `nextFromMessageId`。其媒体视图还处理“本页没新增 UI 项但游标推进”的情形，继续从下一游标取数。结论：**Partial reuse（语义证据）**。没有复制代码，也没有引入 GPL 依赖。

### Unigram

- 仓库：[UnigramDev/Unigram](https://github.com/UnigramDev/Unigram/tree/058d4aad4bafb89f2c480647c2b8de6c806651ec)
- 提交：`058d4aad4bafb89f2c480647c2b8de6c806651ec`
- 许可证：GPL-3.0
- 实际读取：
  - [`MediaCollection.cs`](https://github.com/UnigramDev/Unigram/blob/058d4aad4bafb89f2c480647c2b8de6c806651ec/Telegram/Collections/MediaCollection.cs)
  - [`MediaDataSource.cs`](https://github.com/UnigramDev/Unigram/blob/058d4aad4bafb89f2c480647c2b8de6c806651ec/Telegram/Collections/Experimental/MediaDataSource.cs)
  - [`PlaybackSource.cs`](https://github.com/UnigramDev/Unigram/blob/058d4aad4bafb89f2c480647c2b8de6c806651ec/Telegram/Services/PlaybackSource.cs)

频道媒体集合使用 `SearchChatMessages` 和返回的下一 message id；`GetChatMessageCount` 仅用于媒体位置/总量 UI。结论：**Partial reuse（语义证据）**。没有复制代码，也没有引入 GPL 依赖。

### Kotlin/Java Android 项目检索

对 GitHub 中 `new TdApi.SearchChatMessages`、`SearchMessagesFilterVideo`、`FoundChatMessages.nextFromMessageId` 的 Kotlin/Java 精确检索，新增结果主要是生成绑定、包装器、重复 fork 或与 Telegram-X 同类的媒体列表分页；未发现不同的完整索引算法。结论：不引入第三方包装器，不复制样板。

## TDLib issue 风险证据

- [`getChatHistory` 请求 20 却返回 1 条](https://github.com/tdlib/td/issues/740)：证明不能用“短于 limit”推断历史结束。
- [`getChatHistory` 在删除/缓存状态后返回零条](https://github.com/tdlib/td/issues/1380)：空页也不能在没有协议完成游标时擅自宣告完成。
- [猜测 message id 并行抓历史触发 `FLOOD_WAIT_30`](https://github.com/tdlib/td/issues/743)：拒绝 message id 区间猜测和无界并行。
- [FLOOD_WAIT 会使后续请求延迟](https://github.com/tdlib/td/issues/1847)：采用扫描级共享请求闸门，而不是每频道独立重试风暴。
- [未加入频道的历史访问限制](https://github.com/tdlib/td/issues/1232) 与 [频道搜索要求 TDLib 已知该 chat](https://github.com/tdlib/td/issues/205)：访问失效按频道隔离并保留已有索引。
- [升级群历史边界](https://github.com/tdlib/td/issues/805)：与本产品仅索引当前可访问 `isChannel=true` 的频道范围不同；不猜 chat id，也不扩张到 basic group。
- [直接并发使用同一 TDLib 数据库会遇到文件锁](https://github.com/tdlib/td/issues/2506)：拒绝旁路读取或打开 TDLib 私有 SQLite。

## 候选路线比较

| 路线 | 分页/完成 | 理论请求复杂度 | 完整性与维护风险 | 决定 |
|---|---|---:|---|---|
| `GetChatHistory` 全量扫描后本地过滤 | 旧最老 message id；短页语义不可靠 | `O(totalMessages/100)` | 映射全部对象；稀疏视频频道成本极高 | **Reject（生产初始扫描）**；不影响其他合法历史用途 |
| 频道内 `SearchChatMessages + FilterVideo` | TDLib `nextFromMessageId`；0 完成 | `O(videoMessages/100)` | 官方过滤边界；需要停滞检测和共享 FLOOD 闸门 | **Adopt** |
| 全局 `SearchMessages` | opaque offset | 至少随账号全局视频量增长 | 不能天然限定每个已选频道；恢复/公平性更复杂 | **Reject** |
| `GetChatMessageCount` | 无消息页，仅近似计数 | `O(1)` 计数 | 不能生成索引，不能当完成证据 | **Partial reuse**：只允许“约”统计；本阶段直接复用搜索响应 totalCount |
| calendar/date 跳跃 | 日期/稀疏锚点 | 仍需二次取消息 | 日期间视频数量未知，不能证明覆盖所有视频 | **Reject** |
| `GetChatSparseMessagePositions` | 稀疏位置 | 为超速滚动而非穷举 | 返回抽样位置，不是完整消息集合 | **Reject** |
| 多频道无界并行或猜 message id 分段 | 人造区间 | 表面并行 | 明确 FLOOD_WAIT 风险；遗漏/重复难证明 | **Reject** |
| 直接读取 TDLib SQLite/MessageDb | 私有表/索引 | 可能本地快 | 私有 schema、锁、加密、缓存完整性和模块边界全部不可接受 | **Reject** |
| 多 TDLib client / Bot API / MTProto | 各自协议 | 不适用 | 违反账号、官方 TDLib 和安全合同 | **Reject** |

## 最终复用决定与许可证

只复用 TDLib 1.8.66 已经随仓库提供的官方 API 与其公开协议语义；生产实现全部为本仓库独立 Kotlin 代码。Telegram-X 和 Unigram 只作为交叉验证证据，没有复制 GPL 源码、没有引入依赖，因此没有新增许可证传播义务。

最终方案：逐频道串行使用 `SearchChatMessages(query="", topicId=null, senderId=null, offset=0, limit=100, filter=SearchMessagesFilterVideo)`；不同频道默认最多 2 个并发请求；所有页以 TDLib 返回的下一游标恢复，并把页面数据、页数、候选数、游标、完成/错误状态原子提交到 Room。
