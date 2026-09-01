# 优化阶段 13A：默认随机与随机专项性能基线

日期：2026-08-01；13A/13B/13C 联合复核：2026-08-09
状态：默认 RANDOM 行为、观测、脚本、完整主机 Proof、API 36 x86_64 emulator Compose UI 与当前 Android 13/API 33 ARM64 实体机均通过；历史 13A Normal 失败样本原样保留，当前累积生产基线已补到 Normal Forward 12/12 与连续 Fast Reverse 10 PASS

## 1. 阶段合同

### Outcome

- 每次新建播放页 ViewModel 会话从首个 Loading state 起默认 `RANDOM`，不会先显示“最新”再跳到“随机”。
- 用户可在本会话切换为 `LATEST`；Room Flow、空列表与筛选变化不会把本会话悄悄切回 `RANDOM`。
- 新建另一个播放页 ViewModel 后重新从 `RANDOM` 开始；不读取或写入 DataStore 的“上一次顺序”。
- RANDOM 专项 benchmark 能证明实际顺序并区分控制链路、刷新、预加载、首区间、Media3 READY 与真实首帧。

### Scope

- `core:model` 的播放会话默认顺序事实。
- 播放页 `UiState`、ViewModel、现有 Pager/PlaybackPlan 观测上下文及其 JVM/Compose 测试。
- player 模块的现有 `CVF-Transition` 内存指标和 Debug 汇总。
- 既有滑动 benchmark 模块、入口和脚本测试。
- README、验收矩阵与本文档。

### Boundary

- 不进入 13B。
- 不持久化播放顺序，不修改随机洗牌与轮次算法。
- 不修改 `VideoPreloadController`/`VideoPreloadManager` 行为、唯一下一条 256 KiB、当前项 4 MiB 前读、LoadControl、质量选择、缓存上限、网络策略或 Pager 动画。
- 不新增播放器、PlayerView、权限、依赖、Room/DataStore 字段或媒体缓存副本。

### Failure states

- Loading、Empty、Content 都从同一会话顺序事实渲染；空队列不创建播放请求。
- 用户选中 `LATEST` 后，Room Flow 更新不会重置选择。
- 快速切换顺序、筛选或页面时，旧 plan、binding 与 preload 继续使用既有 generation 失效规则。
- benchmark 若不能从 UI 与成功样本证明 `RANDOM`，或样本、关键字段、首字节/READY/首帧数据不足，必须非零失败。

### Proof

按顺序执行：领域与 ViewModel 窄测试、PowerShell 解析器测试、fresh `test`/`lint`/`assembleDebug`、完整 Compose Path B；只有当次 `adb devices` 存在已授权实体机时才运行 RANDOM normal 12 次与 fast 10 次。

## 2. 实现结果

### 2.1 单一默认顺序事实

`core:model` 的 `DEFAULT_VIDEO_FEED_ORDER` 是新播放会话唯一默认值，固定为 `VideoFeedOrder.RANDOM`。`VideoPlaybackUiState` 与 ViewModel 私有 `FeedCriteria` 都引用该事实，因此 Compose 收到的第一个 Loading state 和 Repository 首次数据使用相同顺序。

`setOrder(LATEST)` 仍只修改当前 ViewModel 的内存 criteria；Room Flow 只组合当前 criteria，不重新构造默认值。新 ViewModel 会重新读取领域默认值，不使用 DataStore。

### 2.2 队列、Pager 与 generation

`VideoPlaybackQueue` 未修改：轮内不重复、轮次边界避免立即重复、元数据刷新保持当前轮顺序的规则全部保留。Pager 的虚拟窗口、snap 中点、target prepare、settled bind 和随机轮次重建行为也未改变。

本阶段只在既有 transition 事件上附加：

- `order=RANDOM|LATEST`；
- `direction=INITIAL|FORWARD|REVERSE|UNCHANGED`；
- `randomRoundBoundary=true|false`。

这些值来自当前 criteria、当前/上一 settled pagerPage 与既有队列大小，不参与队列或调度决策。

### 2.3 PlaybackPlan 与刷新观测

瞬时 `PlaybackPlan` 仍由相同 `VideoKey`、质量、账号和队列 generation 校验。新增的 `refreshMillis` 只用单调时钟测量既有刷新调用；晋升计划把既有 `refreshOutcome`、plan age 与刷新耗时交给 transition state machine。未增加刷新、重试、超时或网络请求。

### 2.4 首区间、READY 与首帧

`VideoPlayerManager` 复用 `PlaybackRangeRequestSession.firstRangeReady()` 的安全内存时间戳，将 bind→first byte 放入同一条 transition summary。Media3 READY 和真实首帧继续使用已有 callback gate；成功样本现在可并列输出：

- bind→first byte；
- bind→READY；
- READY→first frame；
- bind→first frame。

首区间请求范围、owner、优先级和取消行为未改变。详细指标仍仅在 Debug 输出，不持久化。

## 3. RANDOM benchmark 合同

入口仍为 `scripts/run-swipe-first-frame-benchmark.ps1`，报告独立写入 `build/reports/stage13a`。新增 `-Direction Forward|Reverse`，默认 Forward；`-FastCheckpointEvery 0` 保持原连续 Fast，显式非零值只把总手势分批并在批间等待最终可见项终局，报告披露实际批次，不改变每批内快速手势参数。

脚本在发送手势前通过 UI tree 同时确认播放页控制和“随机”tab 的 selected 语义；Compose 将 selected 放在文字的直接父语义节点，受测解析器按该结构确认，且不允许任意已有首帧日志绕过 UI 门槛。完成后还要求每个成功样本都包含并通过：

- `order=RANDOM`；
- direction 与 random round boundary；
- promoted、plan age、refresh outcome/耗时；
- 控制链路字段；
- bind→first byte、bind→READY、bind→first frame。

报告汇总 RANDOM/LATEST/UNKNOWN、正反向、轮次边界、刷新结果、promoted、preload yield/resume、rebuffer 与 crash。所有成功样本的实际方向还必须与 `-Direction` 请求一致；字段缺失、LATEST、方向不符、空样本、Normal 样本不足、FAILED/UNSUPPORTED、rebuffer、crash 或超时都会得到 FAIL 和非零退出。

本阶段是 RANDOM 行为与观测基线，不把随机抽到的不同内容与 12A 顶部序列伪装成同内容性能 A/B。

## 4. TDD 记录

1. 首个 ViewModel 测试先证明修改前 Loading state 为 `LATEST`，再引入唯一默认事实转绿。
2. ViewModel 整类首次回归暴露 9 条旧测试隐式依赖默认 LATEST；只把这些历史夹具改为显式 LATEST，未恢复生产默认。
3. transition 指标测试先因缺 order/direction/round/refresh/first-byte API 编译失败，再加入纯观测状态转绿。
4. ViewModel 方向测试先发现前进被记录为 `UNCHANGED`；根因是先更新 `lastSettledPage` 再采样，只调整观测顺序。
5. benchmark 测试先因解析器缺 RANDOM 证明失败，再增加严格字段验证；随后 runner 测试先证明入口未设硬门槛，再把校验接到退出结果。
6. 首次 fresh full test 暴露一条旧 Compose 复合测试仍隐式依赖默认 LATEST；目标用例连续两次稳定复现。`FeedOrderTab` 明确禁止已选 tab 的冗余回调，因此只将该“点击 RANDOM”夹具的初始 order 改为显式 LATEST，新会话默认 RANDOM 的独立测试保持不变。
7. 最终审查先加入请求方向的合成 forward/reverse/混合/空样本测试并观察缺少纯函数的 RED，再让 runner 调用该受测门槛，要求所有成功样本方向与 `-Direction` 一致后转绿；没有发送额外手势。
8. 真机 Fast 首次暴露 UI tree 的 selected 位于“随机”文字直接父节点，原同标签正则恒不成立且任意首帧日志可错误绕过门槛。合成父语义、LATEST 和无关祖先用例先 RED，再引入纯 UI 解析函数并移除日志旁路后转绿；一度提出的 reverse runway 假设被设备复验否定并撤回，没有保留会改变样本温度的准备手势。
9. 当前队列下连续10次 Fast Reverse 稳定跨整轮回到同一项。纯批次规划测试先 RED，随后只加入可选检查点参数：默认仍为单批10，显式3严格生成3/3/3/1且总数仍为10；runner 接线与报告披露另行 RED→GREEN。未修改生产代码、随机算法或 Pager。

所有 Fake 都使用合成键与文件编号；没有真实 Telegram 内容、频道名、caption、手机号、验证码、密码、API ID 或 API Hash。

## 5. 已执行的窄 Proof

- `:core:domain:test`：通过。
- `:app:testDebugUnitTest --tests VideoPlaybackViewModelTest`：通过，38 tests。
- `scripts/tests/SwipeFirstFrameBenchmark.Tests.ps1`：`SWIPE_BENCHMARK_SCRIPT_TEST_RESULT=PASS`。
- `PlaybackTransitionMetricsTest` 定向/整类：通过。
- 新 Compose 默认选择定向 Robolectric 测试：通过。

## 6. 完整主机与 Compose Proof

- fresh `test --rerun-tasks`：首次运行在 118 项中的一条旧 Compose 夹具失败；完成上述最小修正后重跑通过，345 个 Gradle task 全部执行。
- fresh `lint --rerun-tasks`：通过，220 个 Gradle task 全部执行；只有既存 Android deprecated 编译警告。
- `assembleDebug`：通过。
- `:app:compileInstrumentationKotlin`：通过。
- 两组指定 Robolectric-Compose：通过。
- API 36 AOSP x86_64 emulator Compose UI：39/39，通过；2026-08-01 使用当次在线 emulator 复验仍为 39/39 PASS，instrumentation target 继续不包含生产 TDLib native `.so`。
- 当前目标实体机 install+launch smoke：2026-08-01 在 Android 13/API 33 ARM64 实体机上保留数据覆盖安装、冷启动、`MainActivity` resumed/top、目标包无 Java/native crash，PASS。

## 7. 实体机 RANDOM 基线

- 当次 `adb devices`：API 36 x86_64 emulator 1、Android 13/API 33 ARM64 实体机 1，均为 `device`；unauthorized/offline 0。文档不记录 serial 或设备标识。
- RANDOM Normal 第一轮（Forward、12 次、每次固定 12 秒）：FAIL。成功样本 5/12，`order=RANDOM`、实际方向、关键字段均完整，promoted 5/5，refresh SUCCESS 5/5，FAILED/UNSUPPORTED/rebuffer/crash 均为 0。gesture→首帧 P50/P90/max 为 6,487/8,645/8,645ms；bind→首帧为 5,871/8,029/8,029ms；bind→first-byte 为 5,395/7,584/7,584ms。
- 第一轮第 6 个样本在脚本退出后安全迟到完成：`order=RANDOM`、FORWARD、字段完整，bind→first-byte 12,457ms、bind→首帧 12,623ms、gesture→首帧 13,242ms，0 FAILED/rebuffer/crash。它证明失败来自超过 12 秒门槛，不是卡死、顺序错误或字段缺失。
- RANDOM Normal 同协议第二轮：FAIL，首条在 12 秒内无首帧，报告 0/12；只读迟到检查时仍为 0 FIRST_FRAME、0 FAILED、0 rebuffer。没有提高超时、清缓存、改网络或继续加热后伪造 PASS。
- RANDOM Fast Reverse 10 次严格复验：FAIL。10 次手势产生 SUPERSEDED 9、UNCHANGED 1、FIRST_FRAME 0；最终 `order=RANDOM`、`direction=REVERSE`、`randomRoundBoundary=true`，跨轮次回到同一项，没有新的 bind、READY 或真实首帧样本。FAILED/UNSUPPORTED/rebuffer/crash 均为 0，但样本不足、RANDOM 与方向不能由成功首帧样本证明，因此脚本正确非零退出。
- warm/mixed 与自然冷区分：第一轮五个成功样本包含 bind→首帧 102ms 的热命中及 5.5–8.0s 长尾；随后自然出现的冷缺页 bind→first-byte 为 12,457ms。只读系统分类确认当前默认网络为已验证但计量的 Wi‑Fi；运行期间自适应策略报告 `NETWORK_NOT_ALLOWED`、preload OFF。Fast 终局为同项 UNCHANGED，不产生可归类的首帧温度样本。未人为清缓存、修改网络/VPN 或制造冷样本。
- Normal 第一轮报告：`build/reports/stage13a/random-swipe-first-frame-normal-forward-20260801-051508.md`；第二轮报告：`build/reports/stage13a/random-swipe-first-frame-normal-forward-20260801-051747.md`；严格 Fast 报告：`build/reports/stage13a/random-swipe-first-frame-fast-reverse-20260801-082110.md`。对应 evidence 已脱敏。
- 13A 补验 Normal Forward 12 次：FAIL，成功样本 4/12；四项全部 `order=RANDOM`、FORWARD、字段完整，promoted 4/4、refresh SUCCESS 4/4，FAILED/UNSUPPORTED/rebuffer/crash 均为 0。gesture→首帧 P50/P90/max 为 1,539/8,156/8,156ms；bind→首帧为 919/7,533/7,533ms；bind→first-byte 为 718/6,931/6,931ms。第五次在固定 12 秒内没有首帧，脚本停止。
- 13A 补验 Fast Reverse 10 次：FAIL，再次产生 SUPERSEDED 9、UNCHANGED 1、FIRST_FRAME 0；最终为 `order=RANDOM`、`direction=REVERSE`、`randomRoundBoundary=true`，跨轮次回到同一项。0 FAILED/UNSUPPORTED/rebuffer/crash，但没有成功首帧样本，不能证明所需字段。
- 补验前后均未修改数据、缓存、网络或 VPN。对 active network 主能力的窄解析确认已验证 Wi‑Fi 且 `NOT_METERED=false`，应用日志一致报告 `NETWORK_NOT_ALLOWED`、preload OFF；补验属于当前 warm/mixed 缓存与自然网络状态，不替代已记录的自然冷长尾证据。
- 补验报告：`build/reports/stage13a/random-swipe-first-frame-normal-forward-20260801-115204.md`、`build/reports/stage13a/random-swipe-first-frame-fast-reverse-20260801-115342.md`；对应 evidence 已脱敏。
- 用户明确免除非计量 Wi‑Fi Normal 复测；既有 Normal 结果继续作为未通过的计量网络/warm-mixed 与自然冷证据，不改写为 PASS。
- Fast Reverse 显式检查点补验：总计10次，批次3/3/3/1，严格 PASS。FIRST_FRAME 2、SUPERSEDED 6、UNCHANGED 2；两个成功样本均为 `order=RANDOM`、REVERSE、字段完整，refresh SUCCESS 2/2，FAILED/UNSUPPORTED/rebuffer/crash 均为0。gesture→首帧 P50/P90/max 为2,180/12,020/12,020ms；bind→首帧为1,630/11,342/11,342ms；bind→first-byte 为1,418/10,890/10,890ms。promoted 0/2，plan age 无可用值，报告仍完整区分未晋升路径。
- Fast PASS 报告：`build/reports/stage13a/random-swipe-first-frame-fast-reverse-20260801-120130.md`；对应 evidence 已脱敏。

### 7.1 2026-08-09 累积生产复核

- `DEFAULT_VIDEO_FEED_ORDER`、`VideoPlaybackUiState` 与 ViewModel `FeedCriteria` 仍共同引用 `RANDOM`；定向 app 测试和 fresh full test 继续覆盖 Loading 前置发布、本会话 LATEST 保留、新会话重置 RANDOM 与空筛选不发播放请求。
- 当前 13C、13B 两个生产开关关闭的同设备 Normal Forward 报告 `build/reports/stage13b/random-swipe-first-frame-normal-forward-20260809-132214.md` 为 12/12 PASS，所有成功样本均为 RANDOM/FORWARD、字段完整、rebuffer/crash 0/0。它是后续累积生产基线，不改写或删除本节历史自然冷/计量网络失败证据。
- 修正 benchmark 单 Fast batch 在 PowerShell `StrictMode` 下被标量化的问题后，当前生产 `build/reports/stage13c/random-swipe-first-frame-fast-reverse-20260809-140948.md` 为连续 10 次严格 PASS：最终 FIRST_FRAME 1、REVERSE 1/1、SUPERSEDED 15、rebuffer/crash 0/0。修复只涉及测试工具的显式 `int[]`，没有改变 13A 生产顺序或手势参数。
- 联合复核 fresh `test` 345/345、lint 220/220、assembleDebug、API 36 emulator 40/40 与当前手机 launch smoke 均 PASS。

结论：新会话 RANDOM 行为、成功样本观测字段与 Fast Reverse 10 次专项基线已成立；历史 13A Normal 失败和用户当时的豁免继续保留，但当前累积生产基线已经单独获得 Normal Forward 12/12 PASS。本阶段事实未被 13B/13C 回归。

## 8. 安全与停止边界

- 日志新增字段仅为枚举、布尔和单调耗时；安全 evidence 会继续脱敏 chatId/messageId/fileId/PID。
- 未新增内容文本、路径、remote id、owner token、设备/网络标识或凭证日志。
- merged debug manifest 复核只含 `INTERNET` 与 `ACCESS_NETWORK_STATE`；`allowBackup=false`，且仍引用 `dataExtractionRules`/`fullBackupContent` 排除规则。
- `local.properties` 仍由 `.gitignore` 排除；敏感日志模式和临时 `[DEBUG-*]` 扫描无命中。
- 一次本地 UI XML 诊断异常曾把一个真实频道显示名短暂打印到工具控制台；该值未写入仓库、报告或 evidence，且未包含 caption、手机号、凭证或媒体数据。后续诊断只输出固定布尔/枚举，原始 UI XML 不再打印。
- 未修改权限、备份、私有存储、内容保护、播放器/PlayerView 数量或缓存结构；下一条预算仍为 256 KiB，当前项区间/LoadControl 保持既有值。
- 未清账号、数据或缓存，未退出账号，未改网络/VPN。
- 实体机验收只覆盖安装、启动和既有网络/缓存条件；benchmark 只清本轮 main/crash logcat buffer。两轮 FAIL 均保留，没有通过重试阈值或环境变更隐藏。
- 未暂存、提交或推送。
- 13A 完成后停止；13B 必须获得新的明确授权。
