# 优化阶段 9：弱网基础正确性交接

日期：2026-07-29
状态：实现、目标单元测试、完整主机 Proof 和 Android 13 ARM64 真机安装通过；真实弱网性能对照尚未验证

## 阶段合同

- Outcome：Media3 在 TDLib 已知文件大小时获得准确剩余长度；慢速但持续推进的区间不会被固定 15 秒总截止误杀；完全停滞和无限慢下载仍有明确上限。
- Scope：`TelegramMediaDataSource`、`TelegramFileManager`、对应 JVM 单元测试和阶段文档。
- Boundary：不改变下一条 256 KiB 预加载、当前 4 MiB 有界前读、50–60 秒缓冲、2.5 秒首播门槛、12 秒 rebuffer 恢复门槛、500MB 缓存、唯一 ExoPlayer、权限或 UI。
- Failure states：文件总大小未知继续返回 `LENGTH_UNSET`；相关连续区间 15 秒无增长时报超时；持续增长也不超过 90 秒；owner 释放立即取消等待。
- Proof：两个模块的目标单元测试；fresh `test`、`lint`、`assembleDebug`；有授权真机时执行安装。首帧和 rebuffer 改善必须由相同真实视频的弱网对照单独证明。

## 实际修改

### Media3 已知长度

`TelegramMediaDataSource.open()` 在 `DataSpec.length` 未知时使用等待成功后的 TDLib `file.size`：

- `file.size > 0`：返回 `file.size - DataSpec.position`。
- `file.size` 仍未知：保持 `C.LENGTH_UNSET`。
- 显式 DataSpec.length：仍原样返回请求长度。

这不会读取或复制完整文件，只向 Media3 提供已经掌握的资源长度。

### 进度感知等待

`TelegramFileManager` 只把当前请求起点之后、由 `downloadOffset + downloadedPrefixSize` 证明连续可读的增长视为相关进度：

- stall 窗口：15 秒，由 DataSource 现有 timeout 参数提供。
- 相关进度增长：刷新 stall 窗口。
- 相同进度或无关区间更新：不刷新。
- hard limit：6 个 stall 窗口，即默认 90 秒。
- owner 释放：在移除范围 owner 时 notify 等待线程，等待方立即得到已取消结果。

等待仍位于 Media3 Loader 线程；没有主线程阻塞、无限重试或不可取消等待。

### 脱敏诊断

Debug `CVF-TdFile` 范围完成/超时记录只包含：

- fileId；
- offset、length；
- waitMs；
- progressBytes；
- effectiveKiBps；
- `NO_PROGRESS` 或 `HARD_LIMIT`。

不记录 TDLib 私有路径、消息正文、手机号、验证码、密码、API Hash、会话或完整 TDLib 对象；Release 不输出这些 Debug 日志。

## 回归测试

- 已知大小的无界请求返回从 position 起的精确剩余长度。
- 未知大小继续返回 `LENGTH_UNSET`。
- 300ms 一次的相关进度可跨越原 500ms 总截止并最终成功。
- 相同进度不刷新 stall。
- 持续进展不能越过 6 倍 hard limit。
- close/owner release 唤醒等待线程并返回取消。

## 当前验证

- `:player:testDebugUnitTest --tests "com.qixuan.channelvideoflow.player.TelegramMediaDataSourceTest"`：通过。
- `:telegram:testDebugUnitTest --tests "com.qixuan.channelvideoflow.telegram.media.TelegramFileManagerTest"`：通过。
- Fresh `test --rerun-tasks`：通过，345 个 task 全部执行；XML 共 410 次测试执行，0 failure、0 error、0 skipped。
- Fresh `lint --rerun-tasks`：通过，220 个 task 全部执行；仅有既有 storage broadcast 弃用警告和固定 native 库未剥离提示。
- Fresh `assembleDebug --rerun-tasks`：通过，190 个 task 全部执行。
- 真机安装：通过；`21091116UC`、Android 13/API 33、ARM64，`:app:installDebug` 安装到 1 台设备。
- 相同 3.56 GiB / 3 小时 18 分样本的首帧、seek、rebuffer 和有效吞吐对照：尚未验证。

## Debug APK

- 路径：`E:\Telegram Android Developer\app\build\outputs\apk\debug\app-debug.apk`
- 大小：44,317,801 bytes
- SHA-256：`B05C7752A3B7575C3659AC8E5052000AA95E68BC1843BF3E71D85D0B1E19731E`
- 权限：仅 `android.permission.INTERNET` 与 `android.permission.ACCESS_NETWORK_STATE`

## 后续候选阶段

在获得同一真实视频的 `range ready`、首次 `STATE_READY`、bufferedPosition 和 rebuffer 数据后，再单独评审吞吐感知策略：当前缓冲不足时暂停下一条预加载、按可播放秒数调整下一条目标，以及对当前顺序前读做有界 4/8/16 MiB 调节。本阶段不提前实现这些策略。
