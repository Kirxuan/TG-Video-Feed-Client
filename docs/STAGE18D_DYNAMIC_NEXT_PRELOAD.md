# Stage 18D — 动态唯一下一条与 10 MiB 上限

## Outcome

唯一下一条按“可播放秒数”逐级预热；当前播放 reservoir、吞吐稳定性和设备/网络条件决定 0/2/5/10 MiB tier，每个 512 KiB TDLib chunk 后重新评估，新增网络 payload 绝不超过 10 MiB。

## Scope

- 新增纯 Kotlin `NextPreloadBudgetController` 与脱敏预算/浪费指标。
- 扩展现有 `VideoPreloadManager`，保留唯一 target 和既有 owner token 边界。
- 渐进式 MP4 使用保守峰值码率公式。
- HLS 低优先级读取最低质量 media playlist，解析 MAP/segment BYTERANGE 并按完整 segment 边界计算秒数。
- `VideoPlayerManager` 每 250 ms 将当前 buffer、斜率、播放状态、吞吐、TTFB 和压力信号传给现有 preload manager。

## Boundary

- 本阶段不准备 extractor/track/SampleQueue；Stage 18E 单独实现。
- 不重新启用 Stage 13B owner promotion；生产值仍为 `false`。
- 不请求未被 HLS playlist 证明需要的 MP4 tail。
- 不创建第二个播放器、缓存或 HTTP proxy。

## Failure states

- STARTUP/SEEK/REBUFFER、buffer <8 s、持续下降：预算 0，取消低优先级请求。
- 8～15 s：HLS 只准备 manifest/资源描述，不下载媒体 payload。
- 移动/计量网络默认：媒体 payload 0。
- manifest/byterange 异常：安全回退渐进式预算。
- 单个完整 HLS segment 超过当前 tier：不下载破碎 segment，记录 `SEGMENT_EXCEEDS_TIER`。
- 码率不可靠：停留 256 KiB 最低预算，不跳到 10 MiB。

## GitHub 复用来源

- Media3 duration-based preload 和播放器/预加载 target bytes 分离思想。
- Telegram iOS prefix seconds + 最低质量 HLS 预热思想，仅独立实现。
- hls.js request completion deadline；Shaka 网络复位；dash.js buffer occupancy。

## 参数与公式

- Progressive：`clamp(peakBitrate × targetSeconds ÷ 8 × 1.25, 256 KiB, tier ceiling)`。
- 文件平均码率乘 1.50 保守峰值系数。
- 15～25 s：3 秒，最高 2 MiB。
- 25～35 s：5 秒，最高 5 MiB。
- ≥35 s 且非计量、fast/slow 比值 ≥0.70、斜率非负：10 秒，最高 10 MiB。
- 每个 TDLib payload chunk 最高 512 KiB。
- Feature flag：`cvfDynamicNextPreloadEnabled=true`。

## 可观测性

固定字段记录：`calculatedTargetSeconds`、`calculatedTargetBytes`、`allowedBudgetTier`、`downloadedNewNetworkBytes`、`cachedCoveredBytes`、`canceledBytes`、`skippedNextWastedBytes`、`currentBufferedSeconds`、`bufferSlope`、`predictedCompletionMillis`、`starvationDeadlineMillis`、`preloadStopReason`。不记录正文、URL、路径、token 或媒体字节。

## Proof

```powershell
$env:JAVA_HOME='E:\Android Studio\jbr'
.\gradlew.bat :core:domain:test :telegram:testDebugUnitTest :player:testDebugUnitTest --no-daemon --console=plain
```

结果：2026-08-24 `BUILD SUCCESSFUL in 20s`，84 tasks（9 executed，75 up-to-date）。覆盖：

- 0/2/5/10 MiB tier 与 10 MiB 硬上限；
- 512 KiB chunk；
- 移动/计量默认 0；
- 低水位、下降斜率、seek/rebuffer 抢占；
- target 改变取消、唯一下一条和 skipped waste；
- cached coverage 不计新增网络；
- 大文件不自动下载大前缀、高码率逐级到 10 MiB；
- HLS manifest 使用 NEXT priority、MAP/init 与完整 segment boundaries；
- 既有 owner/logout 防误删回归。

## 未验证部分

- 真实 Telegram HLS segment 尺寸分布、真实 CDN wasted-byte P50/P95：尚未验证。
- iQOO 12/真实账号/真机缓存清理并发：尚未验证。

## 回退策略

- `cvfDynamicNextPreloadEnabled=false` 恢复 Stage 17 已验证的 256 KiB planner。
- HLS 下一条 manifest 失败仅回退 progressive 预算，不影响当前视频 HLS/ABR。

## 下一阶段入口

Stage 18E 在此字节/owner/唯一 target 控制器上增加 Media3 `PreloadMediaSource` 的 manifest、track、extractor 和首批 sample 预热；它使用独立 feature flag 和 A/B 门槛。
