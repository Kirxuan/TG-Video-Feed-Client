# 优化阶段 12D：Media3 首帧缓冲、播放器复用与 rebuffer 安全调优

状态：已完成。阶段 12A、12B、12C 的文档、Proof 和真机结果均已确认通过后才开始本阶段；未进入 12E。

## 1. 阶段合同

### Outcome

在不增加缓存副本、不改变用户质量偏好、保持唯一主要 ExoPlayer 的前提下，减少 bind 到真实首帧的中位数和长尾，消除普通滑动切换中的 `stop`、`clearMediaItems` 和 renderer 全量重启，并保持 rebuffer、错误、内存和内容保护边界。

### Scope

- `player/build.gradle.kts`：增加只用于可复现实验的离散 BuildConfig 候选开关。
- `player/src/main/java/com/qixuan/channelvideoflow/player/ReusablePlayerLifecycle.kt`：唯一播放器实例和绑定生命周期。
- `player/src/main/java/com/qixuan/channelvideoflow/player/PlaybackBufferPolicy.kt`：缓冲参数边界和候选模型。
- `player/src/main/java/com/qixuan/channelvideoflow/player/VideoPlayerManager.kt`：普通换片最小化、PlayerView attach 计数、LoadControl 候选接线、Debug 指标和脱敏失败分类。
- `player/src/main/java/com/qixuan/channelvideoflow/player/PlaybackSessionMetrics.kt`：首帧后的 30/60 秒 rebuffer 窗口。
- `player/src/main/java/com/qixuan/channelvideoflow/player/PlaybackTransitionMetrics.kt`：bind→prepare、prepare→READY 上界和首帧缓冲量。
- `player/src/test`：生命周期、参数边界、rebuffer、stale callback 和错误分类测试。
- `README.md` 与本文档：阶段状态、A/B、Proof 和已知风险。

### Boundary

- 不修改 TDLib 区间调度、read-ahead、缓存实现、质量选择算法或 Media3 版本。
- 不新增完整媒体缓存、权限、公共存储、后台服务或播放器实例。
- 不自动完整下载，不降低 12 秒 rebuffer 恢复门槛。
- 不改变 `FLAG_SECURE`、受保护内容策略、账号退出清理或 Activity 生命周期合同。
- 不模拟弱网时修改 VPN、路由或使用 root；本阶段未执行人工网络整形。

### Failure states

- `supportsStreaming=false`：不创建可播放绑定或预加载，继续显示不支持流式播放。
- 初始加载、用户暂停和显式 seek 的 BUFFERING 不记为普通 rebuffer。
- 只有真实首帧和首次 READY 都已发生后、期望自动播放且不处于 seek 时，BUFFERING 才记为 rebuffer。
- decoder 初始化/查询/解码、格式、网络、超时、文件和通用播放器错误使用脱敏分类；旧 bind 的回调不能覆盖新视频状态。
- 页面/筛选退出释放绑定，账号退出和完整释放继续停止播放并释放播放器；普通支持流式视频之间切换不完整释放 renderer。

### Proof

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :player:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat lint --rerun-tasks --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

并按 `AGENTS.md` 执行完整 Compose Path B。

## 2. 前置条件与 12C 基线

开始前完整阅读了 `AGENTS.md`、`README.md`、阶段 12A、12B、12C 和阶段 10 文档。12C 的完整主机 Proof、Compose Path B 和 Android 13 ARM64 真机结果均为通过，因此允许开始 12D。

12C 最终两轮各 12 次滑动的 `bind→first frame` 分别为：

| 12C 轮次 | P50 | P90 | max | 首帧/rebuffer/crash |
|---|---:|---:|---:|---|
| 第 1 轮 | 195ms | 225ms | 231ms | 12/12，0/0/0 |
| 第 2 轮 | 195ms | 210ms | 231ms | 12/12，0/0/0 |
| 两轮合并 | 195ms | 225ms | 231ms | 24/24，0/0/0 |

两轮合并的 `gesture→first frame` 为 P50 806ms、P90 829ms、max 843ms。12C 原始证据位于 `build/reports/stage12c/owner-targeted-1-valid.log` 和 `owner-targeted-2.log`。

## 3. 50–60 秒缓冲参数的历史理由

现有安全参数为：

```text
MIN_BUFFER_MILLIS = 50000
MAX_BUFFER_MILLIS = 60000
BUFFER_FOR_PLAYBACK_MILLIS = 2500
BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MILLIS = 12000
prioritizeTimeOverSizeThresholds = true
backBuffer = 0
targetBufferBytes = C.LENGTH_UNSET
```

阶段 5 曾在约 3.56GiB 高码率文件上观察到：1MiB read-ahead 首次 READY 约 49.8 秒，之后反复播放 6–7 秒、缓冲 4–5 秒；即使扩大到 4MiB，约 32 秒时仍会暂停。因此采用按时间优先的 50–60 秒目标缓冲，并用 12 秒 rebuffer 恢复门槛避免过早恢复后再次卡顿。

阶段 10 的弱网基线中，约 61.7MiB、21:50、1080p 视频平均消耗约 48.2KiB/s，实测供给约 46.6KiB/s；50–60 秒前向缓冲下连续播放 209.97 秒且 rebuffer 为 0。2.5 秒仅是允许首播的最低门槛，不代表首帧实际只有 2.5 秒缓冲。

因此本阶段没有同时改动多个缓冲参数，也没有在没有独立证据时降低 12 秒恢复门槛或 50–60 秒弱网安全缓冲。

## 4. 播放器生命周期追踪与修改

### 修改前追踪

- `VideoPlayerManager` 已是 application-scoped `@Singleton`，UI 没有直接创建 ExoPlayer。
- `FeedPager` 的 `PlayerView` 位于 `VerticalPager` 外层，同一支持流式播放会话中不是每页一个 PlayerView。
- 普通支持流式视频切换会走完整绑定清理，包含 `stop`，随后重新设置媒体源、`prepare` 和 `playWhenReady`。该 `stop` 会不必要地重置 Media3 播放管线。
- `releaseBinding`、完整 `release`、重试、页面不稳定、账号/队列代际变更和错误回调均已沿现有控制器路径追踪。

### 最终生命周期

- `ReusablePlayerLifecycle` 只通过一个工厂长期持有一个 engine；100 次 bind 测试仍只创建 1 个播放器。
- 普通换片固定为 `pause old audio → setMediaSource → prepare → playWhenReady=true`，不调用 `stop` 或 `clearMediaItems`。
- 旧视频先 pause，因此不会串音；没有为保留 surface 同时播放两个视频。
- `releaseBinding` 仍执行 pause 和 clear，但保留 engine 供后续页面复用。
- 完整 `release` 才清绑定、从 PlayerView detach 并释放 ExoPlayer；之后的新会话才允许创建新实例。
- PlayerView attach 幂等；相同 view/player 重复 attach 直接返回，切换到不同 view 时先 detach 旧 view。
- 两轮最终真机滑动中 `surfaceAttachCount=1`、`playerInstances=1`，页面数量和 bind 次数均未增加播放器或 surface attach 数量。

没有自定义 `RenderersFactory`；继续使用稳定 Media3 默认 renderer，只消除普通换片的全量 stop。

## 5. Debug 观测

本阶段只在 `BuildConfig.DEBUG` 下增加或扩展以下脱敏指标：

- bind→prepare；
- prepare→READY 上界；
- bind→真实首帧；
- 首帧回调时 `bufferedPosition - currentPosition`；
- 首帧后 30/60 秒 rebuffer 次数和总时长；
- decoder initialization/query/decoding、format、network、timeout、file、player 分类；
- surface attach 次数和累计播放器实例数；
- 当前离散候选编号和参数。

指标只使用单调时钟、视频键、fileId、区间和状态，不记录消息正文、媒体字节、路径、手机号、验证码、密码或凭证；Release 不输出这些详细日志，也不持久化指标。

设备实际会出现首帧回调先于 READY listener 的顺序。指标状态机因此允许先记录首帧，但必须等 READY 也发生后才允许计 rebuffer；prepare→READY 使用最早可证明真实画面可用的上界，迟到 READY 不会覆盖首帧结果。

## 6. 测试覆盖

新增测试与既有回归测试共同覆盖：

1. 多次 bind 始终复用同一播放器；
2. 100 次页面式 bind 仍只有一个播放器实例；
3. 默认 bind 顺序为 media source、prepare、playWhenReady；
4. 新 bind 第一条动作是 pause，旧音频立即停止；
5. `releaseBinding` pause/clear 但保留 engine，完整 release 后才允许新实例；
6. startup/rebuffer 阈值不能越过 min buffer，back buffer 和 target bytes 有离散边界；
7. 用户暂停不计 rebuffer；若真实 rebuffer 已开始，暂停时截断计时，不累计暂停等待；
8. seek BUFFERING 不计普通 rebuffer；若真实 rebuffer 已开始，seek 时截断计时，不累计 seek 等待；
9. 首次 READY 前 BUFFERING 不计 rebuffer；
10. READY 和真实首帧后 BUFFERING 才计 rebuffer，并计算 30/60 秒窗口；
11. stale READY、首帧和 error callback 不污染新视频；
12. decoder、network、timeout 与 DataSource timeout 分类保持；
13. 质量刷新/切换后 controller 最终收到正确 fileId；
14. `supportsStreaming=false` 不产生可播放绑定或 next preload，播放器边界在创建媒体源前返回 Unsupported。

## 7. 单变量真机 A/B

设备：在线 Android 13 ARM64，型号 `21091116UC`，serial `<device-serial>`。使用 `adb install -r -t` 保留用户数据和缓存；每个候选都冷启动后进入同一播放测试页，使用相同坐标、150ms 上滑手势和 1300ms 间隔连续滑动 12 次。没有清除 app data/cache，没有修改 VPN、路由或网络策略。

除候选表中唯一变化外，其余参数均为 50s/60s/2.5s/12s、time priority、0 back buffer、自动 target bytes、prepare 后 play。

| 候选 | 唯一变化 | bind→首帧 P50/P90/max | gesture→首帧 P50/P90/max | 首帧 buffer P50/P90/max | 12 次结果 | 决定 |
|---|---|---|---|---|---|---|
| `12D-LIFECYCLE` | 仅生命周期优化 | 86/237/261ms | 699/835/854ms | 17834/30719/39850ms | 12/12；rebuffer/error/crash=0 | 保留生命周期实现 |
| `12D-STARTUP1000` | startup 2500→1000ms | 87/232/244ms | 698/831/845ms | 18282/25301/34048ms | 12/12；0/0/0 | P50 无收益，回滚 |
| `12D-SIZEPRIORITY` | prioritize time true→false | 88/258/260ms | 696/852/871ms | 17834/21781/31811ms | 12/12；0/0/0 | startup P90 变差，回滚 |
| `12D-BACK5000` | back buffer 0→5000ms | 88/219/257ms | 697/816/850ms | 19958/32405/34048ms | 12/12；0/0/0 | 无可解释首帧因果且 PSS 较高，回滚 |
| `12D-TARGET16M` | target bytes 自动→16MiB | 88/237/249ms | 693/849/850ms | 14485/25792/37653ms | 12/12；0/0/0 | 无稳定收益，回滚 |
| `12D-PLAYFIRST` | playWhenReady 在 prepare 前 | 90/216/253ms | 701/811/851ms | 14997/34048/39082ms | 12/12；0/0/0 | P50 变差且偏离固定调用顺序，回滚 |

`12D-SIZEPRIORITY` 的 PSS 前后快照因实验脚本变量错误而未保存，写为“尚未验证”；其余候选滑动后 PSS 约 239–242MiB，没有支持保留更激进参数的证据。

结论：所有更激进 LoadControl 或调用顺序候选都没有安全、可重复的首帧收益。最终保留 12C 的全部安全缓冲参数，只采用生命周期/调用路径的有证据改进，即“参数调优无安全收益”。

## 8. 最终候选与 12C 对比

最终候选 `12D-FINAL`：50s min、60s max、2.5s startup、12s rebuffer、time priority、0 back buffer、自动 target bytes、`setMediaSource → prepare → playWhenReady`。

| 最终轮次 | bind→首帧 P50/P90/max | gesture→首帧 P50/P90/max | 首帧 buffer P50/P90/max | 成功/异常 | PSS |
|---|---|---|---|---|---|
| 第 1 轮 | 86/219/226ms | 694/819/839ms | 15722/29888/34048ms | 12/12；rebuffer/error/crash=0 | 207357→240817KiB |
| 第 2 轮 | 92/247/252ms | 698/845/857ms | 15936/19958/29354ms | 12/12；rebuffer/error/crash=0 | 尚未验证（汇总脚本在输出 PSS 前失败，原始 log 已保留） |
| 两轮合并 | 89/226/252ms | 694/839/857ms | 15936/29354/34048ms | 24/24；rebuffer/error/crash=0 | 以第 1 轮及单视频轮询为证据 |

相对 12C 两轮合并：

- bind→首帧 P50：195→89ms，改善 54.4%；
- bind→首帧 P90：225→226ms，变化 +0.4%，无明显回退；
- gesture→首帧 P50：806→694ms，改善 13.9%；
- gesture→首帧 P90：829→839ms，变化 +1.2%；
- 24/24 到达首帧，播放器实例和 surface attach 均为 1。

因此达到“P50 或 P90 至少改善 15%，另一项不得明显回退”的验收目标；收益来自取消普通换片的 stop/clear 管线重置，不来自降低弱网安全阈值。

## 9. 三条 60 秒连续播放与异常样本

最终 APK 保留数据覆盖安装后，额外抽取三条不同视频连续播放。每 5 秒读取一次进程 PSS，并检查 READY、位置增长、30/60 秒 rebuffer、decoder/network failure 和 crash：

| 视频 | 连续证据 | 首帧后 60 秒 rebuffer | 60 秒内 error/crash | PSS max |
|---|---|---|---|---:|
| 1 | READY，position 80174→145565ms，连续增长 65391ms | 0 次 / 0ms | 0 / 0 | 240243KiB |
| 2 | READY，position 19774→90120ms，连续增长 70346ms | 0 次 / 0ms | 0 / 0 | 228263KiB |
| 3 | 首帧后 60.138 秒仍 READY，position 59965ms | 0 次 / 0ms | 0 / 0 | 223513KiB |

三条都满足首帧后 60 秒无 rebuffer。轮询 PSS 峰值不高于滑动候选约 239–242MiB 的范围，没有观察到明显异常增长；更严格的 heap/native allocation 峰值分析尚未验证。

同时保留以下不计入通过样本、但必须披露的当前网络风险：

- 一条视频渲染首帧后始终未进入 READY，约 63 秒按既有上限分类为 `TIMEOUT`。
- 第 3 条通过首帧后 60 秒窗口后，在约 87.1 秒耗尽前向缓冲，发生 1 次真实 rebuffer，并在约 93.9 秒分类为 `TIMEOUT`；其 `rebuffer60Count` 仍为 0。

这两条异常说明当前 Telegram 网络供给在更长时段仍可能中断。最终参数与 12C 完全相同，本阶段没有用降低门槛、自动完整下载或第二份缓存掩盖问题，也不能声称更长时间或人工弱网下永不 rebuffer。人工网络整形弱网 A/B 尚未验证；未改 VPN、路由或使用 root。

原始证据位于 `build/reports/stage12d/`，包括六个候选、两轮最终滑动、三条 60 秒样本和两个异常尝试的 logcat/crash 记录。该目录是本机构建证据，不包含消息正文或凭证。

## 10. Proof 结果

主机：

- `:player:testDebugUnitTest`：通过；lint 精确标注修复后再次通过。
- `:app:testDebugUnitTest`：通过。
- `test --rerun-tasks`：最终通过，345 个 task 执行。最终态的一次中间重跑曾在未修改的 `TelegramFileManagerTest.closingALeaseWakesAWaitingLoaderImmediately` release 测试上出现 1 次 cause 类型断言抖动；该测试隔离重跑通过、完整 `:telegram:testReleaseUnitTest` 重跑通过、原始全仓命令再次重跑通过，因此未越界修改 12C 调度器。该偶发时序测试仍作为已知测试抖动保留记录。
- `lint --rerun-tasks`：第一次因新适配器缺少 Media3 `@UnstableApi` 标注失败；只补标注后重跑通过，220 个 task 执行。
- `assembleDebug`：通过，190 个 task；APK 为最终 `12D-FINAL` 配置。

Compose Path B：

- `:app:compileInstrumentationKotlin`：通过。
- Login、ChannelSelection、ComposeSmoke Robolectric-Compose：通过。
- CacheSettings Robolectric-Compose：通过。
- API 36 AOSP x86_64 `emulator-5554` Compose UI：`EMULATOR_COMPOSE_RESULT=PASS`。
- Android 13 ARM64 `21091116UC` 安装、冷启动、MainActivity resumed/top、目标包无 crash：`VIVO_LAUNCH_SMOKE_RESULT=PASS`。

实体机不是 iQOO 12 / OriginOS 6 / Android 16，不得把本次结果表述成该机型/系统的完整 instrumentation 证据。按 Path B 合同，真机只执行 install+launch smoke；未重复受 `fast_freezer`/`single-cleaner` 影响的完整 Vivo instrumentation。

## 11. 安全与边界检查

- 单 ExoPlayer、单 PlayerView attach、单份 TDLib app-private 媒体缓存保持。
- UI 不创建 ExoPlayer，未添加不稳定版本依赖或第二份完整缓存。
- 质量偏好、最终 fileId、预加载所有权令牌和 TDLib DataSource 可取消/超时合同不变。
- Media3 load retry 上限、网络/超时/decoder 分类和 stale callback gate 保持。
- 未新增权限、公共存储、后台服务或广泛存储访问。
- `FLAG_SECURE` 和受保护内容策略未修改。
- 未清除真机用户数据或缓存；未退出账号。
- 未提交、未暂存、未推送。

## 12. 阶段结论

12D 完成并停止。最终只保留有证据的播放器生命周期优化；缓冲参数调优无安全收益，因此全部回到 12C 安全值。不要在本阶段继续进入 12E。
