# Stage 18A — GitHub 复用审计、数据边界和可观测性

## Outcome

项目能够在 TDLib 边界内同时读取 `AlternativeVideo.video` 与 `AlternativeVideo.hlsFile`，在边界外使用 app-owned direct/HLS 描述符，并以固定字段脱敏记录 HLS 能力覆盖状态。

## Scope

- 更新 `TdLibMessageObjectMapper` 和 Telegram client 中间模型。
- 扩展 `VideoPlaybackVariant`，保留 direct MP4 fallback 并增加 HLS manifest file 描述。
- 增加 delivery capability、HLS coverage status 和固定字段观测模型。
- 更新 mapper/repository 回归测试，增加边界和日志脱敏测试。
- 建立 `STAGE18_GITHUB_REUSE_AUDIT.md`。

## Boundary

- 本阶段不解析 manifest，不创建内部 URI，不启动 `HlsMediaSource`。
- 本阶段不改变播放器、质量选择、范围调度和预加载预算。
- HLS manifest/fileId 映射只来自刷新后的官方 TDLib message；不写 Room。
- `supportsStreaming=false` 既有行为不变。

## Failure states

- 无 alternative video：`NO_ALTERNATIVE_VIDEO`。
- 有 direct alternative 但无有效 hls file：`NO_HLS_MANIFEST`。
- 描述字段不满足后续注册要求：`INVALID_DESCRIPTOR`。
- 有至少一个 direct + HLS 描述：`AVAILABLE`。

以上状态都是枚举，日志不能携带任意错误文本、URL、caption 或 TDLib 对象。

## 架构决定

1. `VideoPlaybackVariant.fileId` 继续表示 progressive MP4 fallback，减少对 Stage 11/12 质量选择和现有测试的破坏。
2. `hlsManifestFile` 是 `TelegramMediaFileReference?`，只含 fileId、remoteUniqueId、size；不含 `TdApi.*`。
3. `alternativeId` 被保留，用于审计和去重，但不作为全局媒体唯一键；视频唯一键仍为 `chatId + messageId`。
4. Room schema 不变。HLS 描述只存在于 fresh-reference playback plan 的内存生命周期。
5. 账号 generation 的 URI/token 所有权在 Stage 18B 的内部资源注册表落地；不把 generation 写入 Room 或领域持久模型。

## 参数

- 日志行最大设计长度：160 字符以内。
- capability counters 负数在输出时归零。
- mapper 只接受 `hlsFile.id > 0` 的 manifest descriptor。

## Proof

- `:core:model:test`
- mapper 单元测试。
- repository fresh-reference 回归测试。
- app-owned 类型反射边界测试。
- 固定字段日志脱敏测试。
- `:core:model:compileKotlin :telegram:compileDebugKotlin`

## 测试结果

- 2026-08-24：`./gradlew.bat :core:model:test :telegram:testDebugUnitTest --no-daemon --console=plain` 通过。
- `BUILD SUCCESSFUL in 39s`，58 个 task（22 executed，36 up-to-date）。
- mapper、fresh-reference repository、app-owned 类型边界、固定字段日志脱敏和既有 Telegram mapper 回归均通过。

## 未验证部分

- 真实 Telegram 账号的 HLS 覆盖率：尚未验证。
- iQOO 12 / OriginOS 6 真机：尚未验证，且本阶段明确禁止操作。
- 真实 Telegram/CDN manifest 内容：尚未验证；格式安全解析由合成 fixture 和 Stage 18B 完成。

## 回退策略

- 没有有效 HLS descriptor 时只保留现有 direct MP4。
- 领域字段均有默认值，旧持久化记录和旧构造路径继续工作。
- 不变更数据库，因此无需 destructive migration 或 rollback。

## 下一阶段入口

Stage 18B 使用 fresh message 中的 `hlsManifestFile` 建立与账号 generation 绑定、短生命周期且可撤销的内部资源注册表；严格解析/重写 Telegram `mtproto:` 引用后再交给 Media3 HLS。
