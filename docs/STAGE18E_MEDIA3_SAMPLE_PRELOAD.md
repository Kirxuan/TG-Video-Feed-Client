# Stage 18E — Media3 SampleQueue 预热与单播放器交接

## Outcome

Stage 18D 字节目标完成后，可选的 Media3 `DefaultPreloadManager`/`PreloadMediaSource` 路径准备 source、track、extractor 和指定秒数首批 sample；settled target 将同一个预热 MediaSource 交给由同一 builder 创建的唯一 ExoPlayer。

## Scope

- 新增 `Media3SamplePreloadController`、自定义 `TargetPreloadStatusControl` 和 reservoir-aware preload load control。
- 通过 `DefaultPreloadManager.Builder.buildExoPlayer()` 创建现有共享播放器。
- 预热 DataSource 使用 `NEXT_PRELOAD` owner/priority；正式交接后同一 request session 提升为 `CURRENT_STARTUP`。
- 8～15 秒 `METADATA_ONLY` 档允许 manifest 与 track selection，但 capped gateway 的媒体 payload endpoint 为 0；低水位 `BLOCKED` 不进入 sample 预备。
- capped gateway 强制复用 18D payload endpoint 与 512 KiB chunk。
- 新增 exact-target handoff gate、A/B evaluator 和独立 feature flag。

## Boundary

- 不创建第二个 ExoPlayer、播放器池、第二套缓存、第二 Surface 或预解码播放器。
- 不启用 Stage 13B 旧 owner promotion；`PRODUCTION_OWNER_PROMOTION_ENABLED=false`。
- 未达到真机 A/B 门槛前，sample preload 默认关闭。
- HLS、ABR 和动态字节 preload flags 与 sample flag 独立。

## Failure states

- target 未 commit、已取消、generation/key/fileId 不匹配或已经消费：拒绝 handoff。
- sample DataSource 请求超出 `calculatedTargetBytes` 或 chunk >512 KiB：fail closed。
- NEXT 正式提升为 CURRENT 后，同一 request session 解除预加载 endpoint；否则长视频会在预热上限处被错误截断。
- 当前 reservoir 不再安全：自定义 load control 停止继续预热；18D 同时取消低优先级 range。
- 预热 source 缺失：现有 HLS/MP4 source factory 正常创建，不影响播放。
- HLS 预热交接后的 session/token 归当前 `VideoPlayerManager`，退出或替换时撤销。

## GitHub/正式 API

- AndroidX Media3 1.10.1 `DefaultPreloadManager`、`PreloadMediaSource`、`specifiedRangeLoaded` 和同 builder ExoPlayer 交接机制。
- 未采用默认“播放器加载时停止所有预加载”的策略；自定义 `shouldContinuePreloading` 只依据 Stage 18 reservoir/budget gate。
- 未配置 Media3 disk cache；TDLib 私有缓存仍是唯一媒体缓存。

## A/B 门槛与默认值

- `P95 improvement ≥15%`、`FIRST_FRAME=100%`、black/wrong/audio/crash 等安全失败为 0 才能默认开启。
- 确定性 evaluator 覆盖 14% 拒绝、≥15% 接受、FIRST_FRAME 缺失拒绝和任一安全失败拒绝。
- 由于当前没有可用真机/真实账号性能样本，生产默认：`cvfSampleQueuePreloadEnabled=false`。

## Proof

```powershell
$env:JAVA_HOME='E:\Android Studio\jbr'
.\gradlew.bat :player:testDebugUnitTest :app:compileDebugKotlin --no-daemon --console=plain
```

结果：2026-08-24 最终定向回归 `BUILD SUCCESSFUL in 27s`，102 tasks（17 executed，85 up-to-date）。覆盖：

- preload session NEXT→CURRENT priority/owner promotion；
- metadata-only 可选择 track 但不开放媒体 payload；低水位 blocked 不启动 sample preload；
- exact committed MediaSource 只消费一次；错误/取消 target 不交接；
- manager 同时只保留正式 current 与唯一 next；新 next 不会 reset/close 已交接 current，旧 current 仅在播放器切换后移除；
- 18D endpoint/chunk 无法绕过；
- A/B 15%/FIRST_FRAME/安全失败门槛；
- feature flag 默认关闭；
- 既有 `ReusablePlayerLifecycle` 单实例与 `StablePlayerViewBinding` Surface 生命周期回归。

## 未验证部分

- 真机 P50/P95、首帧改善、黑屏/错误视频/音频重叠：尚未验证。
- API 36 emulator 上的实际 SampleQueue handoff：尚未验证（将在 18F 检查 emulator 可用性）。
- iQOO 12、真实 Telegram/HLS：尚未验证。

## 回退策略

- `cvfSampleQueuePreloadEnabled=false` 立即只关闭 SampleQueue 层。
- `cvfDynamicNextPreloadEnabled`、`cvfHybridAbrEnabled`、`cvfTelegramHlsEnabled` 独立控制其他层。

## 下一阶段入口

Stage 18F 执行确定性网络/转场矩阵，汇总延迟、rebuffer、质量切换、abandon、缓存/网络/浪费字节，并执行完整 Gradle、lint、assemble 和可用的 Compose Path B。
