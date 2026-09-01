# Stage 18F：弱网连续播放最终性能与安全验收

日期：2026-08-24

## Outcome

以固定 seed 的确定性事件模型验证 Stage 18 的 HLS/MP4 fallback、混合 ABR、唯一下一条动态预算、10 MiB 硬上限和单 ExoPlayer 交接策略；在主机可验证范围内完成单元、集成、lint、APK 和 Compose Path B Proof。合成结果只证明策略和状态机，不替代真实 Telegram/CDN、decoder 或 iQOO 12 测量。

## Scope

- 新增 0.35/0.5/1/2/12 Mbps、30/80/120/180 ms RTT、5%～35% jitter 的固定 seed 网络矩阵。
- 每档执行 30 次转场，并覆盖冷缓存、manifest/init/prefix/full hit、跳过目标、快滑/反滑、seek/rebuffer、HLS 两类失败与 MP4 fallback、网络切换、generation 失效、缓存清理、10 MiB、高/低码率大文件、暂停/降速/断流恢复。
- 汇总首帧分段、buffer、prediction、质量切换、abandon、缓存/网络/浪费字节和安全失败。
- 执行全量 Gradle、Compose host proof，并在不接触物理手机的前提下检查 emulator 是否可用。

## Boundary

- 不连接、安装、控制或等待 iQOO 12；不运行真机 instrumentation。
- x86_64 emulator 不运行 TDLib native smoke。
- 不请求真实账号、手机号、验证码、密码、api_id 或 api_hash。
- 不把固定 seed 模型描述为真实 CDN、Surface、decoder、耗电、温度或真实 SampleQueue A/B 结果。
- `cvfSampleQueuePreloadEnabled` 保持默认关闭，直到真实 A/B 同时满足 P95 改善至少 15%、FIRST_FRAME 100% 且安全失败为 0。

## Failure states

- FIRST_FRAME 缺失、rebuffer（仅在最低质量可持续时）、crash、black screen、wrong video 或 audio overlap。
- 多播放器、多 next target、单次 next range 超过 512 KiB，或单目标新增网络超过 10 MiB。
- 移动数据默认产生 next payload，当前 buffer 低水位仍允许 next payload，或跳过浪费超标。
- HLS 失败不回退 MP4、generation 失效后仍可读、owner 清理误删在用 range。
- 正常网络 P95 相对 Stage 17 回归超过 5%，或有对应 Stage 17 基线的弱网档改善不足 50%。
- 任一全量构建、lint、单元测试或 Compose host proof 失败。

## 确定性矩阵

测试：`Stage18WeakNetworkContinuousPlaybackSimulationTest`；seed `18018`；每档 30 次，共 150 次转场。

| 档位 | RTT / jitter | 安全表示层 | 已准备首帧 P50/P95/max ms | gesture→first-frame P95 ms | min buffer s | rebuffer / FIRST_FRAME | waste P50/P95 | 单目标最大新增网络 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.35 Mbps | 180 ms / 35% | 240p / 220 Kbps | 130.3 / 192.7 / 249.7 | 779.9 | 21.6 | 0 / 30 | 0 / 256 KiB | 256 KiB |
| 0.5 Mbps | 180 ms / 35% | 360p / 300 Kbps | 133.0 / 202.5 / 244.1 | 792.9 | 22.2 | 0 / 30 | 0 / 256 KiB | 256 KiB |
| 1 Mbps | 120 ms / 25% | 480p / 600 Kbps | 131.3 / 213.2 / 257.2 | 642.0 | 35.4 | 0 / 30 | 0 / 512 KiB | 632,813 B |
| 2 Mbps | 80 ms / 15% | 480p / 600 Kbps | 142.0 / 227.5 / 255.0 | 421.0 | 77.3 | 0 / 30 | 0 / 512 KiB | 1,265,625 B |
| 12 Mbps | 30 ms / 5% | 720p / 1.5 Mbps | 139.7 / 209.8 / 261.3 | 260.4 | 172.4 | 0 / 30 | 0 / 512 KiB | 3,164,063 B |

每档 crash/black screen/wrong video/audio overlap 均为 0；最大 next targets=1、ExoPlayer instances=1、最大单次 range=512 KiB；移动数据默认 next payload=0。表内“单目标最大新增网络”是 duration-first 公式实际选择，不是 ceiling；本矩阵最高只需要约 3.02 MiB，控制器没有为了碰到 10 MiB 而扩大下载。独立 Stage 18D 单元测试证明高码率目标可逐级到 10 MiB 且绝不越界。

## 分段与传输指标

以下为各档 P95，依次为 `target-known→plan-ready / manifest-ready / init-segment-ready / next-playable-seconds-ready / gesture→settled / settled→bind / bind→first-byte / first-byte→READY / READY→first-frame`，单位 ms：

| 档位 | P95 分段 |
|---|---|
| 0.35 Mbps | 7.8 / 180.0 / 63.0 / 8591.3 / 87.4 / 9.4 / 654.3 / 92.8 / 29.6 |
| 0.5 Mbps | 7.3 / 180.0 / 63.0 / 5815.3 / 85.6 / 9.5 / 637.5 / 92.3 / 28.0 |
| 1 Mbps | 7.4 / 120.0 / 42.0 / 6585.3 / 85.7 / 9.9 / 498.8 / 102.2 / 29.1 |
| 2 Mbps | 7.9 / 80.0 / 28.0 / 5805.3 / 88.2 / 9.6 / 267.6 / 106.1 / 29.4 |
| 12 Mbps | 7.7 / 30.0 / 10.5 / 2210.5 / 88.1 / 9.8 / 102.0 / 99.4 / 29.1 |

`next-playable-seconds-ready` 是后台预热完成成本；已完成的目标在 gesture 后不重复等待该成本。每档模拟 2 次有滞回的质量切换、1 次紧急降级、2 次 abandon。predicted completion 相对 actual completion 的 P95 误差依次为 38.2%、34.5%、32.0%、21.5%、12.2%；绝对 P95 分别为 1524.1/1112.3/1140.8/887.1/232.1 ms。高抖动档以 fast/slow 中较小者形成保守预测，并在每个 512 KiB chunk 后重新评估。

30 次转场累计 current-media 下载字节依次为 16,500,000 / 22,500,000 / 45,000,000 / 45,000,000 / 112,500,000；next cache-hit 字节为 3,341,080 / 3,841,080 / 5,716,080 / 5,716,080 / 11,341,080；next 新网络字节为 4,587,520 / 4,587,520 / 13,854,245 / 32,252,670 / 83,580,810。累计值用于传输统计，不是单目标预算。

## 与 Stage 17 对比

| 档位 | Stage 17 gesture P95 ms | Stage 18 合成 P95 ms | 变化 |
|---|---:|---:|---:|
| 0.35 Mbps | 无对应档 | 779.9 | 新增物理边界覆盖，不作因果比较 |
| 0.5 Mbps | 7031.1 | 792.9 | 改善 88.7% |
| 1 Mbps | 3238.7 | 642.0 | 改善 80.2% |
| 2 Mbps | 1538.7 | 421.0 | 改善 72.6% |
| 12 Mbps | 329.2 | 260.4 | 改善 20.9%，无回归 |

Stage 17 与 Stage 18 均为固定 seed 主机模型；该表证明策略模型门槛，不证明真实 Telegram CDN 的同幅度收益。

## 最低可持续边界

最低 fixture 表示层是 220 Kbps。在长期只有 150 Kbps、初始 reservoir 35 秒的模型中，有限缓存可吸收约 110 秒播放，之后必然耗尽；不能物理保证无限零卡顿。实际最低边界还受音频、容器开销、segment 峰值、TDLib/CDN 和 decoder 影响，真实值尚未验证。

## 安全与回退

- Telegram HLS 内部 URI 仅接受当前 generation 的短期 opaque token；外部 HTTP(S)、file/content、未知 scheme、路径穿越和 playlist loop fail closed。
- 每个 segment/byterange 仍经 `TelegramFileManager`；没有 HTTP proxy、local server、SimpleCache、公共存储或第二播放器。
- HLS parser/source/segment 失败后，同一 ExoPlayer 单次回退 direct MP4；`supportsStreaming=false` 行为不变。
- `cvfTelegramHlsEnabled=false`、`cvfHybridAbrEnabled=false`、`cvfDynamicNextPreloadEnabled=false` 可分别回退风险层；SampleQueue 默认已关闭。

## Proof 与结果

已执行：

```powershell
$env:JAVA_HOME='E:\Android Studio\jbr'
.\gradlew.bat :core:domain:test :player:testDebugUnitTest --tests "com.qixuan.channelvideoflow.player.Stage18WeakNetworkContinuousPlaybackSimulationTest" --no-daemon --console=plain
```

- 首轮发现 target 已完全由缓存覆盖时仍把 TTFB 计为预测完成时间；修复为 remaining bytes=0 时 completion=0。
- 第二轮修正已准备 SampleQueue 交接模型，不再重复计入已完成的 payload 下载时间。
- 交接生命周期回归证明 manager 只保留 current+唯一 next；NEXT→CURRENT 后解除 preload endpoint，新 next 不会关闭仍在播放的 current；`METADATA_ONLY` 只进入 track preparation，payload 仍为 0。
- 定向 Stage 18F 模拟最终 `BUILD SUCCESSFUL`。
- 最终源代码变更后的全量 `test`：`BUILD SUCCESSFUL in 1m 8s`，345 tasks（41 executed，304 up-to-date）；定向 Compose 命令覆盖结果目录后再次完整 replay 为 `BUILD SUCCESSFUL in 33s`，最终聚合 XML 为 1045 tests、0 failures、0 errors、0 skipped。
- 最终全量 `lint`：`BUILD SUCCESSFUL in 1m 29s`，220 tasks（24 executed，196 up-to-date）。首次 lint 精确暴露 4 个 Media3 unstable opt-in 错误；改为 AndroidX `@OptIn(markerClass=[UnstableApi::class])` 将 opt-in 收敛到 adapter 后，定向与全量 lint 均通过；未建立 baseline 或 suppress。
- `assembleDebug`：`BUILD SUCCESSFUL in 15s`，190 tasks（5 executed，185 up-to-date）。APK 为 45,116,413 B，SHA-256 `65521DCB8B4718440E845639EFF1421FF7A20AAF68AC5D5AEF5815F8FB46AB64`。
- `:app:compileInstrumentationKotlin`：`BUILD SUCCESSFUL in 14s`。
- Login/Channel/ComposeSmoke Robolectric：`BUILD SUCCESSFUL in 25s`；CacheSettings Robolectric：`BUILD SUCCESSFUL in 21s`。
- Stage 18 benchmark PowerShell parser/runner：`SWIPE_BENCHMARK_SCRIPT_TEST_RESULT=PASS`。
- APK 只含 `arm64-v8a` 的 `libtdjni.so`、`libandroidx.graphics.path.so`、`libdatastore_shared_counter.so`；与既有 provenance 合同一致。
- merged manifest 只含 `INTERNET`/`ACCESS_NETWORK_STATE`，`allowBackup=false`，并同时引用 `dataExtractionRules`/`fullBackupContent`。
- `local.properties` 继续由 `.gitignore` 排除；没有读取或输出其内容。

## 未验证部分

- iQOO 12 / OriginOS 6 install、launch、真实 Surface/decoder、功耗与温度：尚未验证。
- 真实 Telegram 账号 HLS 覆盖率、线上 playlist 变体、TDLib native/CDN 弱网和真实 HLS→MP4 fallback：尚未验证。
- 真机 SampleQueue A/B：尚未验证，因此生产默认不开启。
- API 36 AOSP x86_64 emulator Compose UI：尚未验证。一次性只读检查确认 `CVF_AOSP_API36_X86_64` 配置存在，但没有运行中的 emulator；未启动、未等待、未调用 `adb devices`。

## 未来 iQOO 12 / emulator 验证入口

只有仓库所有者以后明确给出设备验证窗口时执行；以下命令不会被本阶段自动调用：

```powershell
$env:JAVA_HOME='E:\Android Studio\jbr'

# API 36 AOSP x86_64 AVD 已启动后，仅运行 Compose UI，不运行 TDLib native smoke。
.\scripts\run-emulator-compose-tests.ps1 -Serial <x86_64-emulator-serial>

# iQOO 12：先执行保留数据的 install + cold launch smoke。
.\scripts\run-vivo-launch-smoke.ps1 -Serial <iqoo-serial>

# 人工登录并安全进入播放页后，各跑 30 次；不清缓存、不改账号、不改 VPN。
.\scripts\run-swipe-first-frame-benchmark.ps1 -Serial <iqoo-serial> -SwipeCount 30 -PerSwipeTimeoutSeconds 12 -Mode Normal -Direction Forward -ReportStage stage18 -SkipBuild
.\scripts\run-swipe-first-frame-benchmark.ps1 -Serial <iqoo-serial> -SwipeCount 30 -PerSwipeTimeoutSeconds 12 -Mode Fast -Direction Reverse -ReportStage stage18 -SkipBuild
```

真实 A/B 必须使用相同账号、队列、媒体、质量、网络窗口和缓存前置条件，分别以 `-PcvfSampleQueuePreloadEnabled=false/true` 构建；候选只有在 P95 改善至少 15%、FIRST_FRAME 100%、black/wrong/audio/crash 均为 0 时才可考虑改默认值。runner 已用主机脚本测试锁定 `stage18` 独立报告目录、标题和上述比较边界。

## 下一阶段入口

Stage 18A～18F 在主机 Proof 全部通过后结束。本实现之后只建议在用户明确提供可用验证窗口时执行真实账号/HLS 覆盖、iQOO launch smoke 与独立 SampleQueue A/B；未达到 15% 门槛时保持 SampleQueue flag 关闭。
