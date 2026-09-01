# 优化阶段 12E：Pager 手势链路、Compose 重组、PlayerView/Surface 稳定性与视觉连续性

状态：已完成。12E 代码、主机 Proof、Compose Path B、自动化真机协议和仓库所有者人工视觉验收均已通过；阶段 12A、12B、12C、12D 的文档和通过证据均已确认后才开始本阶段；未进入 12F。指定 iQOO 12 / OriginOS 6 / Android 16 机型验证尚未验证。

## 1. 阶段合同

### Outcome

在维持正确 Pager 手势、唯一主要 ExoPlayer、唯一活动 PlayerView 绑定和既有内容保护的前提下：

- 把手势开始、手指释放、目标页可靠确定、Pager settle 和真实首帧分开观测；
- 让可取消 PlaybackPlan prepare 与 Pager 动画重叠；
- 防止 target 抖动、迟到回调或重组导致错误页面绑定、串音和 Surface 抖动；
- 让 250ms position ticker 只更新进度叶节点，不重组 Pager 或重复 attach；
- 首帧前显示与 VideoKey 对齐的不透明合法占位，不显示上一条最后一帧。

### Scope

- `VideoPlaybackScreen.kt`：Pager signal、空间 hysteresis、稳定 pointer observer、稳定页面 key、单一 AndroidView、首帧占位和进度叶节点。
- `VideoPlaybackUiState.kt`：独立的播放器进度 UI 状态。
- `VideoPlaybackViewModel.kt`：手势 generation、target prepare/cancel、settle 仲裁、结构状态与高频进度分流。
- `VideoPlaybackController.kt`、`PlaybackTransitionMetrics.kt`：release/target/plan/settle/first-frame 分段事件与 Debug 汇总。
- `ReusablePlayerLifecycle.kt`、`VideoPlayerManager.kt`：稳定 PlayerView 绑定、幂等 attach、显式 detach 和 surface 计数。
- 相关 player、ViewModel 和 Compose 测试。
- `README.md` 与本文档。

### Boundary

- 未修改 TDLib 区间调度、Media3 版本、LoadControl、质量选择、业务筛选、随机队列、Room、DataStore 或缓存上限。
- 未新增网络下载、缩略图下载、完整文件缓存、公共文件、权限或非稳定依赖。
- 未创建每页 PlayerView/ExoPlayer；UI 仍不直接访问 TDLib、DAO 或创建播放器。
- 未降低 `FLAG_SECURE` 或受保护内容策略，未截图缓存视频帧。
- 未进入 12F，未暂存、提交或推送。

### Failure states

- `supportsStreaming=false`：保持“不支持流式播放”占位，不绑定播放器。
- Ready 但真实首帧未到：保持当前 VideoKey 的不透明 loading，占位不显示上一条帧或错误标题。
- Failed/Unsupported/Loading：状态与回调 VideoKey 不匹配时不能覆盖当前页。
- 快速改变方向：旧 target plan 按 gesture generation 取消，settled page 才能最终 bind/发声。
- 页面离开或完整 release：只 detach 当前活动 PlayerView，旧 view 的迟到 detach 不影响新 view。
- Activity 同进程重建：旧 AndroidView 必须先 `onRelease`/detach，新 Activity 才能 attach 替代视图，任意时刻活动绑定上限为 1；进程重建后仍只有一个活动播放器。

### Proof

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :player:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat lint --rerun-tasks --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

并严格执行 `AGENTS.md` Compose Path B。

## 2. 前置条件与 12D 基线

开始前完整阅读了 `AGENTS.md`、`README.md`、阶段 12A、12B、12C、12D 文档。四个前置阶段都有完成状态、主机 Proof、Compose Path B 和真机证据，因此允许开始 12E。

12D 最新合并基线：

| 指标 | P50 | P90 | max |
|---|---:|---:|---:|
| gesture→first frame | 694ms | 839ms | 857ms |
| bind→first frame | 89ms | 226ms | 252ms |

12D 为 24/24 首帧、`surfaceAttachCount=1`、`playerInstances=1`，首帧后 30/60 秒窗口为 0 rebuffer。12A 的约 620ms gesture→settle 混合了用户手指仍按住的时间和 Pager 动画，不能整体视作程序延迟。

本阶段修改前在同一台 Android 13 ARM64 设备、相同 12 次滑动协议下另采 gfxinfo：1043 帧、现代 jank 174 帧（16.68%），P50/P90/P95/P99 为 5/20/36/57ms。该结果只用于本阶段同设备 A/B。

## 3. 实际链路证据与修复

### 3.1 可靠分段

`PlaybackTransitionEvent` 新增：

- `GestureStarted(observedAtMillis)`；
- `GestureReleased(observedAtMillis)`；
- `TargetKnown(VideoKey)` / `TargetAbandoned`；
- `PlanPreparationStarted(VideoKey)` / `PlanPrepared(VideoKey)`；
- 原有 `PageSettled`、bind、prepare、READY、first frame。

Debug summary 现在分别输出：

- gesture→release：用户直接输入时间；
- release→settle：Pager 动画/吸附时间；
- target-known→plan-ready 与 plan-ready→settle：提前准备和动画重叠；
- release→first-frame 与 target-known→first-frame：本阶段主要体验指标；
- bind→first-frame 与 settle→first-frame：播放器准备部分。

release 来自真实 pointer up；没有使用固定延时估算。pointer observer 使用 `rememberUpdatedState` 和固定 `pointerInput(Unit)`，播放器状态重组不会重启正在进行的手势协程。

### 3.2 两个由真机证据发现的 Pager 边界

第一次实现后，每次正常滑动出现 `SUPERSEDED + FIRST_FRAME` 两条 transition。日志证明 release/fling 交接期间 `targetPage` 会瞬时回到当前页，旧实现误判为用户反向。修复后：

- 手指仍按住时，方向变化立即按新 generation 处理；
- release 后的预测 target 回摆不立即取消；
- 最终 `settledPage` 是 release 后回当前页的权威仲裁；
- 新 pointer 手势打断 fling 时立即开始新 generation 并取消旧准备。

第二次真机慢拖约屏幕高度 11%、最终未跨页时，Compose 的预测 `targetPage` 曾短暂指向下一页。为避免无意义 refresh/prepare，最终采用空间而非时间 hysteresis：只有 `currentPage` 跨过 Pager snap 中点才成为 committed target。结果：

- 轻拖不跨页：`UNCHANGED`，`targetKnown=null`，无 prepare、bind 或额外 attach；
- 正常滑动：committed target 仍比 settle 早约 433ms，保留动画重叠；
- 反向和新手势：继续按 VideoKey + generation 去重/取消。

### 3.3 单播放器和静音边界

- page unstable 立即 `pauseForPageTransition`，settle 前错误页不能发声。
- prepare 只产生瞬时 PlaybackPlan；settled page 才能晋升计划并 bind 共享播放器。
- 旧 target 的 refresh/plan job、preload 和 token 在方向变化时取消。
- 回到仍然绑定的当前页时复用该绑定并 resume，不清空/重取同一计划。

## 4. PlayerView / Surface 稳定性

- PlayerView 位于 `VerticalPager` 外层，同一播放页会话只创建一个 AndroidView。
- 只要 feed 中仍有可流式项目，unsupported/failed/loading 页面切换不会销毁承载视图。
- `StablePlayerViewBinding` 保证同 view/player 重复 attach 为 no-op；替换 view 时先 detach 旧 view；旧 view 的迟到 detach 被忽略。
- `AndroidView.onRelease` 只 detach 自己；页面 ViewModel 的 release 继续释放绑定和 engine。
- 普通 12 次滑动的干净进程会话始终为 `surfaceAttachCount=1`、`surfaceDetachCount=0`、`playerInstances=1`。
- 快速 10 次滑动期间 attach/detach 增量均为 0。
- 播放页↔频道页 5 次往返再最终返回频道后，累计 attach/detach 为 6/6；每次进入恰好 attach 一次，每次离开恰好 detach 一次。

页面 key 使用完整 `chatId:messageId`；随机虚拟 pager 额外包含 pagerPage，既保持 VideoKey 身份又避免虚拟重复页 key 冲突。没有使用 messageId 单独作 key。

## 5. Compose 重组证据

- ViewModel 把高频 position/duration/bufferedPosition/isSeekable 分流到独立 `StateFlow<VideoPlaybackProgressUiState>`。
- 结构 `VideoPlaybackUiState` 只在播放状态、key、暂停、静音、首帧、错误等展示字段变化时更新；ticker 更新保持同一个结构状态对象。
- 只有 `PlaybackProgressState` 叶 composable 使用 `collectAsStateWithLifecycle` 收集进度，并以 VideoKey 拒绝旧进度。
- Compose 测试连续更新 12 次 position，Pager `SideEffect` 计数不变、PlayerView attach 仍为 1。
- 页面内容、占位和回调都以 VideoKey 对齐；READY 但未首帧、或首帧来自旧 key 时仍显示不透明 loading。

本次无可用的交互式 Layout Inspector/Perfetto Compose trace 会话；按任务允许的现有工具路径，使用确定性的重组计数测试、Android UI tree、SurfaceFlinger 列表和 `dumpsys gfxinfo`。未凭感觉大改 UI。

## 6. 测试覆盖

新增或扩展的测试覆盖：

1. target 改变触发计划准备与晋升；
2. 轻拖未过 snap 中点不发布预测下一页，最终不 prepare/bind；
3. 快速 target 改变、release 抖动和新手势 generation 取消旧准备；
4. settled page 才最终 bind，unstable 立即暂停旧音频；
5. StablePlayerViewBinding 永远不同时绑定两个 view；
6. 重组和重复 attach 为 no-op；
7. 页面身份为 chatId + messageId；
8. position ticker 不重建 Pager；
9. 旧 bind 的 READY、first-frame、error 不能完成当前 transition；
10. loading/unsupported/failure/首帧占位与 VideoKey 对齐；
11. 返回频道页调用 detach/release；
12. `ActivityScenario.recreate()` 后旧 PlayerView 先 detach、新 PlayerView 再 attach，活动绑定上限为 1；进程重建后重新进入播放页只创建一个 player；
13. Compose UI 覆盖快速滑动、unsupported 往返、返回和 snap-midpoint 慢拖。

关键红灯包括：缺少分段事件、ticker 改变结构状态、AndroidView 重复绑定、非 saveable Pager key、release 后 target 回摆拆分 transition、预测 target 在轻拖时提前 prepare；均在实现后变绿。

## 7. 最终 12 次正常滑动

设备：`21091116UC`，Android 13，`arm64-v8a`，ADB 状态 `device`。使用 `install -r -t` 保留账号和应用数据。测试页、返回、播放控制坐标均由 UI tree bounds 计算；受 `FLAG_SECURE` 保护的播放内容未截图。

干净进程会话结果：13 条 `FIRST_FRAME`（初始 1 + 滑动 12），0 `SUPERSEDED`，0 failed/unsupported outcome，0 rebuffer，0 TIMEOUT，0 crash。

| 分段指标 | P50 | P90 | max |
|---|---:|---:|---:|
| gesture→release（用户输入） | 153ms | 157ms | 157ms |
| release→settle（Pager 动画） | 478ms | 479ms | 480ms |
| gesture→target-known | 198ms | 203ms | 205ms |
| target-known→settle | 433ms | 435ms | 437ms |
| target-known→plan-ready | 0ms | 0ms | 0ms |
| plan-ready→settle | 433ms | 435ms | 437ms |
| bind→first-frame | 88ms | 229ms | 259ms |
| release→first-frame | 565ms | 708ms | 738ms |
| target-known→first-frame | 521ms | 662ms | 689ms |
| gesture→first-frame（兼容旧口径） | 719ms | 865ms | 889ms |

解释：

- 约 153ms 是脚本模拟手指仍按住的输入时间，不再算作程序延迟。
- committed target 在 settle 前约 433ms 已知，plan-ready 在该边界为 0ms，准备与动画充分重叠。
- 相对 12D 的 bind→first-frame 89/226/252ms，最终为 88/229/259ms：P50 改善 1ms，P90/max 分别波动 +3/+7ms，未观察到有意义的播放器启动回退。
- 旧 gesture→first-frame 口径为 719/865/889ms，较 12D 合并基线增加 25/26/32ms；该口径包含本轮 153ms 输入和约 478ms Pager 动画，因此不再作为主要性能目标。release/target 指标在 12D 不存在，无法做前后同口径比较。

## 8. jank 与异常手势

### 同协议 gfxinfo

| 样本 | 总帧 | 现代 jank | legacy jank | P50/P90/P95/P99 |
|---|---:|---:|---:|---|
| 修改前 12 次 | 1043 | 174（16.68%） | 236（22.63%） | 5/20/36/57ms |
| 最终 12 次 | 982 | 88（8.96%） | 119（12.12%） | 5/11/23/57ms |
| 最终快速 10 次 | 358 | 27（7.54%） | 47（13.13%） | 5/12/16/29ms |

同协议现代 jank 降低 7.72 个百分点；未增加。

### 异常协议

- 慢拖不跨页：1 条 `UNCHANGED`；无 target、prepare、bind、attach 或 crash。
- 跨页后立即反向：旧 target 为 `SUPERSEDED`，最终当前页为 `UNCHANGED`；无首帧错配或 crash。
- 连续快速 10 次：18 个被 generation 取代的 transition，只有最终 key 出现 1 次 quality/bind 信号和 1 次 `FIRST_FRAME`；无额外 surface attach/detach、rebuffer、TIMEOUT 或 crash。
- 播放页↔频道页 5 次：每次成对 attach/detach；最终返回频道后 6/6。
- 暂停/继续：UI tree 从“暂停视频”变为“继续播放”，恢复后回到“暂停视频”。
- seek：日志出现 `CURRENT_SEEK→CURRENT_CONTINUATION` owner 转换，随后 BUFFERING→READY，rebuffer 仍为 0，无 crash。
- 后台 `am kill`：PID `2750→4154`，数据保留；新进程进入播放页后 `surfaceAttachCount=1`、`playerInstances=1`、首帧成功。

设备接受横/竖屏命令但 Manifest 的 `configChanges` 使 Activity token 不变，这是当前生产生命周期策略；Xiaomi ROM 也未按临时 `always_finish_activities=1` 销毁 Activity。两项设备设置均恢复原值（rotation=`free`、always_finish=0）。随后在 API 36 AOSP x86_64 instrumentation 中用 `ActivityScenario.recreate()` 强制执行同进程重建：首次 attach=1，旧 Activity 销毁后 detach=1/active=0，新 Activity 挂载后 attach=2/active=1，场景关闭后 detach=2/active=0，全程 `maxActiveBindings=1` 且两个 PlayerView 实例不同。物理机生产旋转继续遵守 `configChanges`，未伪造一次不属于该策略的旋转重建；进程重建、Compose onRelease、ViewModel release 和新进程单播放器也已验证。

## 9. Compose Path B 与主机 Proof

最终代码执行结果：

- `:app:testDebugUnitTest`：通过，75 tests，0 failure/error/skipped。
- `:player:testDebugUnitTest`：通过，52 tests，0 failure/error/skipped。
- `test --rerun-tasks`：通过，345/345 Gradle tasks executed。
- `lint --rerun-tasks`：通过，220/220 tasks executed。
- `assembleDebug`：通过。
- `:app:compileInstrumentationKotlin`：通过。
- Login/ChannelSelection/ComposeSmoke Robolectric 组合：通过。
- CacheSettings Robolectric：通过。
- `VideoPlaybackScreenTest`（含本阶段测试）：通过。
- API 36 AOSP x86_64 emulator Compose UI：最终 38 tests 通过，含 `ActivityScenario.recreate()` 的 PlayerView detach/reattach 上限测试，`EMULATOR_COMPOSE_RESULT=PASS`。
- 物理机 install+launch smoke：`VIVO_LAUNCH_SMOKE_RESULT=PASS`，MainActivity resumed/top，目标包无 crash。

一次早期 emulator 执行在第 25 项时出现 emulator `system_server` 崩溃，`am instrument` 返回 `DeadObjectException`；目标包无 FATAL，crash buffer 是 SystemUI/Phone 的 `DeadSystemException`。system_server 自动恢复后，同一脚本完整通过。新增 recreate 测试后的第一次完整 38 项执行在设备日志中已经是 `38 tests, 0 failed`，但该类位于 CacheSettings 前面时 AndroidJUnitRunner 的结束握手没有返回给 `adb`，外层 180 秒超时；将 recreate 类移到默认类清单最后后，同一 38 项套件在 193.303 秒内输出 `OK (38 tests)` 并由脚本正式判定 PASS。另一次 lint 首跑因失联的旧 Gradle 进程锁住 TDLib `R.jar` 失败；核验并终止该孤儿进程后，相同 lint 通过。以上均保留为诊断证据，没有跳过测试或降低规则。

本机物理设备不是 AGENTS.md 中曾出现问题的 iQOO 12 / OriginOS 6 / Android 16；指定 Vivo/iQOO 机型验证尚未验证。按 Path B 只执行 install+launch smoke，没有用 Vivo 完整 instrumentation 代替 emulator 组合。

## 10. 安全与边界审计

- 最终 APK 权限只有 `INTERNET` 和 `ACCESS_NETWORK_STATE`。
- `allowBackup=false`、两套备份排除规则和 `usesCleartextTraffic=false` 保持。
- `FLAG_SECURE` 路径未降低；未截图或缓存受保护视频帧。
- `local.properties` 仍被 `.gitignore` 忽略；文档、源码、测试和日志未加入真实 API ID/hash、手机号、验证码或密码。
- 未新增依赖或 alpha/beta/RC/snapshot 版本。
- 未修改 TDLib/Room/缓存/质量/筛选/随机队列语义。
- 未暂存、提交或推送；仓库仍是 `main` 且原有工作树整体未跟踪，本阶段没有覆盖或清理用户文件。

## 11. 已知问题与人工视觉验收结果

- 仓库所有者于 2026-07-30 在当前最终 APK 的 Android 13 实体机上完成约定的人工观察：连续正常滑动 3 次，再快速反向 2 次；确认没有上一条画面挂在新标题下、明显黑闪或串音，人工视觉验收通过。
- 该结论来自实体屏幕人工观察，不来自受 `FLAG_SECURE` 影响的 ADB 截图或镜像；自动 VideoKey gate、Surface 计数和迟到回调测试证据与人工结果一致。
- 指定 iQOO 12 / OriginOS 6 / Android 16 的同进程 recreate 和视觉表现尚未验证；当前 API 36 AOSP instrumentation recreate 与 Android 13 实体机进程重建已经通过。
- 12D 没有 release/target 分段，因此 release→first-frame 和 target-known→first-frame 没有历史同口径数据。

## 12. 证据文件

本机未跟踪构建证据位于：

- `build/reports/stage12e/prechange-transition-player.log`
- `build/reports/stage12e/prechange-gfxinfo.txt`
- `build/reports/stage12e/final-clean-normal-transition-player.log`
- `build/reports/stage12e/final-clean-normal-gfxinfo.txt`
- `build/reports/stage12e/final2-slow-no-cross.log`
- `build/reports/stage12e/final-cross-reverse.log`
- `build/reports/stage12e/final-fast10.log`
- `build/reports/stage12e/final-fast10-gfxinfo.txt`
- `build/reports/stage12e/final-route-roundtrips.log`
- `build/reports/stage12e/final-pause-resume-seek.log`
- `build/reports/stage12e/final-process-recreation.log`
- `build/reports/emulator-compose/`
- `build/reports/vivo-launch-smoke/`

12E 已完成并到此停止；不进入 12F。
