# 阶段 11：Telegram 服务端视频清晰度选择

日期：2026-07-29
状态：实现、主机 Proof、Compose Path B、Android 13 ARM64 真机安装/冷启动和真实账号样本验证均已通过

## 1. 阶段合同

### Outcome

用户可在“播放与缓存”中选择“自动选择”“省流模式”“720p”“原画”。播放稳定当前项前，应用通过官方 TDLib 刷新消息并从 Telegram 提供的直接视频文件中选择一个安全版本；当前视频和唯一下一条预加载使用同一选择策略。

### Scope

- 在 `core:model` 表达瞬时服务端视频版本和质量偏好。
- 在 `core:domain` 实现不依赖 TDLib/Android 的选择策略。
- 在 `telegram` 边界映射 `alternativeVideos`，刷新消息但只把原始视频元数据写入 Room。
- 用 DataStore 保存质量偏好，并在设置页提供四个中文选项。
- 让唯一播放器与唯一下一条预加载读取最终选中的 fileId/size。
- 补齐策略、映射、Repository、DataStore、ViewModel、预加载和 Compose 测试。

### Boundary

- 不在手机本地转码；本地转码不能减少已经下载的网络字节，还会增加 CPU、耗电、温度和首帧等待。
- 不接入 HLS manifest，不改变既有 ProgressiveMediaSource/TDLib 分段读取链路。
- 不在同一视频播放途中无缝切换清晰度；偏好在下一次稳定绑定或重试时生效。
- 不把服务端可选版本写入 Room，不升级 Room schema。
- 不改变 4 MiB 当前前读、256 KiB 下一条预加载、50–60 秒播放器缓冲、500MB 默认私有缓存或唯一 ExoPlayer 合同。

### Failure states

- 消息刷新失败、超过 3 秒、没有直接可选版本、版本信息无效或 codec 不是 H.264：回退到索引中的原始文件。
- “省流模式”只有在候选文件更小（未知大小时像素更少）时才选择候选。
- “720p”不放大原本不超过 720p 的视频；没有适合的 720p 候选时尝试安全的省流候选，否则回退原画。
- `supportsStreaming=false` 继续显示“该视频暂不支持流式播放。”，不触发完整下载。

### Proof

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat lint --rerun-tasks --no-daemon --console=plain
.\gradlew.bat assembleDebug --rerun-tasks --no-daemon --console=plain
.\scripts\run-emulator-compose-tests.ps1 -Serial emulator-5554
.\scripts\run-vivo-launch-smoke.ps1 -Serial <device-serial> -SkipBuild
```

## 2. 选择策略

| 用户选择 | 实际策略 |
|---|---|
| 自动选择 | Wi-Fi 选择不超过 720p 的最高安全版本；移动数据、OTHER/VPN 或离线状态选择省流版本 |
| 省流模式 | 选择 Telegram 提供的最小已知文件；只有未知大小时才按像素数比较 |
| 720p | 选择像素预算不超过 `1280 × 720` 的最高版本，同分辨率优先较小文件 |
| 原画 | 直接使用索引中的原始文件，最新顺序下不额外刷新消息 |

第一版只接受 TDLib `alternativeVideos` 中的直接 H.264 文件。API 26+ 保证 AVC/H.264 基线解码能力；HEVC/AV1 的设备差异较大，因此本阶段不把它们作为自动候选。选择结果只改变实际播放的 `fileId`、大小和分辨率，不改变视频的 `chatId + messageId`、标题、标签、保护属性或发布时间。

## 3. 实现结果

- `TdLibMessageObjectMapper` 将官方 `alternativeVideos[].video` 映射为内部 `TelegramClientVideoVariant`，过滤原始 fileId 的重复项。
- `TdLibTelegramMessageRepository.refreshVideo` 返回瞬时候选列表；Room 仍只持久化原始消息视频元数据，避免把易变 fileId 当作长期业务事实。
- `VideoPlaybackViewModel` 在稳定绑定时刷新并选择当前版本；非原画刷新以 3 秒为上限。下一条预加载使用独立可取消任务，刷新后采用相同网络与偏好策略。
- `VideoPlayerManager`、`VideoPreloadManager`、缓存 pin、内部 URI 与区间请求全部使用最终 `playbackFileId`，不会先预加载原画再播放省流版本。
- DataStore 使用 `video_quality` 字符串保存偏好；缺失或未知值安全回退 `AUTO`。
- Debug 质量日志只包含是否为替代版本、fileId、宽高和文件大小，不包含消息正文、私有路径或凭证。

## 4. 自动化结果

- Fresh `test`：通过，345 个 Gradle task 全部重新执行；72 个 XML 文件共 437 次测试执行，0 failure、0 error、0 skipped。
- Fresh `lint`：通过，220 个 task；只有既有废弃存储广播提示和 native 未 strip 提示。
- Fresh `assembleDebug`：通过，190 个 task。
- Compose Path B：
  - instrumentation Kotlin 编译通过。
  - 指定 Robolectric-Compose suite 通过。
  - API 36 AOSP x86_64 emulator UI instrumentation：31/31 通过。
  - Android 13 ARM64 真机最终 APK install + cold launch smoke：通过，目标包无 crash。

新增或扩展的回归覆盖包括：

- 省流、720p、AUTO Wi-Fi/AUTO 移动网络、原画、非 H.264、无候选、不放大和候选更大时回退。
- TDLib `AlternativeVideo` 映射和 Repository 瞬时刷新。
- DataStore 保存/恢复质量偏好。
- 当前项与下一条使用相同服务端版本策略。
- 刷新超过 3 秒时仍及时回退原始文件。
- 播放器/预加载使用最终 fileId 和 fileSize。
- 设置页四个选项均可见、可选择。

## 5. 真机结果

设备：`21091116UC`，Android 13 / API 33，ARM64；安装保留现有账号、Room、DataStore 和媒体缓存。

- 最终 APK 覆盖安装和冷启动通过；应用回到已授权频道页。
- “省流模式”在重装/冷启动后仍保持选中。
- 同一真实 Telegram 视频的官方文件元数据对照：
  - 原画：`1670 × 1080`，20,360,719 bytes。
  - 省流：`740 × 480`，3,284,570 bytes。
  - 完整文件预算减少约 `83.9%`。
- 最终代码实际绑定日志：`alternative=true`、`740 × 480`、3,284,570 bytes；首次 READY 386ms，播放 11.27 秒期间 rebuffer 0，释放时缓冲前瞻 48.52 秒。

上述数据证明应用确实选择并播放 Telegram 服务端较小文件，不是手机本地缩放。它不是严格空缓存、关闭 VPN、按蜂窝接口抓包的网络实验；实际节省量仍取决于观看时长、已有缓存和 Telegram 为具体视频提供的版本。

## 6. APK 与安全

- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：44,354,917 bytes
- SHA-256：`879AE794F1E3CCCAA0D498575920DBA56F7504C0E0411EFA52AC3766E3F4A62E`
- 权限只有 `android.permission.INTERNET` 与 `android.permission.ACCESS_NETWORK_STATE`。
- `allowBackup=false`，`fullBackupContent` 和 `dataExtractionRules` 均存在。
- APK 只含 `arm64-v8a/libandroidx.graphics.path.so`、`libdatastore_shared_counter.so` 和 `libtdjni.so`。
- `local.properties` 未被读取或纳入扫描；其余源码的硬编码凭证、敏感日志、公共存储、本地转码/HLS 路径扫描均为 0 命中。

## 7. 已知限制

- Telegram 不保证每条普通视频都提供 `alternativeVideos`；没有候选时只能使用原画。
- HEVC/AV1 候选和 HLS 自适应码流尚未验证，也未启用。
- 播放过程中根据吞吐量无缝升降清晰度尚未实现。
- 严格空缓存、蜂窝网络、关闭 VPN、更多高码率/竖屏/不同 codec 视频的逐字节流量对照尚未验证。

## 8. 下一阶段建议

先收集更多真实视频的候选覆盖率、codec、分辨率、文件大小、首帧和 rebuffer 数据，再决定是否值得进入“播放中动态切换”或 HLS 可行性阶段。没有数据前不建议引入本地转码或复杂 ABR。
