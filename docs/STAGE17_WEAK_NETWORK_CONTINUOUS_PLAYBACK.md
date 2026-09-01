# Stage 17：弱网短视频连续播放优化

## 1. 阶段合同

### Outcome

在既有单播放器、TDLib 私有缓存和有界 range 下载模型内，减少下一条首帧等待、弱网重缓冲及已下载区间的重复请求；AUTO 使用 TDLib 真实网络下载阶段的保守吞吐估计为下一条选择可持续 H.264 服务端质量。

### Scope

- `TelegramFileClient`/`TelegramClientManager`：封装官方任意 offset downloaded-prefix 查询。
- `TelegramFileManager`：prefix single-flight、lease-local 命中、取消/超时/陈旧路径保护，并从 active range 的 `updateFile` 进度采样。
- 领域层：内存态稳健吞吐估计器和 AUTO 码率选择。
- `VideoPlaybackViewModel`：在下一条 PlaybackPlan 和 256 KiB 预加载之前应用估计；当前项不重绑。
- `VideoPlayerManager`：把已识别的真实 rebuffer 反馈给估计器。
- JVM 单测与 test-only 固定种子弱网模拟；README、复用审计和性能结果文档。

### Boundary

- 不修改 Media3 1.10.1、TDLib 1.8.66 或任何依赖、Gradle 配置、Manifest、权限、Room/DataStore schema。
- 继续只有一个 application-scoped ExoPlayer 和一个 PlayerView。
- 当前 read-ahead 仍不超过 4 MiB；唯一下一条仍不超过 256 KiB；移动数据默认不预加载。
- 不引入 `SimpleCache`、第二媒体缓存、第二播放器、完整下载、公共存储、代理、转码、HLS 或生产弱网模拟。
- `DATA_SAVER`、`HD_720`、`ORIGINAL` 完全绕过吞吐选择；`supportsStreaming=false` 行为不变。
- 不恢复 64/128 KiB、head512、tail、play-before-prepare、1 秒启动缓冲、back buffer、360 ms snap 或 owner promotion 等历史回归候选。

### Failure states

- prefix 为 0/不足、失败或 10 ms 超时：回退原有有界 `downloadFile(offset, limit)`。
- 本地路径不存在/不可读：仅调用一次官方 `getFile` 刷新路径并重查 prefix；仍失败则回退有界下载。
- owner 取消、最后一个同 offset owner 释放、账号退出：取消对应等待；晚到 query/generation 结果不能发布。
- 样本不足、零时长、重复/倒退进度、过小样本、异常速率、缓存命中、磁盘读取或已取消 request：不形成吞吐样本。
- 网络 generation、网络类型、TDLib Ready session 或账号退出改变：清空估计，AUTO 回到原网络类型冷启动规则。
- rebuffer 或连续两个大幅下降样本：下一条快速降档；升级需要连续可靠估计且有 25% 滞回。

### Proof

1. 领域 selector/estimator、TDLib client/file manager、DataSource/preload/player metrics、ViewModel 定向测试。
2. test-only seed `170017`、每档 30 次转场的 NORMAL/0.5/1/2 Mbps 相同 trace A/B。
3. 全量 `test`、`lint`、`assembleDebug`、Robolectric/Compose 和 `:app:compileInstrumentationKotlin`。
4. 仅在存在 API 36 AOSP x86_64 emulator 时运行 emulator Compose smoke；本阶段禁止任何实体机操作。

## 2. 已实现设计

### 2.1 任意偏移 prefix 命中

读取仍先信任当前 `TelegramFileSnapshot`。当该窗口不覆盖 owner、但 TDLib 已报告存在本地下载字节时，`TelegramFileManager` 对同一 `(fileId, offset)` 只启动一个 `getFileDownloadedPrefixSize` 协程。命中结果只写入对应 owner，并由 `awaitAvailable()` 返回 `downloadOffset=owner.offset` 的 lease-local snapshot；它不会写入 `observeFile/currentSnapshot`，因此旧查询不能移动全局窗口或污染其他 seek。

10 ms query 上限由未修改代码 NORMAL 的 prepare→READY p95 238.5 ms 和“不超过 5% 回归”门槛反推：即使查询走满上限再回退，额外 10 ms 仍小于 11.9 ms 的 5% 预算。该 API 只检查 TDLib 本地下载区间；若本地串行查询未在预算内返回，超时不是扩大 range 或延长播放等待的理由，直接回退原路径。

路径在 IO dispatcher 检查 `isFile && canRead`。陈旧路径只刷新一次；prefix query 不改变 owner 的 `length/readAheadBytes`。range 使用半开区间判断，端点相接不再被误合并，避免把 256 KiB NEXT 请求扩大几个当前项字节。

### 2.2 TDLib 网络阶段吞吐估计

`StreamingNetworkMetricsEstimator` 只驻留内存，最多保存 9 个样本。`TelegramFileManager` 仅在 `entry.activeRequest === request`、request 已开始且未取消时，从后续 `updateFile` 对该 active range 的连续正增量采样；`downloadFile` 即时响应、prefix/local hit 和 `RandomAccessFile` 读取均不采样。

- 单样本至少 32 KiB；排除零/负时长、倒退/重复进度与 16 Kbps 以下或 500 Mbps 以上异常值。
- 至少 3 个可靠样本后，按字节加权中位数计算，单样本权重上限 512 KiB。
- 可用带宽为中位数的 0.7。
- 候选低于当前估计 15% 时可降级；连续两个样本低于当前估计 70% 时提前快速降级。
- 升级候选需高于当前估计 25%，并连续出现 3 个可靠估计；真实 rebuffer 立即把下一条可用估计乘 0.6。
- 网络/session reset 增加 context revision；跨 revision 的迟到 progress 被忽略。

### 2.3 AUTO 下一条质量

`VideoQualitySelector` 对可用的直接 H.264 alternatives 按 `fileSize * 8 / durationSeconds` 做饱和码率估算，排除未知码率、重复 fileId 和非 H.264 项。在可靠估计下选择安全带宽内码率最高的较低成本候选；若最低档也超出安全带宽则选择最低码率候选。未知大小/时长回退既有 Data Saver 规则；无可靠估计时仍为 Wi-Fi→720p、其他→Data Saver。

估计被纳入 `PlaybackPlanToken`，但只在 AUTO 下有值。estimate 改变会取消旧的“下一条”计划并在当前项已有首帧、页面仍前台稳定时重建，不调用当前 `bind`。质量选择先于 `VideoPreloadController.setNextVideo`；滑到下一条后同一个计划提供完全相同的 fileId。

## 3. 不变量与测试覆盖

- prefix：充分/不足、相同 offset single-flight、取消一个/最后一个 owner、超时与晚到、陈旧路径一次刷新、任意 seek、logout、NEXT 预算。
- 吞吐：最小样本、0.7、加权中位数、异常/旧 context 排除、快降慢升、rebuffer、网络和 session reset。
- 质量：冷启动回退、慢 Wi-Fi、显式偏好、未知/重复/non-H.264、溢出。
- 计划：estimate 不重绑当前，选择发生在预加载前，预加载 fileId 与最终 bind 完全一致。
- test-only 模拟覆盖冷缓存、shifted partial、下一条 prefix、连续 30 次、快速反向取消、0/5 MiB/1 MiB seek、离线恢复和 logout 清理。

## 4. 候选结论

- A：**保留**。官方 prefix API 消除 shifted-window 的可避免下载，真实 manager 测试证明同范围不再调用 `downloadFile`。
- B：**保留**。只采 TDLib active range 网络进度；相同 trace A/B 在弱网取得明确改善，显式质量不受影响。
- C `DefaultPreloadManager`：**拒绝**，未进入生产代码。现有 `TelegramMediaDataSource.Factory` 没有 Media3 source-preload owner 语义，SOURCE_PREPARED/TRACKS_SELECTED 会走 CURRENT 的 4 MiB read-ahead，无法证明 NEXT 总字节不超过 256 KiB；`specifiedRangeCached` 又要求被禁止的 Media3 Cache。为满足硬边界，未留下构建开关或半成品。

## 5. 尚未验证

- 真实 Telegram 账号、真实 TDLib CDN、真实弱 Wi-Fi/移动网络和真实视频组合：尚未验证。
- 主机全量 `test`：977/977、0 failure/error/skipped；`lint` 与 `assembleDebug` 通过。
- `:app:compileInstrumentationKotlin`、规定的两组 Robolectric Compose 测试通过。
- API 36 AOSP x86_64 `emulator-5580` Compose/UI：95/95 通过；instrumentation target APK 无 `.so`，目标包 logcat 无 crash。没有在 emulator 运行 TDLib native smoke。
- 实体机：尚未验证（用户明确禁止实体机测试）。
