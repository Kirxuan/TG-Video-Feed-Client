# 优化阶段 13E：随机模式消息引用解析、刷新去重、短期限回退与一次透明恢复

日期：2026-08-09（2026-08-10 完成续验）

## 1. 阶段合同

### Outcome

1. 当前项与唯一下一项对同一 `VideoKey` 的并发刷新共享一次官方 `getMessage`。
2. RANDOM + 原画的可见刷新等待与其他质量统一为 3 秒软期限，不再暴露 Repository 的 15 秒硬上限。
3. 软期限或普通网络失败时先使用 Room 索引中的引用；真实旧 `fileId` 失败时，在同一 Loading 状态内自动刷新、按当前质量重选并重绑一次。
4. 刷新后仍失败、消息已删除或不再是普通 `messageVideo` 时才进入明确错误页；透明恢复不循环，显式“重试”仍保留。

### Scope

- `TelegramMessageRepository` 的应用自有消息引用解析结果。
- `TdLibTelegramMessageRepository` 的 `VideoKey` single-flight、结果分类及 Room 写回。
- `VideoPlaybackViewModel` 的统一软期限、generation 核对和一次透明恢复。
- player 的 FILE_UNAVAILABLE 恢复反馈边界、转场指标与 benchmark 解析。
- 对应 Repository、ViewModel、player、PowerShell parser 测试及本文、README、验收矩阵。

### Boundary

- 只解析当前项和唯一下一项；不批量刷新 RANDOM 队列，不扩大预加载。
- 不修改 Pager 动画、随机洗牌、13D 启动区间参数、Media3 buffer 或 TDLib range 调度。
- `alternativeVideos` 与完整消息不写入 Room；不持久化 `PlaybackPlan`。
- 不新增轮询、后台任务、权限、存储位置、协议实现或第二个 Repository。
- 不覆盖用户质量，不吞掉 `CancellationException`，不捕获 `Error`，不主动重试 FLOOD_WAIT。

### Failure states

- 成功：Room 只写原始普通视频元数据/标签；安全服务端质量候选只随本次内存结果返回。
- 3 秒软期限或普通异常：当前准备回退索引引用；透明恢复则结束 Loading 并显示既有 FILE_UNAVAILABLE。
- 删除/unsupported：Repository 更新 Room，当前可见项显示“视频已不可播放”。
- stale `fileId`：同一 `VideoKey` 自动刷新一次；新引用按当前质量重新选择。
- 刷新后仍为同一 `fileId` 或新绑定再次 FILE_UNAVAILABLE：显示错误，不再自动刷新。
- 页面、账号、队列、质量或随机轮次 generation 变化：取消等待；迟到结果不得绑定到新目标。
- FLOOD_WAIT：返回分类结果并安全回退，不在本路径自动重试。

### Proof

按阶段合同执行 `:telegram:testDebugUnitTest`、`:app:testDebugUnitTest`、`:player:testDebugUnitTest`、fresh `test/lint/assembleDebug`、Compose Path B、真机 install+launch smoke、RANDOM normal/fast 安全样本与敏感日志扫描。自然冷引用只在不清 TDLib 数据库、不清缓存、不退出账号的前提下记录；没有自然样本时写“尚未验证”。

## 2. 13D 前置复核

- 13D 生产值保持 `STARTUP_RANGE_CANDIDATE=BASELINE`：唯一下一项头部 256 KiB、tail 0、额外 speculative 字节 0。
- 13D 记录的 fresh `test` 为 345/345 tasks、lint 为 220/220 tasks；assembleDebug、两组 Robolectric、API 36 AOSP x86_64 emulator 40/40 与当时 Android 13 ARM64 真机 install+launch smoke 通过，Vivo/iQOO Android 16 尚未验证。
- 进入 13E 前代码追踪确认：Repository `getMessage` 硬超时为 15,000ms；非原画准备外层为 3,000ms；RANDOM + 原画逐项调用 `getMessage` 但没有外层软期限；target 准备与 settled 绑定可能分别请求同一 key；旧 FILE_UNAVAILABLE 直接完成失败转场，只能由用户手动重试。

## 3. 应用自有解析结果

领域接口不暴露 TDLib 类型，`refreshVideo` 返回：

- `Resolved(IndexedVideo)`：新原始引用及仅内存安全候选；
- `MessageMissing`：官方消息不存在，Room 行标记删除；
- `UnsupportedMessage`：消息不再是普通 `messageVideo`，Room 行标记不支持；
- `Unavailable(failure)`：Network、FLOOD_WAIT、Timeout、SessionUnavailable、AccessLost、脱敏 code 或 Unknown。

这使 ViewModel 能区分“可安全用旧索引继续”和“索引本身已终止”，且 TDLib callback、对象和原始错误文本仍停留在 telegram 数据边界内。

## 4. `VideoKey` single-flight

`TdLibTelegramMessageRepository` 在自身受控 coroutine scope 内保存有界的活动请求表，键为 `chatId + messageId`：

- 首个等待者创建 lazy `Deferred`，并发等待者复用它，因此一次活动窗口只有一次官方 `getMessage`。
- 每个调用者仍可取消自己的等待；最后一个等待者离开时取消未完成请求。一个 3 秒 speculative caller 不能取消仍由当前可见项等待的共享请求。
- 请求完成且最后等待者离开后立即移除，不形成历史缓存。
- 账号退出时取消并清空全部活动 refresh；不保留跨账号结果。

Repository 仍保留 15 秒硬上限作为基础设施防线；可见路径统一由 ViewModel 的 3 秒软期限约束。

## 5. PlaybackPlan 与软期限

所有需要解析的当前/唯一下一项都经同一 `resolveVideoReference`：

- `withTimeoutOrNull(3_000)` 到期返回索引引用，不等待 15 秒；RANDOM + ORIGINAL 也使用同一规则。
- 普通 `Exception` 映射为瞬时不可用并回退；`CancellationException` 继续抛出；`Error` 不被转换。
- target prepare 与 settled promotion 继续共享既有当前/下一项 `PlaybackPlan`；同时 Repository single-flight 兜住交错调用。
- 解析成功后才按当前用户质量选择 `playbackFileId`；质量、网络、账号、队列或随机 generation 变化使旧 plan/结果失效。
- Repository 在返回临时候选前对 Room 的同 key 元数据写回只清理已完成 plan，不取消正在产生该写回的合法解析；删除/unsupported 导致当前 key 从列表移除时，允许该次终态结果完成并显示明确错误，但成功引用仍必须通过完整 generation/key 校验才能绑定。

本阶段没有增加建议的 16 项历史计划缓存。既有原子 `currentPlan`/`nextPlan` 已覆盖当前项和唯一下一项，并已有质量、网络、账号、队列与随机轮次失效规则；额外缓存会重复所有权并扩大 stale 引用面。

`LATEST + ORIGINAL` 的既有跳过刷新路径保持不变，不产生额外 `getMessage`。

## 6. 一次透明恢复

player 将 `FILE_UNAVAILABLE` 先交给 ViewModel，而不是立即结束该转场：

1. 当前 `VideoKey`、settled page、质量/账号/队列/random token 均匹配时，把 UI 保持为同一项 Loading 并停止下一项预加载。
2. 以同一 3 秒软期限调用 single-flight refresh。
3. 成功时按恢复开始时的当前质量选择最终 `fileId`；只有 `fileId` 改变才重绑。
4. 页面或任一 generation 已改变时不绑定；合法 Repository 写回仍只包含原始元数据。
5. 同一恢复键带有内存 `attempted` 标记。刷新后同一 `fileId`、刷新失败，或重绑后再次 FILE_UNAVAILABLE 都结束原转场并显示既有错误，不再次自动刷新。
6. 消息删除/unsupported 显示明确 MESSAGE_UNAVAILABLE；显式用户 retry 入口不删除。

FIRST_FRAME 会清除恢复 attempt；离页、质量/账号/队列变化、退出播放页和 release 会取消恢复 job 与旧绑定资格。

## 7. 指标与 benchmark

同一脱敏转场增加：

- `transparentRecoveryAttempts`；
- `transparentRecoveryOutcome=REBOUND|SOFT_TIMEOUT|UNAVAILABLE|MESSAGE_UNAVAILABLE|STALE_REFERENCE|REFRESHED_FILE_UNAVAILABLE`。

benchmark parser/runner 汇总尝试次数和各 outcome 计数。字段只含计数、枚举与既有 `chatId/messageId`，不增加完整错误、路径、正文、账号、remote id、凭证或媒体字节。

## 8. 自动化覆盖

- Repository：同 key 并发只发一次 `getMessage`；成功写回；删除/unsupported 安全写回；FLOOD_WAIT 单请求无风暴；最后 waiter 取消继续传播。
- ViewModel：RANDOM + ORIGINAL 3 秒回退；普通 Exception 回退、Error 不吞；target 与 settled 共享请求；自动恢复最多一次；恢复后按当前质量重选；同 `fileId` 不重绑；软超时不迟到绑定；快速离页不绑定；同 key Room 写回不取消合法解析；删除/unsupported 即使同步移出列表也显示明确错误；FLOOD_WAIT 回退；LATEST + ORIGINAL 无额外刷新；既有 plan generation 失效规则继续通过。
- player/metrics：恢复事件保留在同一个转场样本内，最终 FIRST_FRAME 或 FAILED 统一完成。
- benchmark：新字段解析、汇总与敏感文本拒绝规则通过 PowerShell 脚本测试。

## 9. Proof 与真机结果

- 模块单测：`:telegram:testDebugUnitTest`、`:app:testDebugUnitTest`、`:player:testDebugUnitTest` 全部 PASS。
- 2026-08-10 续验使用 `--rerun-tasks` fresh 重跑 `TdLibTelegramMessageRepositoryTest` 14/14、`VideoPlaybackViewModelTest` 57/57、`PlaybackTransitionMetricsTest` 14/14、`PlaybackCallbackGateTest` 2/2、`VideoAudioPolicyTest` 3/3，共 90/90 PASS；single-flight、3 秒回退、取消/Error 边界、一次恢复、质量重选、删除/unsupported、FLOOD_WAIT、迟到回调和单播放器门槛均被重新执行。
- fresh 主机 Proof：`test --rerun-tasks` PASS（345/345 tasks），`lint --rerun-tasks` PASS（220/220 tasks），`assembleDebug` PASS。只有既有 Android SDK XML 版本与 deprecated API 警告。
- Compose Path B：`compileInstrumentationKotlin` PASS；两组指定 Robolectric-Compose 命令 PASS；API 36 AOSP x86_64 emulator PASS，`OK (41 tests)`。
- 当前 Android 13/API 33 ARM64 真机的 install+launch smoke PASS，目标包为 top/resumed 且 crash buffer 无目标包 crash。该设备不是 iQOO 12 / OriginOS 6 / Android 16，Vivo 专项尚未验证。
- benchmark parser/runner 自检 PASS。最初三份 setup/无首帧失败报告以及续验早期四份媒体长尾失败报告均原样保留，不改写为通过。有效播放页的迟到终局显示消息 refresh 只有 5–44ms，而 bind→first-byte/READY 可达 12.5–34.5 秒；首个失败根因是当时的媒体首字节/READY 长尾，不是 Repository 15 秒刷新阻塞。对应续验失败报告为 `random-swipe-first-frame-normal-forward-20260810-004907.md`、`random-swipe-first-frame-normal-forward-20260810-005051.md`、`random-swipe-first-frame-fast-reverse-20260810-005224.md`、`random-swipe-first-frame-fast-reverse-20260810-005347.md`。
- 网络窗口恢复后，完整 RANDOM Normal Forward 报告 `random-swipe-first-frame-normal-forward-20260810-010312.md` 严格 PASS：FIRST_FRAME 12/12、RANDOM/FORWARD/必需字段/启动观测全部确认，refresh SUCCESS 12/12、promoted 12/12、message refresh P50/P90/max 22/46/49ms，bind→first-frame 910/3,368/3,834ms，gesture→first-frame 1,531/3,992/4,458ms，rebuffer/crash 0/0。
- 随后的连续 RANDOM Fast Reverse 报告 `random-swipe-first-frame-fast-reverse-20260810-010411.md` 严格 PASS：单批 10 个手势产生 FIRST_FRAME 1、SUPERSEDED 18、FAILED/UNSUPPORTED/UNCHANGED 0，最终 refresh 13ms、bind→first-frame 1,394ms、gesture→first-frame 1,919ms，promoted 1/1、rebuffer/crash 0/0。快速滑动没有终态刷新风暴；同 `VideoKey` 官方调用恰为一次的断言由 fresh Repository 并发测试直接验证。
- 两份完整通过报告前另保留已准备/窄窗口的 Normal 1/1 与 Fast 1/1 PASS 报告；它们只用于定位，不替代最终 12/10 门槛。续验 16 份 report/log 中共有 51 个脱敏转场摘要，FAILED/UNSUPPORTED、UNKNOWN 顺序/方向、非 1 player instance 均为 0；目标包 crash buffer 为 0。
- 未删除 TDLib 数据库、未清应用缓存、未退出账号、未修改网络/VPN/质量。没有遇到可合法确认的自然旧 `fileId`，自然 stale `fileId` 的真机透明恢复仍为“尚未验证”；确定性自动化已覆盖成功重绑、同 fileId、二次失败、删除/unsupported、软超时和离页取消。

## 10. 安全与最终生产状态

- 不修改 Pager 动画、预加载预算、13D 候选或 player buffer。
- 不新增权限、公共存储、Room schema、DataStore 字段、TDLib native、Bot API 或手写 MTProto。
- 不记录完整错误、路径、正文、账号、凭证、验证码、密码、会话或完整 TDLib 对象。
- 最终 Manifest 仍仅实际申请 INTERNET 与 ACCESS_NETWORK_STATE；WAKE_LOCK 和动态 receiver 权限条目是显式 `tools:node="remove"`。`local.properties` 继续被 git ignore。
- 原 9 份最终 emulator/真机 device、instrumentation、target-logcat、crash/start/prep 日志的敏感字段计数保持为 0；2026-08-10 续验新增 16 份 benchmark report/log 再扫描 TELEGRAM_API_ID/HASH、api_hash、phone_number、authenticationCode、database_encryption_key、password、messageText、remoteId、localPath、未脱敏 VideoKey、绝对/远程路径，全部为 0。
- 13D 最终生产参数复核为 `STARTUP_RANGE_CANDIDATE=BASELINE`、head 256 KiB、tail 0、额外 speculative 0；owner promotion 与 contained active-request 生产开关仍为 false。
- 阶段完成后停止在 13E，不进入 13F。
