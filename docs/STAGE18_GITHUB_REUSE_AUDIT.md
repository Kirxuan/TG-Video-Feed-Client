# Stage 18 GitHub 复用审计

## 审计范围与结论

- 审计日期：2026-08-24。
- 项目版本基线：AndroidX Media3 `1.10.1`、TDLib `1.8.66`（提交 `022d602...`）。
- 结论：生产实现只直接依赖项目同版本的 AndroidX Media3 稳定 API和官方 TDLib。
- Telegram Android、Telegram iOS、Telegram X、hls.js、Shaka Player、dash.js 只提供架构或算法思想；未复制、翻译或 vendor 任何源码。
- Feed/通用播放器与代理缓存项目均不引入，避免播放器池、第二播放内核、HTTP 代理和双缓存。

## 生产基础

| 项目 | 实际读取的文件 | 许可证 | 可复用内容 | 不复用内容 | 决定 |
|---|---|---|---|---|---|
| [AndroidX Media3](https://github.com/androidx/media) | [`DefaultLoadControl.java`](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultLoadControl.java)、[`AdaptiveTrackSelection.java`](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/AdaptiveTrackSelection.java)、[`BandwidthMeter.java`](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/upstream/BandwidthMeter.java)、[`HlsMediaSource.java`](https://github.com/androidx/media/blob/release/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaSource.java)、[`PreloadMediaSource.java`](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/preload/PreloadMediaSource.java)、[`DefaultPreloadManager.java`](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/preload/DefaultPreloadManager.java)、[`RELEASENOTES.md`](https://github.com/androidx/media/blob/release/RELEASENOTES.md) | Apache-2.0 | `HlsMediaSource`、同播放器接管预热 `MediaPeriod`、duration-based preload、`BandwidthMeter`、`AdaptiveTrackSelection`、player/preload target bytes | 不直接使用默认 preload 调度。`DefaultLoadControl.shouldContinuePreloading` 在任意播放器加载时返回 false；项目 50 秒 reservoir 会让下一条长期饥饿 | 采用正式 API；自定义风险门控、唯一下一条、TDLib byte ceiling |
| [TDLib](https://github.com/tdlib/td) | [`td_api.tl`](https://github.com/tdlib/td/blob/master/td/generate/scheme/td_api.tl)、[`LICENSE_1_0.txt`](https://github.com/tdlib/td/blob/master/LICENSE_1_0.txt)，以及仓库内生成的 `telegram/tdlib/.../TdApi.java` | Boost Software License 1.0 | `alternativeVideo.hls_file`、`alternativeVideo.video`、`downloadFile(offset,limit,priority)`、`updateFile`、prefix/cancel/cache 语义 | Bot API、手写 MTProto、消息链接媒体源 | 完全采用，唯一 Telegram 数据源合同 |

Media3 源码复核出的关键约束：

- `PreloadMediaSource` 可预先完成 source、track 与 sample 加载，并在同一 `MediaPeriod` key 命中时交给播放器。
- `DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded` 是按时长目标，不代表可以绕过项目的 10 MiB 新增网络上限。
- `DefaultLoadControl` 支持 `PlayerId.PRELOAD` 独立 target bytes；Stage 18 仍需由 TDLib range 控制器做最终硬上限。
- `AdaptiveTrackSelection` 的默认升级/降级缓冲滞回只作为底层防线，Stage 18 另用真实 TDLib 网络样本和 deadline 风险控制。

TDLib schema 复核出的边界：

- 每个 `AlternativeVideo` 同时给出 direct `video:file` 和 `hls_file:file`，direct file 是 HLS 失败的官方 MP4 fallback。
- `downloadFile` 的 `offset/limit` 与 `getFileDownloadedPrefixSize`、`updateFile` 共同构成可取消范围读取；Media3 的 `DataSpec.position/length` 最终仍映射到这套合同。
- `supportsStreaming=false` 的行为不改变，不能借 HLS 或预加载触发完整下载。

## Telegram 客户端参考

| 项目 | 实际读取的文件 | 许可证 | 采用的思想 | 拒绝/限制 | 决定 |
|---|---|---|---|---|---|
| [Telegram Android](https://github.com/DrKLO/Telegram) | [`VideoPlayer.java`](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/VideoPlayer.java)、[`FileLoadOperation.java`](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/FileLoadOperation.java)、[`LICENSE`](https://github.com/DrKLO/Telegram/blob/master/LICENSE) | GPL-2.0-or-later | AUTO 使用 HLS、质量集合、流请求优先于后台获取、范围/取消思想 | 禁止复制或逐行翻译；内部 ExoPlayer fork、文件加载器和 UI 架构不引入 | 仅架构参考 |
| [Telegram iOS](https://github.com/TelegramMessenger/Telegram-iOS) | [`HLSVideoContent.swift`](https://github.com/TelegramMessenger/Telegram-iOS/blob/master/submodules/TelegramUniversalVideoContent/Sources/HLSVideoContent.swift)、[`README.md`](https://github.com/TelegramMessenger/Telegram-iOS/blob/master/README.md) | GPL 系列仓库；按强 copyleft 边界处理 | `application/x-mpegurl`、`mtproto:<videoFileId>` 关联 playlist/quality file；合成 master；按 `EXTINF` + `BYTERANGE` 累计 prefix seconds | 其本地 server/JS/AVFoundation 实现不适用于本项目，且不得复制 Swift | 仅采用格式事实与按秒预热思想 |
| [Telegram X](https://github.com/TGX-Android/Telegram-X) | [`docs/GUIDE.md`](https://github.com/TGX-Android/Telegram-X/blob/main/docs/GUIDE.md)、[`LICENSE`](https://github.com/TGX-Android/Telegram-X/blob/main/LICENSE)；指南中的 `TdlibDataSource` 状态说明 | GPL-3.0 | TDLib DataSource 接入 ExoPlayer、按账号上下文管理生命周期的方向 | 当前指南将视频流式 UI 标为未完成；整体架构与旧 ExoPlayer 不引入 | 部分参考，现有 `TelegramMediaDataSource` 更符合本仓库边界 |

## ABR 算法参考

| 项目 | 实际读取的文件 | 许可证 | 采用的思想 | 不采用内容 | 决定 |
|---|---|---|---|---|---|
| [hls.js](https://github.com/video-dev/hls.js) | [`abr-controller.ts`](https://github.com/video-dev/hls.js/blob/master/src/controller/abr-controller.ts)、[`docs/API.md`](https://github.com/video-dev/hls.js/blob/master/docs/API.md)、[`LICENSE`](https://github.com/video-dev/hls.js/blob/master/LICENSE) | Apache-2.0 | fast/slow EWMA、TTFB 独立估计、starvation deadline、remaining bytes 完成时间、紧急 abandon/downswitch、真实 segment bitrate | JavaScript 框架与浏览器事件模型 | 独立 Kotlin 状态机重新实现思想，无源码复制 |
| [Shaka Player](https://github.com/shaka-project/shaka-player) | [`simple_abr_manager.js`](https://github.com/shaka-project/shaka-player/blob/main/lib/abr/simple_abr_manager.js)、[`ewma_bandwidth_estimator.js`](https://github.com/shaka-project/shaka-player/blob/main/lib/abr/ewma_bandwidth_estimator.js)、[`LICENSE`](https://github.com/shaka-project/shaka-player/blob/main/LICENSE) | Apache-2.0 | upgrade/downgrade 不同安全因子、switch interval、网络变化复位、短时缓存命中排除、无可靠估计选最低质量 | 浏览器 NetworkInformation、完整 Shaka 管线 | 独立实现上述规则 |
| [dash.js](https://github.com/Dash-Industry-Forum/dash.js) | [`BolaRule.js`](https://github.com/Dash-Industry-Forum/dash.js/blob/development/src/streaming/rules/abr/BolaRule.js)、[`ThroughputRule.js`](https://github.com/Dash-Industry-Forum/dash.js/blob/development/src/streaming/rules/abr/ThroughputRule.js)、[`InsufficientBufferRule.js`](https://github.com/Dash-Industry-Forum/dash.js/blob/development/src/streaming/rules/abr/InsufficientBufferRule.js)、当前 `AbandonRequestsRule.js`/`SwitchHistoryRule.js`/`DroppedFramesRule.js` 入口、[`LICENSE.md`](https://github.com/Dash-Industry-Forum/dash.js/blob/development/LICENSE.md) | BSD-3-Clause | buffer occupancy、throughput、insufficient-buffer、abandon、switch history、dropped-frame 信号混合 | 不移植 BOLA 数学实现或 dash.js 管线 | 采用“吞吐 + 实际 buffer + deadline”小状态机 |

Kotlin 实现没有从上述项目复制表达式或控制流；仅将公开算法原则落实为本项目参数化、确定性可测的状态机，因此无额外源码归属文件需要加入。

## Feed、播放器生命周期与预加载样例

| 项目 | 实际读取的文件 | 许可证/状态 | 可取之处 | 拒绝理由 | 决定 |
|---|---|---|---|---|---|
| [compose-reels](https://github.com/manjees/compose-reels) | `README.md`、`LICENSE` | Apache-2.0 | settled page/lifecycle 概念 | 默认 7-player pool、双向多条预载、150 MB disk cache | 拒绝实现，仅比较 |
| [SampleReelsApp](https://github.com/Shahidzbi4213/SampleReelsApp) | `README.md`；仓库根未找到许可证文件 | 未确认许可证 | HLS media source 类型分离 | URL/HTTP 模型、可选 cache，且许可证不明 | 拒绝 |
| [Toro](https://github.com/eneim/Toro) | `README.md`、`LICENSE` | Apache-2.0 | idle/visibility 后选择单一播放目标 | Android MediaPlayer/RecyclerView 旧架构 | 只采用 settled target 思想 |
| [Kohii](https://github.com/eneim/Kohii) | `README.md`、`LICENSE`、公开 `ExoPlayerPool` API 文档 | Apache-2.0 | renderer 绑定/解绑生命周期 | ExoPlayer 2.17.1、player pool、SimpleCache | 拒绝引入 |
| [GSYVideoPlayer](https://github.com/CarGuo/GSYVideoPlayer) | `README.md`、`LICENSE` | Apache-2.0 | 保留上一帧/单例播放的 UX 参考 | 多内核、IJK/FFmpeg native、HTTP cache/SimpleCache、多播放模式 | 拒绝引入 |
| [DKVideoPlayer](https://github.com/Doikki/DKVideoPlayer) | `README.md`、`LICENSE` | Apache-2.0 | 控制层与内核层分离 | 与现有 Media3/TDLib 平行的播放器框架 | 拒绝引入 |
| [compose-video-player-pooling](https://github.com/nikitachicherindev/compose-video-player-pooling) | `README.md` 中列出的 `VideoRow`、`ActiveIndices`、`ExoPlayerPool`、`VideoContainer`、`VideoController`、cache factory | 明确“no license granted” | 稳定 item identity、取消传播、首帧前不透明 overlay | 明确 player pool + SimpleCache，且未授权复用 | 拒绝源码；独立保留现有单播放器/共享 Surface 设计 |
| [VideoPlayerManager](https://github.com/danylovolokh/VideoPlayerManager) | `README.md`（含许可证声明） | Apache-2.0 | scroll idle 后唯一最可见 item | Android MediaPlayer、停止/重建资源、旧 jcenter | 只采用唯一 settled target 思想 |
| [NewPlayer](https://github.com/TeamNewPipe/NewPlayer) | `README.md`、`LICENSE` | GPL-3.0-or-later | Compose 控制层和 Media3 生命周期可读性 | GPL、面向 HTTP/NewPipe、不是 TDLib 单播放器 feed | 仅比较，拒绝代码 |

## 缓存、代理和大型播放器反例

| 项目 | 实际读取的文件 | 许可证 | 拒绝理由 | 决定 |
|---|---|---|---|---|
| [AndroidVideoCache](https://github.com/danikula/AndroidVideoCache) | `README.md` | Apache-2.0 | 本地 HTTP proxy，只接受 direct URL，不支持 HLS；会与 TDLib cache 构成双缓存 | 拒绝 |
| [ijkplayer](https://github.com/bilibili/ijkplayer) | `README.md` | LGPL-2.1-or-later + 多项第三方许可证 | 新 FFmpeg/native 播放内核、ABI 体积、平行架构 | 拒绝 |
| [VLC Android](https://github.com/videolan/vlc-android) | `README.md`、`COPYING` | app GPL-2.0-or-later；LibVLC LGPL-2.x | 新 LibVLC/medialibrary/native 内核、协议面过宽、架构和体积成本 | 拒绝 |
| [google/ExoPlayer](https://github.com/google/ExoPlayer) | `README.md`、`LICENSE` | Apache-2.0 | 旧仓库已弃用，最后版本 2.19.1，官方要求迁移 AndroidX Media3 | 拒绝，使用 AndroidX Media3 |

## 最终采用矩阵

| 能力 | 来源 | 采用方式 |
|---|---|---|
| Telegram HLS/direct 描述 | TDLib schema | 官方类型在 telegram 模块内映射为 app-owned descriptor |
| HLS 播放/动态 track | Media3 1.10.1 | `HlsMediaSource` + 同播放器 `AdaptiveTrackSelection` |
| TDLib 真实带宽 | TDLib + Media3 `BandwidthMeter` 合同 | 只采活动新增网络范围，桥接给播放器 |
| 风险 ABR | hls.js/Shaka/dash.js 思想 | Kotlin fast/slow EWMA + TTFB + buffer/deadline/hysteresis 状态机 |
| 按秒下一条预热 | Telegram iOS + Media3 duration preload | segment/byterange 精确秒数或 MP4 峰值码率推算；0/2/5/10 MiB 分级 |
| sample queue 预热 | Media3 `PreloadMediaSource` | 唯一下一条、同一播放器、独立 feature flag、默认须由 A/B 门槛决定 |
| 缓存 | 现有 TDLib internal cache | 不添加 SimpleCache、HTTP proxy 或公共存储 |

## 许可证与归属结果

- 新增生产代码为本仓库独立实现。
- 只通过 Gradle 依赖 AndroidX Media3，并继续使用仓库内有 provenance 的官方 TDLib。
- 未复制第三方代码片段，因此没有新增 NOTICE/源码归属义务。
- GPL/LGPL 项目仅用于架构对照；未形成源码衍生。
