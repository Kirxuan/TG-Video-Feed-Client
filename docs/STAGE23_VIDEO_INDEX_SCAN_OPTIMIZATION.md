# Stage 23：频道视频索引扫描极致优化

## 1. 阶段合同

### Outcome

历史悠久且视频稀疏的频道以 TDLib 普通视频过滤搜索建立完整索引；客户端请求、对象映射和 Room 热路径成本主要随普通 `messageVideo` 数量增长。用户可以在完整回填期间继续浏览已经落库的索引，并看到真实的视频结果页、候选数、唯一索引数和频道状态。

### Scope

- `telegram/client`：应用自有过滤搜索页面模型，以及精确 `SearchChatMessages` 请求/映射。
- `telegram/message`：近期优先、公平轮转、默认并发 2、逐频道串行、共享 FLOOD_WAIT 闸门、游标停滞保护。
- `core:database`：Room v5、4→5 显式迁移、独立策略/游标/统计、每页批量事务。
- `core:model`、`feature:channels`：真实统计语义和分页停滞错误。
- 单元、Room migration/DAO、Compose 和确定性性能模型测试。
- README、架构、验收、开发计划与 Stage 23 审计/结果文档。

### Boundary

不修改授权、播放器、Media3、缓存、品牌、主题、导航、权限、备份、TDLib 版本或依赖；不下载视频字节；不索引动画、视频留言、Stories、直播、secret video 或文档伪装视频；不进入 Stage 24。

### Failure states

加载、空频道/空视频、扫描中、用户暂停、普通网络失败、15 秒请求超时、FLOOD_WAIT 倒计时、访问失效、数据库错误、过滤搜索分页停滞。所有错误保留已提交索引；只有退出账号按既有合同清理账号数据。

### Proof

先跑 client、repository、确定性性能模型、ViewModel、Room 编译/迁移和 Compose 定向测试；再运行 `test --rerun-tasks`、`lint --rerun-tasks`、`assembleDebug --rerun-tasks`。Compose 发生变更，因此按 Path B 运行 instrumentation 编译、Robolectric-Compose 和明确指定的 API 36 AOSP x86_64 emulator UI；本阶段明确禁止任何实体机命令。

## 2. 生产请求边界

初始/恢复索引生产路径只调用：

```text
SearchChatMessages(
  chatId = selectedChannelId,
  topicId = null,
  query = "",
  senderId = null,
  fromMessageId = persistedVideoSearchCursorOrZero,
  offset = 0,
  limit = 100,
  filter = SearchMessagesFilterVideo()
)
```

`TelegramClientVideoSearchPage` 是 telegram 模块内部的应用自有模型，只暴露映射后的消息、可空的近似总数和 `nextFromMessageId`。`TdApi.*` 不越过 telegram 边界。`TdLibMessageObjectMapper` 仍只把普通、非 secret 的 `MessageVideo` 映射成视频；即使服务端返回意外对象，repository 也只会 `mapNotNull` 普通视频元数据。

生产历史索引接口中已移除 `getChatHistory`，静态搜索可证明扫描链路不再遍历普通消息。扫描阶段没有 `DownloadFile`。

## 3. 分页、事务与恢复

- 完成条件只有 TDLib 返回 `nextFromMessageId == 0`；不能用页面长度或近似总数判断。
- 短页且 next 非零：提交并继续。
- 空页且 next 推进：提交游标并继续。
- 非空最终页且 next 为零：先在同一事务写入该页，再标记完成。
- 非零游标必须严格向更老的 message id 推进。相同或反向游标把该页数据和 `PAGINATION_STALLED` 状态一起提交，但保留上一个有效游标，停止无限循环；用户可在服务端状态恢复后手动继续。
- 请求结果到达前取消不会进入 Room；事务完成后取消/进程退出，重启从已提交 next cursor 继续。每页是独立事务，不持有超长频道的大事务。
- `lastNewMessageId` 继续作为近期增量对账位置；它不与过滤历史游标混用。
- recent 对账从 0 向旧翻页时，只有 `requestCursor == persisted videoSearchCursor` 才在同一页面事务推进历史游标。首次/迁移安全重扫可连续消除已提交 recent 页的后续重放；已经持有不同历史恢复位置的频道仍保持原游标，不能被猜测性覆盖。

## 4. 非破坏迁移

Room 从 v4 升到 v5，新增：

- `scan_strategy_version = 2`
- `video_search_cursor`
- `video_search_completed`
- `video_candidate_count`
- `video_search_page_count`
- `approximate_video_count`

旧 `oldest_scanned_message_id`、`scanned_message_count`、`scanned_page_count` 留在 schema 中作为历史数据，不再驱动新扫描或 UI，也绝不重新解释成过滤游标。

- 旧版完整频道：保留全部视频/标签、旧游标和完成事实，`video_search_completed=true`。
- 旧版未完整频道：保留全部视频/标签，`video_search_cursor=0`，以策略 2 从安全起点重扫；复合主键与批量 upsert 吸收重复。
- 新策略统计从 0 开始，避免把旧“普通消息数”伪装成“视频候选数”。
- 应用显式注册 `MIGRATION_4_5`，没有 `fallbackToDestructiveMigration`。

## 5. 有界并发和限流

- 同一频道每次只有一个请求，严格按持久 cursor 串行。
- 每轮每个频道最多处理一页，再进入下一轮；轮次起点旋转，避免超大频道饿死其他频道。
- 第一次轮转只做每个选中频道的近期页，所有频道获得近期结果后才公平回填完整历史。
- 工作队列只创建最多 2 个 worker；普通网络重试占用自己的 worker，另一个 worker可继续健康频道，不按频道数创建无界 async。
- 任一频道收到 FLOOD_WAIT 后把服务器截止时间写入共享扫描闸门和 Room。所有尚未发出的频道请求都等待同一截止时间；已在错误返回前发出的最多另一个并发请求无法撤回，但不会继续形成请求风暴。
- 普通网络失败、超时和拒绝按频道最多 3 次，指数退避、可取消；不会无故设置全局网络闸门。
- 取消选择、退到后台、用户暂停、失去访问权限和退出账号沿既有 lifecycle/generation 边界取消协调器。

## 6. Room 批量热路径

每页事务执行：

1. 内存中按 `(chatId, messageId)` 去重。
2. 用复合主键索引一次查询页面已存在 message id。
3. 一次批量 upsert `VideoEntity`。
4. 一次批量插入页面去重后的 `TagEntity`。
5. 一次批量删除这些视频的旧 cross-ref。
6. 一次批量插入新 cross-ref。
7. 一次更新 `ChannelEntity` 的游标、状态和统计。

不再为每个视频调用 `getVideo` 或单条 replace。全局孤儿标签清理只在扫描完成/分页停滞、编辑、删除和增量边界执行，不再在每个普通历史页执行。`EXPLAIN QUERY PLAN` instrumentation 断言页面存在性查询使用 `(chat_id,message_id)` 复合主键自动索引，因此没有新增冗余索引或反规范化计数。

## 7. UI 语义

- 紧凑摘要保持详情/暂停操作在同一动作行，左侧用两行完整显示 `处理视频 N 个` 与 `已索引 M 个`，大计数不再挤掉第二个指标。
- 展开详情以两行四格分别显示“已处理视频、搜索页数、唯一索引、完整频道”，不再把所有数字压成一条实现术语长句。
- 只有所有选中频道都返回近似总数时才合计显示 `Telegram 估计约 N 个视频，仅供参考`；该数字不形成百分比或进度条。
- 每个频道行用两行显示“状态 · 已处理 N 个视频”和“搜索 P 页 · 已索引 M 个”，避免窄屏省略后半段。
- `approximateTotalCount` 从不控制完成。
- 分页停滞显示“已保留索引，可手动继续重试”。有任何唯一索引时仍沿既有入口允许浏览，不等待老历史完全回填。

API 36 AOSP x86_64 的视觉反馈循环覆盖 360dp 折叠/展开和 320dp + 1.35 字号。首轮截图发现折叠态长计数会截断唯一索引，随后改为双行；第二轮发现大字号会截断末尾单位，再压缩为“处理视频 N 个”，最终截图保留全部关键数字。

## 8. 安全与数据边界

- 复合唯一键仍是 `chatId + messageId`。
- Room 只保存视频/标签/扫描元数据，不保存媒体字节或临时 HLS/alternative 描述符。
- 没有新增依赖、权限、公共存储、备份范围或日志敏感字段。
- 扫描不写标题/正文到日志，不记录完整 TDLib 对象、文件路径、凭证、手机号、验证码、密码或会话。
- 真实账号、真实超长频道、iQOO 12 和所有实体机性能/正确性结果：尚未验证。

## 9. 证据索引

- GitHub/源码比较：`STAGE23_GITHUB_REUSE_AUDIT.md`
- 确定性模型和验证层级：`STAGE23_PERFORMANCE_RESULTS.md`
- 长期架构：`ARCHITECTURE.md`
- 回归矩阵：`ACCEPTANCE_TESTS.md`
