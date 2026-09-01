# Stage 18B — Telegram HLS 安全播放路径

## Outcome

存在合格 `AlternativeVideo.hlsFile` 的视频通过 Media3 `HlsMediaSource` 播放；HLS manifest、segment、init segment 仍由 TDLib 私有文件和现有范围请求提供。HLS 失败时，同一 ExoPlayer 对当前 binding 只允许一次渐进式 MP4 回退。

## Scope

- 扩展 `TelegramFileGateway`/`TelegramFileManager` 的短生命周期内部资源注册表。
- 新增严格 HLS parser、generation 绑定的 opaque URI、HLS DataSource/Factory 和播放 session。
- 在现有 `VideoPlayerManager` 内选择 HLS 或渐进式 MediaSource。
- 增加稳定 Media3 1.10.1 HLS 模块；未增加不稳定依赖。
- 增加 parser、URI、range、MAP、取消、超时、fallback、generation、owner/logout 测试。

## Boundary

- 本阶段不实现 ABR 风险状态机、动态下一条预算或 SampleQueue 预热。
- 不创建第二个 ExoPlayer、播放器池、HTTP server/proxy 或 `SimpleCache`。
- 不改变 `supportsStreaming=false` 的提示和禁止自动完整下载行为。
- 不把 Telegram 消息链接、外部 URL 或用户输入 fileId 交给播放器。

## Failure states

- manifest 超过 256 KiB、不是严格 UTF-8、标签不在白名单、资源未注册或格式异常：拒绝 HLS 并回退 MP4。
- `http/https/file/content`、未知 scheme、路径穿越、递归 playlist、token/generation 伪造：拒绝。
- range 超时、取消或本地文件不可读：映射为现有脱敏播放失败；HLS 当前 binding 可安全回退一次。
- 账号 ready/logout 改变 generation：全部旧 URI 和 token 立即失效。

## GitHub 复用来源

- AndroidX Media3 `HlsMediaSource` 和 MediaSource 选择结构：Apache-2.0，直接使用正式 API。
- 官方 TDLib `alternativeVideo.hls_file/video`、`downloadFile(offset, limit)`、`updateFile`：Boost 1.0，沿用正式合同。
- Telegram Android/iOS 的内部资源与 synthetic master 思想：仅架构参考，未复制 GPL 源码。
- 详细矩阵见 `STAGE18_GITHUB_REUSE_AUDIT.md`。

## 架构决定与参数

- 内部 URI：`telegram-hls://resource/<generation>/<kind>/<opaque-token>`；token 为不可推断随机值，默认 TTL 5 分钟、硬上限 15 分钟。
- manifest 最大 256 KiB、最多 4096 行、单行最多 2048 字符。
- 支持 `EXTM3U/STREAM-INF/EXTINF/BYTERANGE/MAP/TARGETDURATION/MEDIA-SEQUENCE/ENDLIST` 及少量必需的无资源标签；其他 EXT 标签 fail closed。
- manifest 和媒体资源均由当前 HLS owner token 保护；退出账号和 session 释放时撤销。
- 每个 Media3 `DataSpec.position/length` 继续映射至现有 TDLib bounded range；当前播放 priority 与取消/超时语义不变。
- Feature flag：`cvfTelegramHlsEnabled`，稳定默认值 `true`；关闭后直接走原渐进式路径。

## Proof

执行：

```powershell
$env:JAVA_HOME='E:\Android Studio\jbr'
.\gradlew.bat :player:testDebugUnitTest :telegram:testDebugUnitTest --no-daemon --console=plain
```

结果：2026-08-24 `BUILD SUCCESSFUL in 31s`，82 个 Gradle task（40 executed，42 up-to-date）。覆盖：

- parser 白名单/黑名单、外部 scheme、路径穿越、递归和大小限制；
- master/media、MAP/init segment、BYTERANGE 到 TDLib range；
- URI 伪造和 generation 隔离；
- manifest 超时转换、range close/cancel；
- HLS→MP4 单次回退门；
- owner 防误删、跨 owner 引用拒绝、退出账号清理。

## 未验证部分

- 真实 Telegram 账号的 HLS 覆盖率与官方线上 manifest 变体：尚未验证。
- 真实 TDLib native/CDN 播放、iQOO 12 安装与真机播放：尚未验证。
- 本阶段不以离线 fixture 冒充上述真机结果。

## 回退策略

- `cvfTelegramHlsEnabled=false` 可整体关闭 HLS，保留原 MP4 播放。
- 单视频 HLS 解析/读取/解码失败时，同一 ExoPlayer 对同一 binding 回退一次；回退同时撤销 HLS token。
- 旧 progressive source、缓存 owner 和错误映射未删除。

## 下一阶段入口

Stage 18C 使用 TDLib 新网络字节样本建立 fast/slow EWMA、TTFB 和网络 generation 复位，并将其接入 Media3 自适应轨道选择与纯 Kotlin `PlaybackRiskController`。
