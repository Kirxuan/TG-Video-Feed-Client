# 阶段 5 单视频播放交接

日期：2026-07-27
状态：主机验证通过；2026-07-27 已将当前播放的 TDLib 有界前读提升至 4 MiB，并保留换段 owner 连续性；播放器在 rebuffer 后改为至少积累 12 秒媒体再续播，并按时间维持 50–60 秒缓冲。该组合优化后的 debug APK 尚待在 V2307A / Android 16 上复测；确定性 seek 仍待补测。

## Outcome

在已授权且已保存频道选择后，频道页会出现“播放测试页”。该页面从已索引视频中优先选择一条 `supportsStreaming=true` 的普通 `messageVideo`，并由唯一 `VideoPlayerManager` 持有的 ExoPlayer 播放。没有可流式视频时，页面会展示第一条不支持流式的视频及精确提示“该视频暂不支持流式播放。”。

## 实现边界

- `TelegramMediaDataSource` 只接受内部 `telegram-file://file/<fileId>` 标识；它不是 Telegram 消息链接，也不是 HTTP/代理地址。
- `DataSpec.position` 映射到官方 TDLib `DownloadFile(fileId, priority, offset, limit, false)` 的 `offset`。Media3 首段只等待 256 KiB 连续可读窗口；当前播放的 TDLib 请求会继续进行有界前读，单次最多 4 MiB，减少大文件建索引和高延迟网络下的区间请求切换。4 MiB 是当前播放的一次受限请求，不是完整下载，也不改变总缓存配额。
- `UpdateFile.file.local.downloadOffset` 与 `downloadedPrefixSize` 是允许读取私有文件路径的唯一依据。DataSource 只在 Media3 Loader 线程同步等待，主线程调用会明确失败。
- `TelegramFileManager` 将同 fileId 的重叠 owner 合并为一个 TDLib 区间；DataSource 换段时先取得下一段 owner，再释放当前 owner，避免有效的前读请求在边界被短暂取消。最后一个 owner 释放时才发送 `CancelDownloadFile(fileId, false)`。若请求在队列中尚未开始，快速关闭不会再启动下载。
- `VideoPlayerManager` 仍只持有一个 ExoPlayer。它以时间缓冲优先：首次播放门槛保持 2.5 秒，发生 rebuffer 后需至少 12 秒才续播，并将正常缓冲维持在 50–60 秒，避免高码率大文件反复短暂续播后再次卡住。
- 视频字节仅来自 TDLib 的 app-internal `cacheDir/tdlib/files`；没有 Media3 `SimpleCache` 或任何第二份完整文件缓存，也不会写入相册、Downloads 或公共目录。
- 这里不是“零下载”。准确表述为：TDLib 在应用内部缓存中保留**临时分段缓存**；本阶段未实现下一条预加载和完整缓存管理。

## 真实验收步骤（待执行）

1. 在真机登录、选择频道并等待索引至少包含一个 `supportsStreaming=true` 视频。
2. 从频道页进入“播放测试页”，记录点击进入到首次 `STATE_READY` 的界面诊断等待毫秒数，以及页面显示的 TDLib 已下载字节快照。
3. 从开头播放、拖动到中间、再拖动到另一位置；确认每次请求使用目标 offset，且没有弹出“不支持流式”或播放失败。
4. 将应用切到后台，确认立即暂停；返回后可由用户再次播放。快速返回频道页，确认不继续产生无 owner 下载。
5. 断开网络后点击“重试”，确认显示明确失败原因且恢复网络后可手动重试。
6. 使用以下命令保存诊断（不得记录验证码、密码、api_hash、会话或完整消息内容）：

```powershell
adb logcat -c
# 在真机执行一次播放、seek、后台、返回后：
adb logcat -d | Select-String 'AndroidRuntime|FATAL EXCEPTION|StrictMode|NetworkOnMainThread|ExoPlayer|MediaCodec|cvf'
adb shell run-as com.qixuan.channelvideoflow sh -c 'du -sk cache/tdlib/files'
```

验收重点：无 `NetworkOnMainThread`/StrictMode 网络告警、无崩溃、无重复并发 `DownloadFile`、无播放器泄漏；记录首次播放等待时间和 `cache/tdlib/files` 的临时分段缓存增长。

## 未验证项

- 首次 `STATE_READY` 的精确等待毫秒数（debug 诊断标签未出现在本次设备 logcat）。
- 可复现、可量化的中段 seek 恢复时间；本次手势未取得可判定的系统 seek 事件。
- 真实网络中断、后台切换、快速退出后的 TDLib 更新顺序。
- 真机 logcat、首次播放等待时间与临时缓存增长。
- 4 MiB 有界前读加 12 秒 rebuffer 恢复策略后的首次 `STATE_READY` 时间、播放中 rebuffer 次数及与关闭 Clash VPN 后的对照结果。
- API 36 x86_64 emulator UI Proof、Vivo install/launch smoke（本阶段新增播放器后尚未重跑）。

## 2026-07-27 真机播放记录

- V2307A / Android 16 上直接 `adb install -r -t` 安装优化后的 debug APK 成功，冷启动 `MainActivity` 为 623 ms；本次未运行会修改系统设置的 Vivo 测试准备脚本，三项动画缩放均保持 1.0。
- 已索引的真实 `supportsStreaming=true` 视频进入 `PlayerView` 并使用 `c2.qti.avc.decoder` 硬件解码（720×1496、25 fps）；同一播放会话累计 118 秒，音频 `underrun/xrun=0`。
- 播放期间 TDLib app-internal `cache/tdlib/files` 从约 15.7 MB 增至约 47.9 MB；返回频道页后保持 12 秒不再增长，证明已无 owner 时没有继续下载。
- 本次 logcat 未见目标包 `AndroidRuntime`、`NetworkOnMainThread` 或 `StrictMode` 网络违规；上述结果不等价于网络断开、精确首帧等待或 seek 通过。

## 2026-07-27 大文件诊断基线

- 当前选中频道的默认流式样本为 3.56 GiB、1080p、3 小时 18 分。原 1 MiB 前读实现首次 `STATE_READY` 为 49.8 秒，此时 TDLib 已取得约 25.4 MiB。
- 随后的实播出现约 6–7 秒播放、4–5 秒缓冲恢复的循环。TDLib 日志显示连续的 1 MiB 区间请求；没有播放器/解码器重建或目标包崩溃证据。
- 因而将前台单次有界请求升至 4 MiB；该改动需以同一真实视频的安装后复测验证，不能把较大 TDLib 总缓存上限当成首播加速方案。

## 2026-07-27 4 MiB 真机复测（缓冲策略调整前）

- 新 APK 成功直接安装并冷启动；本次未运行任何会改系统设置的脚本，三项动画缩放均为 1.0。
- 从 4 KiB 的 TDLib 私有缓存开始，66 秒内缓存增长至约 51.8 MiB；系统媒体指标确认真实音频输出，但约 32 秒后暂停，说明 4 MiB 区间供给尚未完全消除大文件 rebuffer。
- 通过页面内“返回频道”释放 owner 后，缓存 15 秒不再增长。后续重复试播在 90 秒内持续拉取数据却未获得新的可量化音频开始事件，不能宣称该大文件的首次加载问题已解决。
- 因此新增时间优先的 50–60 秒缓冲与 12 秒 rebuffer 恢复策略；其真机结果尚待验证。
