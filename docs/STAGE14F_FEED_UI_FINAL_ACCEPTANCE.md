# 阶段 14F：播放页 UI、交互、性能与安全最终验收

日期：2026-08-12（Asia/Hong_Kong）
仓库：`E:\Telegram Android Developer`
分支：`main`（仓库尚无提交；开始验收时全部文件均为未跟踪状态）
结论：**阶段 14F 的静态、主机与 API 36 x86_64 emulator 强制 Proof 全部通过，可以关闭阶段 14。未获授权的真机安装、启动、真实账号 benchmark、人工手势与 gfxinfo 不计为 PASS，统一保留为“尚未验证”。**

## 1. 阶段合同

### Outcome

形成可复查证据，证明阶段 14A–14E.1 的加载海报、首次滑动提示、短按/长按、文案与详情、完整 `VideoKey`、单播放器/单预加载、缓存与内容保护可以共同工作，并且没有局部修复破坏完整播放页流程。

### Scope

- 新增本文档。
- 全部强制 Proof 通过后，最小更新 `README.md` 的当前状态与本文档链接。
- 读取并验证 14A–14E.1 的生产实现与测试。

### Boundary

- 不修改生产代码、测试、依赖、权限、缓存预算、Pager 参数或性能阈值。
- 不新增播放器、预加载目标、图片下载、图片库或第二份媒体缓存。
- 不运行 TDLib native smoke 于 x86_64 emulator。
- 未获仓库所有者明确授权，不在真机安装/覆盖 APK、启动真实账号播放页、运行 instrumentation、修改网络/缓存/账号/系统设置或执行真实账号 benchmark。

### Failure states

以下任一情况会停止 14F，并转入独立 14F.1：五个前置缺陷缺少生产修复或回归测试；任一强制命令失败；默认 emulator 完整套件失败；发现可复现生产缺陷；静态审计发现权限、依赖、凭证、播放器、预加载、缓存或内容保护回退。本次没有触发这些状态。

### Proof

1. 审计生产实现、测试、最终 merged manifest、APK native 内容和依赖版本。
2. 执行指定的七条主机 Gradle 命令。
3. 在 API 36 AOSP x86_64 AVD 上执行脚本默认完整 Compose 套件。
4. 用主机与 emulator 自动化覆盖十项端到端行为矩阵。
5. 读取阶段 13F benchmark 协议并运行纯主机解析回归；真实设备部分遵守授权边界。

## 2. 阶段 14A–14E.1 能力表

| 阶段 | 最终能力 | 生产边界 | 回归证据 |
| --- | --- | --- | --- |
| 14A | 播放控制与无障碍语义在进入、退出和快速切换期间不留下重复或过期节点 | `VideoPlaybackScreen.kt` 的控制层与语义层；不创建播放器 | `muteAndOriginalMessageEachKeepOneSemanticNodeAndInvokeOneCallback`、`exitingPausedOverlayKeepsAnimationWithoutExposingResumeSemantics`、`rapidPauseStateChangesNeverLeaveDuplicateOrStaleOverlayNodes`、`temporarySpeedPromptIsNonClickableAndExitAnimationClearsStaleSemantics` |
| 14B | 首次滑动提示只在偏好读取完成、当前完整 `VideoKey` 的真实首帧和稳定页同时成立时出现；任何提前触摸/翻页/离开都消费本会话机会 | `VideoPlaybackViewModel.kt` 与 `VideoFeedOnboardingPreferences`；一个账号会话最多写入一次 | `swipeHintStaysHiddenUntilPreferencesFinishLoading`、`pagerPointerDownBeforePreferencesLoadPermanentlyHandlesSwipeHintForThisSession`、`pagerPointerDownAfterUnseenPreferenceButBeforeFirstFrameHandlesSwipeHint`、`lateFirstFrameFromAnOldVideoCannotShowSwipeHintForTheCurrentPage` |
| 14C | 画面短按暂停/继续；系统长按阈值后才请求 2×；rebuffer 保持长按意图；所有结束边界恢复 1× | Compose 只发意图；`ReusablePlayerLifecycle` 在唯一播放器上仲裁速度 | `shortTapPausesExactlyOnceWithoutRequestingTemporarySpeed`、`systemLongPressHoldsTemporarySpeedUntilReleaseWithoutPauseOrReattach`、`temporarySpeedIntentSurvivesRebufferAndIsIdempotentlyReappliedOnRecovery`、`everyExplicitLifecycleBoundaryClearsIntentAndRestoresNormalSpeed` |
| 14D | 文案仅在真实 visual overflow 时可展开；同 `VideoKey` 内容更新会重新计算；Sheet 隔离手势并优先响应返回键 | overflow 状态以 `remember(item.video.key, caption/tagsText)` 建模；详情以完整 `VideoKey` 选择 | `sameVideoKeyCaptionOverflowClearsWhenCaptionAndTagsBecomeEmpty`、`sameVideoKeyTagsOverflowClearsWhenCaptionAndTagsBecomeEmpty`、`sameVideoKeyOverflowFollowsCurrentContentAfterRelayout`、`openDetailsShowsUpdatedContentForTheSameVideoKey` |
| 14E | Loading 海报为完整 `VideoKey` 决定的 Compose 占位，首帧后 190 ms 有限淡出，不泄露旧帧/旧 alpha | 无 Telegram 缩略图、Bitmap、图片请求或磁盘图片缓存 | `readyWithoutMatchingFirstFrameKeepsOpaqueKeyAlignedPlaceholder`、`staleReadyFirstFrameCannotRemoveCurrentPoster`、`matchingFirstFrameUsesTheSpecifiedPosterFadeDuration`、`switchingVideoKeyDuringFadeRestoresANewFullyOpaquePosterImmediately`、`chatIdParticipatesInPosterIdentityWhenMessageIdsMatch` |
| 14E.1 | Loading 期间竖向翻页保持可用，快速/反向/未过 snap 中点不会提交错误页面，PlayerView attach 上限仍为 1 | 海报不持有背景点击/长按手势；Pager 生命周期继续接收指针 | `loadingSwipeUpUsesPagerLifecycleAndShowsTheSecondOpaquePoster`、`loadingSwipeDownReturnsToThePreviousOpaquePoster`、`rapidLoadingSwipesKeepOnePlayerViewAndOnlyTheFinalPoster`、`loadingDragBelowSnapMidpointKeepsTheOriginalPosterWithoutWrongTarget` |

## 3. 历史缺陷与前置条件

最终验收开始前逐项读取了生产实现和测试，结果如下：

| 历史缺陷 | 首个修复点 | 回归测试 | 前置结论 |
| --- | --- | --- | --- |
| 14A 无障碍语义残留/重复 | 动画退出阶段清除可点击语义；按钮各自保持单一语义节点 | 14A 表中四项及 action-button 隔离测试 | 已修复且 host/emulator 回归通过 |
| 14B 首次指针与教学提示竞态 | 首次 pointer-down 在偏好返回前即永久处理本会话，旧首帧受完整 key 门禁 | 14B 表中四项及“一次写入”测试 | 已修复且 host 回归通过 |
| 14C rebuffer 期间丢失长按 2× 意图 | `temporaryPlaybackSpeedRequested` 独立保存；短暂 BUFFERING 后 READY 幂等重施 2× | `temporarySpeedIntentSurvivesRebufferAndIsIdempotentlyReappliedOnRecovery`、`releasingHoldDuringBufferingKeepsRecoveryAtNormalSpeed` | 已修复且 player 单元回归通过 |
| 14D 同 `VideoKey` 内容编辑后 overflow 残留 | caption/tags 内容共同参与 `remember` key | 14D 表中四项及 `editedOverflowStateRemainsIsolatedAcrossVideoKeys` | 已修复且 host/emulator 回归通过 |
| 14E Loading 期间竖向翻页被锁死 | Loading 海报不接管播放画面 gesture；Pager pointer 生命周期始终可用，fullscreen/details 例外 | 14E.1 表中四项及 `fullscreenStillPreventsLoadingPagerSwipe` | 已修复且 host/emulator 回归通过 |

五项前置条件全部成立，因此没有创建 14F.1 修复阶段，也没有修改生产代码或测试。

## 4. 实际修改文件与最终模块边界

### 本阶段源码/文档修改

- `docs/STAGE14F_FEED_UI_FINAL_ACCEPTANCE.md`：新增本验收记录。
- `README.md`：在强制 Proof 通过后最小增加阶段 14F 当前状态与文档链接。

没有修改生产 Kotlin、测试、Gradle、Manifest、资源、脚本或依赖。Gradle 与 emulator 脚本刷新了 `build/` 和各模块 `build/` 下的本地构建/测试报告；这些是验证产物，不是生产源码改动。

### 最终模块边界

依赖方向保持：Compose UI → ViewModel → UseCase/Repository 接口 → Repository 实现 → 基础设施适配器 → 官方 TDLib、Room 或 Media3。

- 四个生产 Compose Screen 对 TDLib、Room DAO 与 `ExoPlayer.Builder` 的直接引用审计命中为 0。
- 全部生产代码只有 1 个 `ExoPlayer.Builder`，位于 `player/VideoPlayerManager.kt`。
- 全部生产代码只有 1 个 `PlayerView(context)` 构造，位于 Pager 外层的 `VideoPlaybackScreen.kt`；页面数不会增加 PlayerView/ExoPlayer 数量。
- ViewModel 通过 `VideoPlaybackController` 调用播放器能力；UI 不创建 ExoPlayer，不调用 TDLib 或 DAO。
- Poster、overflow、详情选择、播放回调和 Pager identity 均使用 `VideoKey(chatId, messageId)`；`pagerIdentityUsesChatIdAndMessageIdEvenWhenMessageIdsMatch` 与 `chatIdParticipatesInPosterIdentityWhenMessageIdsMatch` 通过。

## 5. 主机自动化证据

环境统一设置：

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
```

| # | 精确命令 | 退出码 | Gradle 任务 | 测试统计（tests/failures/errors/skipped） | 结果 |
| --- | --- | ---: | --- | --- | --- |
| 1 | `.\gradlew.bat :player:testDebugUnitTest --tests "com.qixuan.channelvideoflow.player.ReusablePlayerLifecycleTest" --rerun-tasks --no-daemon --console=plain` | 0 | 30 actionable，30 executed | `ReusablePlayerLifecycleTest`：11/0/0/0 | PASS，41.1 s |
| 2 | `.\gradlew.bat :app:testDebugUnitTest --tests "com.qixuan.channelvideoflow.feature.video.VideoPlaybackViewModelTest" --rerun-tasks --no-daemon --console=plain` | 0 | 128 actionable，128 executed | `VideoPlaybackViewModelTest`：80/0/0/0 | PASS，74.4 s |
| 3 | `.\gradlew.bat :app:compileInstrumentationKotlin --no-daemon --console=plain` | 0 | 90 actionable，90 up-to-date | 不适用 | PASS，15.5 s |
| 4 | `.\gradlew.bat :app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.video.VideoPlaybackScreenTest" --rerun-tasks --no-daemon --console=plain` | 0 | 128 actionable，128 executed | `VideoPlaybackScreenTest`：64/0/0/0 | PASS，91.5 s |
| 5 | `.\gradlew.bat test --rerun-tasks --no-daemon --console=plain` | 0 | 345 actionable，345 executed | 88 个 XML 测试套件合计：911/0/0/0 | PASS，136.8 s |
| 6 | `.\gradlew.bat lint --rerun-tasks --no-daemon --console=plain` | 0 | 220 actionable，220 executed | 不适用 | PASS，138.5 s；7 个 lint XML 共 53 个 issue 节点，Error 0、Warning 51、其余 2 为信息级 |
| 7 | `.\gradlew.bat assembleDebug --rerun-tasks --no-daemon --console=plain` | 0 | 190 actionable，190 executed | 不适用 | PASS，74.8 s |

定向 JUnit XML 时间戳：

- `ReusablePlayerLifecycleTest`：`2026-08-12T07:08:33.502Z`。
- `VideoPlaybackViewModelTest`：`2026-08-12T07:09:58.788Z`。
- `VideoPlaybackScreenTest`：`2026-08-12T07:11:51.759Z`。

`test` 的 911 次执行按 Gradle test task 分组：`test` 52、`testDebugUnitTest` 324、`testInstrumentationUnitTest` 211、`testReleaseUnitTest` 324，失败/错误/跳过均为 0。lint/assemble 的 native strip 提示未转为错误；最终 debug APK 的 native 白名单在第 8 节单独核验。

另执行纯主机 benchmark 解析回归：

```powershell
.\scripts\tests\SwipeFirstFrameBenchmark.Tests.ps1
```

PowerShell 进程退出码 0，输出 `SWIPE_BENCHMARK_SCRIPT_TEST_RESULT=PASS`。

## 6. API 36 x86_64 emulator Compose 证据

### 设备与执行

- serial：`emulator-5554`
- ABI：`x86_64`
- SDK：`36`
- AVD：`CVF_AOSP_API36_X86_64`
- product：`sdk_phone64_x86_64`
- 本地开始时间：`2026-08-12 15:22:22 +08:00`
- 本地结束时间：`2026-08-12 15:23:39 +08:00`
- 总耗时：76.8 s
- 命令：`.\scripts\run-emulator-compose-tests.ps1 -Serial emulator-5554`
- 脚本退出码：0
- 构建：179 actionable，179 up-to-date
- instrumentation：`OK (87 tests)`，耗时 56.557 s
- 结果：`EMULATOR_COMPOSE_RESULT=PASS`

未传 `TestClass`，实际运行脚本默认六类完整套件：

| 测试类 | tests/failures/errors/skipped |
| --- | --- |
| `LoginScreenTest` | 13/0/0/0 |
| `ChannelSelectionScreenTest` | 4/0/0/0 |
| `ComposeSmokeTest` | 2/0/0/0 |
| `VideoPlaybackScreenTest` | 64/0/0/0 |
| `CacheSettingsScreenTest` | 3/0/0/0 |
| `VideoPlaybackActivityRecreationTest` | 1/0/0/0 |
| 合计 | 87/0/0/0 |

这是 14F 新生成的 87/87 日志，没有复用 14E 的旧 81/81：

- `build/reports/emulator-compose/device.log`，最后写入 `2026-08-12 15:22:22 +08:00`，SHA-256 `DD96E608D2CA923D7264D01193B0E00B187258CFF6E71A847B01214A3B7D467A`。
- `build/reports/emulator-compose/instrumentation.log`，最后写入 `2026-08-12 15:23:38 +08:00`，SHA-256 `608EC2E9E950EB8D87D4E3EA3E4248CDE55B7037DC46CE462A92F809DB9D3092`。
- `build/reports/emulator-compose/target-logcat.log`，最后写入 `2026-08-12 15:23:39 +08:00`，SHA-256 `A75B506B76467D818881A55C668AB590F7C923F575327E7EB2AB04CD099C6BAF`。

instrumentation 与目标包 logcat 对 `Process crashed`、`FAILURES!!!`、目标包 `FATAL EXCEPTION` 和 ANR 的命中为 0。

### Native 边界

- `app-instrumentation.apk` 的 `lib/**/*.so` 数量为 0。
- 生产 `app-debug.apk` 的 `.so` 数量为 3，且只在 `arm64-v8a`：`libtdjni.so`、`libandroidx.graphics.path.so`、`libdatastore_shared_counter.so`。
- `.so` 排除只配置在 `instrumentation` variant；debug/release 生产 APK 未改动。
- emulator 未运行 TDLib native smoke。

## 7. 端到端 Compose 行为矩阵

| # | 组合 | 证据与结果 |
| --- | --- | --- |
| 1 | Loading 海报 → 上滑到下一条 Loading | emulator 的 `loadingSwipeUp...`、`loadingSwipeDown...`、`rapidLoadingSwipes...` 通过；ViewModel 的新 generation 取消旧目标测试通过；新页面立即为 alpha 1，identity 同时包含 chatId/messageId。 |
| 2 | Loading → 当前真实首帧 | `matchingFirstFrameUsesTheSpecifiedPosterFadeDuration` 证明 190 ms；`controlsAndMetadataStayAbsentBeforeFirstFrame` 与 `matchingFirstFrameRestoresControlsAndExistingDetailsSheet` 证明首帧前后控制边界；attach 始终为 1。 |
| 3 | 淡出中立即反向翻页 | `switchingVideoKeyDuringFadeRestoresANewFullyOpaquePosterImmediately`、反向取消旧 target 与详情自动关闭测试通过；新 key 不继承旧 alpha/文案/详情，无 crash。 |
| 4 | 首次滑动提示 | 偏好未完成、首帧前、非稳定页均隐藏；提前触摸、翻页、离开或旧 key 首帧不会迟到显示；一个会话只消费/写入一次。相关 ViewModel 定向测试 80/80。 |
| 5 | 短按 | 只有播放画面区域触发 pause/resume；action buttons、详情入口和 progress gesture 不触发背景短按或 2×。`shortTap...` 与 `actionButtonsAndProgressGesturesNeverActivateTemporarySpeed` 通过。 |
| 6 | 长按 2× | Compose 使用系统长按阈值；按住期间 rebuffer 保留请求，READY 后仍为 2×；release/cancel/pause/seek/page/background/end/failure/unbind 均清意图并恢复 1×。Compose、ViewModel、lifecycle 三层测试全部通过。 |
| 7 | 文案与详情 | 仅真实 visual overflow 显示展开；同 key caption/tags 更新清除旧 overflow；详情使用独立 Sheet/scroll 容器，背景语义被清除，滚动不触发 Pager、pause、2× 或 seek；返回先关 Sheet。 |
| 8 | details/fullscreen/返回键 | 生产 `BackHandler` 顺序明确为 detail → fullscreen → `onBack`；`firstSystemBackClosesDetailsAndSecondBackLeavesPlaybackPage`、fullscreen 与详情失效测试通过。 |
| 9 | Unsupported/Failed/Empty | 三种状态保持明确文案/重试或返回行为；Unsupported 不 attach，现有 recoverable failure 可重试/继续浏览；`failureAndUnsupportedReplaceLoadingPosterWithoutSuccessFade` 证明不会被 Loading poster 遮挡。 |
| 10 | 快速连续滑动 | `rapidLoadingSwipes...`、`onlyFinalStablePageBindsAndTransitionPausesOldAudio`、`rapidTargetChangesCancelOldPreparationAndNeverBindItsVideo`、旧 key callback gate 测试通过；只有最终当前项可展示首帧，attach 上限为 1。 |

这里的 emulator 证据是真实 API 36 x86_64 Compose instrumentation，但使用测试数据和 Fake 边界，不宣称为真实 Telegram 账号、TDLib native 或真实网络证据。

## 8. 性能、资源、架构与安全审计

### 性能与资源

- 先读取 `run-swipe-first-frame-benchmark.ps1` 参数：必填 `-Serial`；默认 `SwipeCount=12`、`PerSwipeTimeoutSeconds=12`、`Mode=Normal`、`Direction=Forward`、`PlaybackReadyTimeoutSeconds=30`、`FastCheckpointEvery=0`、`ReportStage=stage13d`。脚本明确拒绝 emulator，并在未传 `-SkipBuild` 时构建和覆盖安装 debug APK。
- 本次未获真机安装/账号/网络操作授权，因此没有运行真实 benchmark，没有更改 timeout、阈值、缓存、账号或网络。
- 生产代码对 `rememberInfiniteTransition`/`infiniteRepeatable` 的命中为 0；海报淡出是一次性 190 ms `Animatable.animateTo`，提示与控制动画也为有限动画。
- `positionTickerOnlyRecomposesProgressAndNeverReattachesPlayerView` 在 host 与 emulator 通过：12 次 position 更新不重组 Pager，PlayerView attach 仍为 1。
- `posterAndVideoKeyChangesNeverIncreasePlayerViewAttachments`、快速 Loading 翻页和 Activity recreation 测试通过；页面数量不增加 PlayerView/ExoPlayer。
- 生产 `Bitmap`/ImageLoader/Coil/Glide/Picasso/MediaStore/DownloadManager 命中为 0；没有额外图片、网络或磁盘图片缓存任务。
- 14F 没有启用阶段 13 否决候选：Pager 仍使用 Compose 默认 fling；owner promotion 与 Telegram contained-active-request reuse 的生产开关均为 `false`；startup range 默认 `BASELINE`，head 256 KiB、tail 0；play-before-prepare 默认 `false`。
- 新增 UI 的自动化证据没有 crash、无限动画、Pager 重组或 attach 增长回退。真实设备 `gfxinfo modern jank` 未重新采集，因此不把 emulator/host 结果表述为真机 jank 百分比，也不将阶段 13F 的不同随机媒体/缓存/网络窗口写成严格因果 A/B。

### 播放器、预加载与缓存

- 唯一 ExoPlayer 构造点和唯一 PlayerView 构造点保持。
- `VideoPreloadManager` 只有一个 `target`，同一时刻只有唯一下一条 speculative owner；正常与保守预算均为 256 KiB，OFF 为 0。
- 移动数据预加载默认 `false`；网络策略与用户开关未改变。
- `SimpleCache`/`CacheDataSource` 命中为 0；不存在 TDLib 完整缓存之外的 Media3 完整文件缓存。
- 媒体缓存默认 500 MiB，可选 200 MiB、500 MiB、1/2/5/10/15/20 GiB，未改动。
- Loading 海报只是 `VideoKey` 决定配色和宽高比的 Compose 图形占位，**不是 Telegram 缩略图**。

### 权限、备份、网络与内容保护

对 debug、instrumentation、release 三份 merged manifest 分别解析，最终有效权限均严格为 2 个：

1. `android.permission.INTERNET`
2. `android.permission.ACCESS_NETWORK_STATE`

三份 merged manifest 均保持：

- `allowBackup=false`
- `dataExtractionRules=@xml/data_extraction_rules`
- `fullBackupContent=@xml/backup_rules`
- `usesCleartextTraffic=false`

`data_extraction_rules.xml` 同时排除 cloud backup 与 device transfer 的 root/file/database/sharedpref 及 device_* 域；`backup_rules.xml` 排除相同敏感域。受保护内容仍以当前视频 `canBeSaved == false` 驱动 `FLAG_SECURE`，没有新增保存、导出、分享或公共目录路径。

### 依赖、凭证与日志

- 精确匹配版本值中的 alpha/beta/RC/snapshot 命中为 0；没有新增依赖。
- `local.properties` 由 `.gitignore` 忽略，`git ls-files` 结果为 0；本次未读取或输出其真实值。
- 源码、测试、文档和脚本中真实 32 位 API hash 赋值命中为 0。5 个非零 API ID 数字字面量全部位于明确使用 `SYNTHETIC_VALID_HASH` 的凭证单元测试，不是真实凭证。
- logger/Log 调用与 caption、formattedText、messageText、手机号、密码、apiHash、验证码、媒体 byte array 的同调用命中为 0；现有调试日志只记录允许的状态、请求、完整 key、区间和脱敏错误类别。
- 没有记录真实账号验证码、密码、完整手机号、会话、数据库密钥、完整 TDLib 对象、正文或媒体字节。

## 9. Host / emulator / 真机证据分栏

| 范围 | 已验证 | 明确不能推导 |
| --- | --- | --- |
| Host | 7 条指定 Gradle Proof；911 次全量测试；定向 11/80/64；lint/assemble；静态架构、安全、依赖、凭证、APK/manifest 审计；benchmark 解析回归 | 不代表真实 TDLib、真实账号、真实网络、Surface/厂商系统或真机 jank |
| API 36 AOSP x86_64 emulator | 默认六类 Compose suite 87/87；播放页组合手势、海报、语义、Sheet、单 attach；目标包 crash 命中 0；instrumentation target 无 `.so` | 不运行 TDLib native smoke；不代表 ARM64 真机、真实媒体、账号、缓存温度或网络 |
| 真机 | 仅只读 `adb devices -l`：检测到 `<device-serial>`，product/device `pissarropro`，model `21091116UC` | 不是 Vivo，也没有被当作 x86_64 emulator；本阶段没有安装、覆盖、启动、instrumentation、播放或 benchmark |

## 10. 尚未验证事项与关闭结论

以下均为**尚未验证**，不伪装成 host/emulator PASS：

- 当前真机 install+launch smoke、`MainActivity` resumed/top 与目标包冷启动 crash。
- 真实 Telegram 账号下的正常连续滑动、Loading 中继续滑动、快速反向、海报到真实首帧、长按 2×、详情 Sheet 和系统返回组合。
- 真实网络/随机媒体下的 swipe-first-frame benchmark、rebuffer、目标包 crash 和 UID 流量。
- 14F 动画加入后的真机 `gfxinfo modern jank`；也未执行严格同媒体、同缓存温度、同网络窗口因果 A/B。
- Vivo/iQOO 12、OriginOS 6、Android 16 的 install+launch smoke；当前 `adb devices` 中没有此设备。
- x86_64 emulator 的 TDLib native smoke（按门槛明确禁止）。

这些项目均需要真机安装、启动、真实账号或设备状态变化，而本阶段没有得到该授权；按任务合同记录为“尚未验证”，没有擅自操作或扩大权限。

**最终决定：可以正式关闭阶段 14 的静态、主机与 API 36 x86_64 Compose 最终验收。** 14A–14E.1 的五个历史缺陷都有回归测试，七条主机 Proof 和新的 87/87 emulator 完整套件全部通过，未发现生产缺陷，未触发 14F.1。该关闭结论不把上述真机事项升级为 PASS；若后续仓库所有者授权真机 smoke/benchmark，应作为补充证据追加，不应修改本次通过阈值、timeout 或缓存状态。
