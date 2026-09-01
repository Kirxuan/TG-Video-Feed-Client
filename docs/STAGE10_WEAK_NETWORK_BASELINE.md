# 优化阶段 10：弱网真机基线与播放观测

日期：2026-07-29
状态：实现、目标单元测试、完整主机 Proof、Android 13 ARM64 真机安装和真实流式播放基线通过；高码率大文件、seek 与断网恢复尚未验证

## 阶段合同

- Outcome：在不改变播放、预加载和缓存参数的前提下，从同一真实播放会话得到首次 READY、bufferedPosition、缓冲前瞻、区间供给速率、rebuffer 次数/时长和释放后的缓存停止增长证据。
- Scope：`player` 模块的 Debug 播放会话指标、对应 JVM 单元测试、真机诊断和阶段文档。
- Boundary：不改变当前 4 MiB 有界前读、下一条 256 KiB 预加载、50–60 秒缓冲、2.5 秒首播门槛、12 秒 rebuffer 恢复门槛、500MB 缓存、唯一 ExoPlayer、账号、网络/VPN、权限或存储位置；不清理用户缓存。
- Failure states：设备或 OEM 阻止启动；无已索引 `supportsStreaming=true` 视频；样本已有缓存使严格冷启动口径失效；日志不足以区分初始加载、seek、暂停和真实 rebuffer。
- Proof：指标目标单元测试与播放器编译；fresh `test`、`lint`、`assembleDebug`；保留数据覆盖安装；真机至少 90 秒真实播放日志和返回后的缓存稳定检查。

## 实现

### rebuffer 判定

`PlaybackSessionMetrics` 只保存当前绑定会话的计数和单调时钟：

- 首次 READY 前的 BUFFERING 是初始加载，不计 rebuffer。
- 首次 READY 后，只有仍期望自动播放、不是显式 seek 的 BUFFERING 才计 rebuffer。
- 重复 BUFFERING 回调不会重复计数。
- READY 恢复时记录本次恢复毫秒并累计总时长。
- reset 会清空会话；不保存 fileId、频道、消息或视频文本。

`VideoPlayerManager` 在 Debug 中记录：

- 播放状态变化；
- 每 5 秒一次的 position、bufferedPosition、ahead、rebuffer 次数和时长；
- 释放绑定时的首次 READY、最终位置、缓冲前瞻和 rebuffer 汇总。

Release 继续关闭这些详细日志，指标不写入 Room、DataStore 或文件。

## 自动化 Proof

- `:player:testDebugUnitTest --tests "com.qixuan.channelvideoflow.player.PlaybackSessionMetricsTest" :player:compileDebugKotlin`：通过。
- Fresh `test --rerun-tasks`：通过，345 个 Gradle task 全部执行；68 个 XML 共 416 次测试执行，0 failure、0 error、0 skipped。
- Fresh `lint --rerun-tasks`：通过，220 个 task 全部执行；只有既有 storage broadcast 弃用和固定 native 库未剥离提示。
- Fresh `assembleDebug --rerun-tasks`：通过，190 个 task 全部执行。
- 第一次 fresh `test` 尝试被外层 120 秒命令窗口中止，未出现测试失败且无残留 Gradle 进程；扩大执行窗口后同一命令完整通过。

## 真机环境

- 设备：`21091116UC`。
- Android：13 / API 33。
- ABI：`arm64-v8a`。
- 安装：`adb install -r -t` 保留现有账号、Room、DataStore 和缓存，成功。
- 网络：保留用户现有 Wi‑Fi 与 VPN；未开关、未限速、未修改系统策略。
- 起始 TDLib 私有媒体目录：42,467 KiB。
- 未清缓存，因此这是正常产品状态基线，不是严格空缓存实验。

## 观测校验样本

先用约 20.36 MB / 60 秒的已缓存短视频确认指标链路：

- 首次 READY：331 ms。
- READY 时本地可见字节：20,295,183。
- 完整 bufferedPosition：59.792 秒。
- 播放正常到 ENDED，rebuffer 0，目标包错误 0。

该样本只能证明观测正确，不能证明弱网性能。

## 真实流式基线

为避免把已缓存短视频当成弱网结果，随后使用索引近端的普通 `messageVideo`：

- `supportsStreaming=true`。
- 文件大小：64,660,140 bytes，约 61.7 MiB。
- 时长：1,310 秒，即 21 分 50 秒。
- 分辨率：1920×1080。
- 平均媒体字节率：约 48.2 KiB/s。

快速到达目标页会按正常产品逻辑触发少量中间预加载，因此本次不宣称严格冷缓存。目标绑定后的可判定结果：

- 首次 READY：1,700 ms。
- READY 时本地可见字节：4,194,304。
- 首次 READY 的 bufferedPosition：26.240 秒。
- 采集到 42 个 5 秒样本。
- 返回前连续播放位置：209.966 秒。
- 返回前 bufferedPosition：267.029 秒。
- 返回前缓冲前瞻：57.063 秒。
- rebuffer：0 次，累计 0 ms。
- `range timeout`、播放器 error、目标包 FATAL、`NetworkOnMainThread`：0。

### 区间供给

目标会话读取 53 个 256 KiB DataSource 区间，共 13.25 MiB。用于排除内存/磁盘命中的连续 4 MiB 边界时间：

- 4 MiB 边界：14:08:49.726。
- 8 MiB 边界：14:10:18.412。
- 12 MiB 边界：14:11:45.517。
- 4–12 MiB 合计 8 MiB / 175.791 秒，约 46.6 KiB/s。

该稳态供给略低于样本约 48.2 KiB/s 的平均媒体字节率，但初始前读和 50–60 秒时间缓冲把播放前瞻维持在约 52–70 秒，209.97 秒内没有 rebuffer。

### 缓存与释放

- 快速导航并绑定目标后：50,923 KiB。
- 返回频道页前：59,127 KiB。
- 返回频道页 15 秒后：59,127 KiB。
- 释放汇总：position 209.966 秒、bufferedPosition 267.029 秒、ahead 57.063 秒、rebuffer 0。

这证明页面返回释放当前 owner 后没有继续下载；视频字节仍只位于 app-private TDLib 缓存。

## Debug APK

- 路径：`E:\Telegram Android Developer\app\build\outputs\apk\debug\app-debug.apk`
- 大小：44,317,801 bytes
- SHA-256：`BD60281F735598C0232C1870B46240E97E03F40F189868BA8010C7B1A356EBE5`
- 权限：仅 `android.permission.INTERNET` 与 `android.permission.ACCESS_NETWORK_STATE`

## 已知限制

- 阶段 5 使用的 3.56 GiB / 3 小时 18 分高码率样本不在当前索引近端；本阶段没有用数百次滑动污染大量预加载，因此同一高码率样本对照尚未验证。
- seek、真实断网/恢复、后台/前台恢复和发生 rebuffer 后的 12 秒恢复门槛尚未在本阶段人工执行。
- `effectiveKiBps=0` 表示等待为 0 ms 的缓存命中，不能当成网络零速；稳态网络供给使用跨 4 MiB 边界的墙钟时间计算。
- 短视频 ENDED 后，现有 250 ms UI 进度 ticker 会继续运行到页面释放；这是已观察到的轻量后台工作，尚未在本阶段修改。

## 下一阶段建议

不要仅凭本样本扩大前读或缓存。优先单独评审“当前项优先”的预加载门控：当前仍在 Loading、发生 BUFFERING 或缓冲前瞻低于下限时释放下一条 256 KiB owner；当前 READY 且缓冲前瞻恢复到上限后再允许下一条预加载，并用迟滞避免反复启停。该策略需要高码率样本 A/B 证明后再保留。
