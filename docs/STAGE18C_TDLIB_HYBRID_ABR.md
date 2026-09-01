# Stage 18C — TDLib 真实网络估计与混合 ABR

## Outcome

HLS 自适应选择读取仅由活动 TDLib 网络下载产生的吞吐和 TTFB；fast/slow EWMA、缓冲斜率与请求 starvation deadline 共同驱动立即降级/放弃和保守升级，同一 ExoPlayer 在播放中切换表示层。

## Scope

- 扩展 `StreamingNetworkMetricsEstimator`：fast/slow EWMA、TTFB P50/P90、稳健裁剪、小内存窗口和网络 generation。
- `TelegramFileManager` 仅对活动 bounded request 的新进度记录样本，并暴露脱敏 active-request 进度。
- 新增纯 Kotlin `PlaybackRiskController`。
- 新增 `TdLibBandwidthMeter` 并接入现有 ExoPlayer/Media3 `AdaptiveTrackSelection`。
- 在现有进度 ticker 中以缓冲、请求 deadline 和设备压力限制 Media3 最大视频码率；不重建播放器。

## Boundary

- 不实现下一条动态预算或 SampleQueue 预热。
- 不统计已缓存 range、DataSource 本地读取、极快缓存命中或非活动请求。
- 不建立另一个网络估计器、播放器或缓存。

## Failure states

- 少于 3 个可信样本：最低安全表示层，先积累 reservoir。
- 预计完成晚于 starvation deadline：取消当前 bounded lease，并在新 track ceiling 下由 Media3 HLS loader 重试；最终 HLS 失败仍可走 Stage 18B MP4 fallback。
- fast 或 slow 无法承载当前层、缓冲斜率下降：立即降级。
- 网络 generation 改变：吞吐、TTFB、稳定窗口和切换历史复位。
- 电量、温度或存储压力：禁止升级。

## GitHub 复用来源

- Media3 `AdaptiveTrackSelection`/`BandwidthMeter`：直接使用 1.10.1 稳定 API。
- hls.js：fast/slow EWMA、独立 TTFB、starvation deadline 和紧急 abandon 思想，独立 Kotlin 实现。
- Shaka：升降级不同阈值、最短切换间隔、网络复位和缓存样本排除。
- dash.js：吞吐 + buffer occupancy + abandon 的混合决策。

## 参数

- 网络样本：至少 32 KiB 且持续至少 2 ms；吞吐范围 16 Kbps～500 Mbps。
- fast EWMA alpha 0.50；slow EWMA alpha 0.15；样本窗口 9；TTFB 窗口 20。
- 可用带宽系数 0.70；异常样本相对中位数裁剪到 0.25～4.0 倍。
- 升级：buffer ≥25 s、斜率非负、fast ≥候选峰值 1.35 倍、slow ≥1.50 倍、连续 4 个窗口、切换间隔 ≥12 s。
- starvation 安全余量 1.2 s；降级不等待 cooldown。
- Feature flag：`cvfHybridAbrEnabled=true`。

## Proof

```powershell
$env:JAVA_HOME='E:\Android Studio\jbr'
.\gradlew.bat :core:domain:test --no-daemon --console=plain
.\gradlew.bat :telegram:testDebugUnitTest :player:testDebugUnitTest --no-daemon --console=plain
```

结果：

- domain：`BUILD SUCCESSFUL in 16s`。
- telegram + player：`BUILD SUCCESSFUL in 29s`，82 tasks（24 executed，58 up-to-date）。
- 覆盖 fast/slow EWMA、TTFB、缓存/本地样本排除、网络复位、立即降级、延迟升级、抗震荡、请求截止时间、最低质量和相同表示层不重建计划。

首次 Proof 发现旧测试仍要求两个大跌样本才降级；根据 Stage 18C 合同，将 fast EWMA 的可信大跌改为首个样本立即降低上限，同时保留慢速 EWMA与升级稳定窗口。修复后回归通过。

## 未验证部分

- 真实 Telegram CDN 吞吐/TTFB 与实际 HLS 表示层切换：尚未验证。
- 真实设备 decoder、iQOO 12 和账号播放：尚未验证。

## 回退策略

- `cvfHybridAbrEnabled=false` 关闭 TDLib ABR bridge/risk ceiling；HLS 与 MP4 fallback 独立保留。
- 风险控制器是纯状态机，故可单独替换参数而不影响 Telegram 数据边界。

## 下一阶段入口

Stage 18D 将相同 buffer/网络/request 风险数据用于唯一下一条的按秒预算，并把每个 TDLib chunk 限制在 10 MiB 总 ceiling 内。
