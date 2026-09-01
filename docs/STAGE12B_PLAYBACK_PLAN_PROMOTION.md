# 阶段 12B：播放计划原子晋升

日期：2026-07-29

状态：已完成；主机 Proof、Compose Path B、Android 13 ARM64 真机保留数据覆盖安装和 12 次真实滑动 A/B 均已通过；另保留一组后续未播放媒体的冷数据压力样本，不将其冒充同条件 A/B

## 1. 阶段合同

### Outcome

用户滑到下一条后不再无条件等待额外 250ms。Pager 能可靠给出目标页时，控制链路提前准备可取消的瞬时 `PlaybackPlan`；目标页确认稳定后，只有 `VideoKey`、质量选择 generation、账号 generation 和播放队列 generation 全部匹配，才以一次原子 compare-and-promote 晋升已经准备的唯一下一条计划。播放器仍只在稳定页绑定。

### Scope

- 深化现有 `VideoPlaybackViewModel`、Compose Pager 信号、单例 `VideoPlayerManager`、唯一下一条预加载控制器和 `CVF-Transition` 指标。
- 新增覆盖固定等待、计划晋升/失效、快速连滑、非流式回退、刷新回退、首帧优先和迟到播放器回调的 Fake 单元测试。
- 更新本阶段文档与 README 当前状态。

### Boundary

- 不进入阶段 12C。
- 不新增播放器、Media3 完整文件缓存、Room/DataStore 持久字段、权限、依赖或网络轮询。
- 不修改 256KiB 唯一下一条预加载预算、移动数据默认禁用预加载、质量偏好、缓存上限、Wi-Fi 或 VPN。
- 不清理账号、Room、DataStore、TDLib 数据库、会话或媒体缓存。
- `PlaybackPlan` 只驻留内存，不保存完整 TDLib 对象、消息正文或媒体字节。

### Failure states

- 目标页改变、快速连续滑动或 generation 变化：取消旧准备任务，旧任务和旧回调不得绑定。
- `VideoKey`、质量选择 generation、账号 generation 或播放队列 generation 不匹配：拒绝晋升并回退正常刷新流程。
- 服务端质量刷新失败或 3 秒超时：安全回退原画。
- `supportsStreaming=false`：不进入播放器。
- 当前项尚无真实首帧：停止唯一下一条的网络预加载；首帧出现后再按原有网络策略恢复。

### Proof

主机 Proof：

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :player:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat lint --rerun-tasks --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Compose Path B：

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :app:compileInstrumentationKotlin --no-daemon --console=plain
.\gradlew.bat :app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.auth.LoginScreenTest" --tests "com.qixuan.channelvideoflow.feature.channels.ChannelSelectionScreenTest" --tests "com.qixuan.channelvideoflow.test.ComposeSmokeTest" --no-daemon --console=plain
.\gradlew.bat :app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.settings.CacheSettingsScreenTest" --no-daemon --console=plain
.\scripts\run-emulator-compose-tests.ps1 -Serial emulator-5554
.\scripts\run-vivo-launch-smoke.ps1 -Serial <device-serial> -SkipBuild
```

真机 A/B：先用 `adb devices -l` 重新识别设备，再以 `adb install -r -t` 覆盖安装并保留现有应用数据，按阶段 12A 的协议完成首次进入样本和 12 次向上滑动。

## 2. 红绿测试

本阶段遵循红—绿—重构：

1. 先增加“稳定页无需推进 250ms 就开始播放”测试；保留旧 `delay(250)` 时失败，删除固定等待后通过。
2. 先增加目标页提前准备和稳定后晋升测试；`onPageTargeted` 尚不存在时编译失败，接入可取消准备与原子槽位后通过。
3. 先增加“当前项首帧前下一条预加载必须让路”测试；旧行为会启动预加载而失败，增加真实首帧门控后通过。
4. 先增加播放器回调 gate 测试；旧代码没有按 bind token 隔离迟到 READY/首帧/错误，加入每次绑定 token 和 listener 后通过。
5. 审阅时先增加 fatal refresh `Error` 测试；原来的 `catch (Throwable)` 会把它静默降级为原画而失败，收窄为 `catch (Exception)` 后通过，同时继续显式重抛 `CancellationException`。
6. 增加同一 `VideoKey` 连续两次 bind 的旧 generation 回调测试；现有 binding token gate 已满足，测试用于补足此前只覆盖不同 key 的证据。

Fake 仅使用合成 `chatId`、`messageId` 和 `fileId`；没有真实 Telegram 账号、手机号、验证码、密码、API ID 或 API Hash。

覆盖结果：

- 稳定页面不再等待 250ms，settle 后立即准备/绑定。
- 快速连滑取消旧 generation，重复 unstable/settled 回调幂等。
- 匹配计划直接晋升；`VideoKey` 不匹配、质量变化、队列重建和账号释放均拒绝旧计划。
- 非流式视频不绑定；可恢复的刷新异常和超时均回退原画，fatal `Error` 不会被静默吞掉。
- 当前项首帧前停止下一条网络预加载，首帧后恢复唯一下一条。
- 迟到的旧 READY、真实首帧和错误回调不能结束当前转场。

## 3. 实现结果

### Pager 与取消边界

Compose 读取 `targetPage`、`settledPage` 和 `isScrollInProgress`。滚动期间只把可靠的 `targetPage` 交给 `onPageTargeted` 做可取消准备；只有 `settledPage` 且 `isScrollInProgress=false` 才调用稳定页绑定。目标改变会取消上一准备任务，重复信号不会创建平行任务或重复绑定。

原来的稳定页 `delay(250)` 和常量已删除，没有以其他延时、debounce、轮询或新调度器替代。每次准备和绑定都校验 generation 与 `VideoKey`。

### PlaybackPlan 与原子晋升

瞬时 `PlaybackPlan` 包含：

- `VideoKey(chatId, messageId)`；
- 原始 fileId、最终 `playbackFileId` 和 `supportsStreaming`；
- 质量偏好/网络选择 generation 与最终原画/服务端版本选择结果；
- 账号 generation 和播放队列 generation；
- 刷新结果与准备时间。

计划槽位使用单个原子引用，只保存当前项和唯一下一条。晋升以 CAS 完成；任一键或 generation 不匹配即拒绝并回退正常刷新。质量偏好/网络选择变化、退出账号、频道筛选变化和队列重建都会立即失效旧计划。

### 当前播放优先

下一条可以提前完成内存/Room 元数据读取、服务端消息刷新和质量选择，但只有当前绑定已经收到真实首帧，才允许现有 `VideoPreloadController` 发起唯一下一条的区间预加载。当前项重新 bind 或首帧缺失时停止下一条网络预加载；当前项的 owner token 和保护租约不受影响。

### 迟到播放器回调

`VideoPlayerManager` 仍只创建一个 ExoPlayer。每次 bind 生成独立 binding token 和 listener；旧 listener 在覆盖时移除，回调还必须通过 token 与 `VideoKey` 双重检查。迟到 READY、首帧和错误不会更新新视频快照，也不会结束新转场。

### 性能日志

继续使用仅 Debug 输出的 `CVF-Transition`。本阶段只增加 `promoted`、`planAgeMs` 和准备阶段刷新结果等脱敏枚举/耗时；没有记录频道名、caption、文件路径、remote id、手机号、凭证或完整 TDLib 对象。Release 仍不输出详细转场指标。

## 4. 修改文件

- `app/src/main/java/com/qixuan/channelvideoflow/feature/video/VideoPlaybackViewModel.kt`
- `app/src/main/java/com/qixuan/channelvideoflow/feature/video/VideoPlaybackScreen.kt`
- `app/src/test/java/com/qixuan/channelvideoflow/feature/video/VideoPlaybackViewModelTest.kt`
- `player/src/main/java/com/qixuan/channelvideoflow/player/VideoPlaybackController.kt`
- `player/src/main/java/com/qixuan/channelvideoflow/player/VideoPlayerManager.kt`
- `player/src/main/java/com/qixuan/channelvideoflow/player/PlaybackTransitionMetrics.kt`
- `player/src/test/java/com/qixuan/channelvideoflow/player/PlaybackCallbackGateTest.kt`
- `player/src/test/java/com/qixuan/channelvideoflow/player/PlaybackTransitionMetricsTest.kt`
- `player/src/test/java/com/qixuan/channelvideoflow/player/VideoPreloadManagerTest.kt`
- `docs/STAGE12B_PLAYBACK_PLAN_PROMOTION.md`
- `README.md`

## 5. 主机与 Compose Proof

| Proof | 结果 |
|---|---|
| `:player:testDebugUnitTest` | 通过 |
| `:app:testDebugUnitTest` | 通过 |
| fresh `test --rerun-tasks` | 通过，345/345 tasks |
| fresh `lint --rerun-tasks` | 通过，220/220 tasks；app debug/release 各为 0 errors/15 warnings、instrumentation 为 0 errors/16 warnings，core database 与 tdlib 各为 0 errors/1 warning，player/telegram 为 No issues；另有既有 storage broadcast deprecated 与 `libtdjni.so` strip 控制台警告 |
| `assembleDebug` | 通过，190 tasks |
| `:app:compileInstrumentationKotlin` | 通过 |
| 第一组 Robolectric-Compose | 通过 |
| 第二组 Robolectric-Compose | 通过 |
| API 36 AOSP x86_64 emulator Compose UI | `EMULATOR_COMPOSE_RESULT=PASS` |
| Android 13 ARM64 实体机 install+launch smoke | `VIVO_LAUNCH_SMOKE_RESULT=PASS`（脚本名沿用仓库 Path B；本次连接设备厂商为 Xiaomi） |

Vivo/OriginOS 完整 instrumentation 按 Path B 不作为通过条件，本阶段尚未验证且未重复运行。API 36 模拟器第一次执行时 instrumentation 进程的 `FinalizerWatchdogDaemon` 超时并引发 emulator system crash；冷重启 AVD 后同一仓库脚本完整通过，未修改业务代码来规避。最终 Debug APK 为 44,519,870 bytes，SHA-256 为 `E7690E44278D323CC031A68BF0596B7F3EEEF1469FE626DAF7E122071D59DDC8`；真机安装后的 `base.apk` 哈希与之相同。

## 6. 真机 A/B

测试条件：

- `adb devices -l` 当次识别到 Android 13 ARM64 实体机 `<device-serial>` 和 API 36 AOSP x86_64 模拟器 `emulator-5554`，没有使用过期 serial。
- 真机以 `adb install -r -t` 覆盖安装；已有账号、Room、DataStore 和 TDLib 缓存保留。
- 沿用阶段 12A 的暖/混合缓存状态；未修改 Wi-Fi、VPN、缓存上限、质量设置或移动数据预加载策略。
- 首次进入得到 1 个独立首帧样本，随后执行 12 次向上滑动；表中分位数只统计 12 次手势样本，使用 nearest-rank。

终态结果：

- `FIRST_FRAME`：13（首次进入 1 + 滑动 12）。
- 12 次手势：`FIRST_FRAME=12`、`FAILED=0`、`SUPERSEDED=0`，计划原子晋升 `12/12`。
- 12/12 均在采样上限内到达真实首帧。
- 采样窗口内目标包 crash/ANR 为 0，非零 rebuffer 会话汇总为 0。
- 没有观察到错误视频绑定、第二播放器或第二份 Media3 完整文件缓存。

| 指标 | 12A P50 | 12B P50 | 12A P90 | 12B P90 | 12A 最大 | 12B 最大 |
|---|---:|---:|---:|---:|---:|---:|
| gesture → settle | 620ms | 603ms | 628ms | 612ms | 628ms | 612ms |
| settle → plan | 250ms | 0ms | 251ms | 0ms | 251ms | 0ms |
| bind → first frame | 212ms | 212ms | 853ms | 240ms | 1243ms | 241ms |
| gesture → first frame | 1089ms | 818ms | 1737ms | 838ms | 2115ms | 844ms |

验收结论：

- 固定 `settle→plan` 250ms 已消失。
- `gesture→first frame` P50 降低 271ms（约 24.9%），达到至少降低 200ms 的目标。
- P90 降低 899ms，没有高于阶段 12A 的 1737ms。
- 12/12 到达首帧，无新增 rebuffer、目标包崩溃或错误视频绑定。

原始脱敏证据位于：

- `build/reports/stage12b-acceptance-transition.log`
- `build/reports/stage12b-acceptance-telemetry.log`
- `build/reports/stage12b-acceptance-crash-check.log`

## 7. 失败尝试

第一次自动滑动使用了 250ms 的输入手势，导致 `gesture→settle` P50 约 711ms，明显不等价于阶段 12A 的 P50 620ms。该轮虽然 12/12 首帧、`settle→plan` 为 0ms 且全部晋升，但因手势协议漂移不纳入最终 A/B。证据保留在：

- `build/reports/stage12b-transition.log`
- `build/reports/stage12b-run1-full-logcat.log`

输入手势随后调整到与 12A `gesture→settle` 分布相符的 150ms。审阅后的最终 APK 再按“首次进入 + 顶部序列 12 次滑动”复测，`gesture→settle` P50 603ms、P90 612ms；没有为改善数字修改网络、缓存、质量或播放器参数。

为排除“完全由重复热缓存造成”的错误归因，又先越过已反复播放的顶部 12 条，再对后续 12 条执行一次单程压力采样：

- `FIRST_FRAME=12`、`FAILED=0`、`SUPERSEDED=0`、晋升 `12/12`；
- `settle→plan` P50 0ms、P90 1ms，控制链路仍无固定 250ms；
- `gesture→first frame` P50 3795ms、P90 10102ms、最大 13137ms，不满足 12A 的绝对性能门槛，其中两条超过 10 秒采样上限；
- `CVF-TdFile` 显示首个 256KiB 区间等待最高 12334ms，播放器 `bind→first frame` 同步出现长尾；12 个会话的 rebuffer 都为 0，目标包 crash 为 0；
- 因媒体集合与缓存条件均不同，这组数据不能与 12A 顶部序列做 A/B，也不用于宣称 12B 性能通过。它定位的是保留缓存条件下新媒体的 TDLib/网络首区间长尾，不是 `PlaybackPlan` 晋升、generation gate 或 settle 后调度回退。本阶段按合同停止扩展，不调整 256KiB 预算或进入 12C。

压力样本的脱敏证据位于：

- `build/reports/stage12b-single-pass-transition.log`
- `build/reports/stage12b-single-pass-telemetry.log`

## 8. 安全检查与遗留问题

安全检查：

- 构建 APK 有效权限只有 `INTERNET` 和 `ACCESS_NETWORK_STATE`。
- `allowBackup=false`，且 `dataExtractionRules` 与 `fullBackupContent` 都保留。
- 未新增公共存储路径、广泛存储权限、第二播放器或 Media3 完整文件缓存。
- `local.properties` 仍被 Git 忽略；源码、测试和文档凭证模式扫描为 0。
- 未清理账号、Room、DataStore、TDLib 数据库、会话或媒体缓存。
- 未暂存、提交或推送。

遗留问题：

- 正式 A/B 是单台 Android 13 ARM64 真机上的暖/混合缓存顶部序列，且保留了 12A 之后的缓存；它能验证本机当前状态下的阶段门槛，但不能单独证明严格冷缓存的因果收益。严格冷缓存、移动数据和更多设备分布尚未验证。
- 后续新媒体压力样本暴露 TDLib 首区间网络等待长尾并超过 12A P90；由于媒体与缓存条件不同，不能判定为 12B 回退，也没有在本阶段扩大预加载预算。
- 网络字节级流量没有单独抓包，尚未验证；代码仍复用原有唯一下一条 256KiB 预算和移动数据策略，没有新增下载通道。
- 阶段 12C 尚未开始。
