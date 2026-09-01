# 优化阶段 13B：唯一下一条预加载 owner 的保留、共享与无缝晋升

日期：2026-08-01；实体机续验：2026-08-09
状态：候选实现、回归测试和真实匹配 owner handoff 均已验证，但同设备 A/B 性能明确失败；生产开关保持关闭。仓库之后经单独授权进入 13C，13C 没有启用本候选

## 1. 阶段合同

### Outcome

候选目标是在用户滑向已经预加载的随机下一项时，保留匹配 `VideoKey + fileId` 的唯一 `NEXT_PRELOAD` owner 和进行中区间请求；`CURRENT_STARTUP` owner 建立后再释放旧 owner，避免 `YIELD`、取消和同 fileId 冷重启。

### Scope

- 领域层预加载 owner handoff 状态与控制边界。
- 播放页目标手势、commit、settle、错误与 release 的 generation 协调。
- player 的唯一下一条 owner、DataSource 当前 owner 建立回调和单播放器绑定门槛。
- telegram 同 fileId 已包含活动请求的可选原位提优。
- 对应 app/player/telegram 单元测试、benchmark 解析、README、验收矩阵和本文档。

### Boundary

- 继续只有一个 ExoPlayer 和一个活动 PlayerView；settle 前不绑定、不发声。
- 继续只有当前项与唯一一个下一/目标项；下一项预算固定 256 KiB。
- 不增加多条/尾部预加载、完整下载、Media3 完整缓存或公共存储。
- 不改变质量选择、移动数据默认策略、缓存上限、Pager 动画、随机轮次算法或账号数据边界。
- ViewModel 不接触 TDLib 类型、`RangeLease`、owner token 或文件路径。
- 本阶段止于候选验证；性能门槛失败后不得启用候选，也不得进入 13C。

### Failure states

- 只有 `VideoKey + fileId + generation` 全部匹配才允许 handoff；相同 key 但质量 fileId 改变时拒绝晋升。
- 反向、目标改变或回弹使旧 generation 失效；迟到的当前 acquire 回调不能恢复旧 owner 或绑定旧视频。
- 当前 acquire 失败时释放预加载 owner，并沿用既有可恢复错误路径。
- 网络、账号、筛选、质量或队列 generation 改变，以及页面退出、后台、seek、logout、完整 release，仍完整释放。
- `supportsStreaming=false` 不建立 owner；任意时刻只有一个 speculative fileId。

### Proof

先以测试稳定复现旧行为，再实现状态机和候选调度；依次运行 telegram/player/app 定向测试、fresh `test`/`lint`/`assembleDebug`、完整 Compose Path B，以及保留数据与网络设置的 RANDOM Normal Forward 真机 benchmark。性能收益未达到合同即回滚生产候选，只保留测试、解析器和证据。

## 2. 前置门槛与红测

开始修改前已确认仓库根目录为 `E:\Telegram Android Developer`、当前分支为 `main`，并检查 `git status`。仓库没有已提交基线，现有文件均属于用户的未跟踪工作；本阶段未覆盖、删除、暂存、提交或 push 用户文件。

完整阅读 `AGENTS.md`、`README.md`、架构/媒体文档、`STAGE12B` 至 `STAGE12F` 与 `STAGE13A_RANDOM_DEFAULT_BASELINE.md` 后，核实 13A 的目标测试、fresh full test/lint/assembleDebug、Compose Path B、RANDOM Normal/Fast 证据均已记录。13A 前置 Proof 完整，因此才进入本阶段。

红测证明两个既有根因：

1. 播放页 `onPageUnstable()` 会立即调用 `stop()`，即使目标正是已有唯一下一项，也会先释放匹配 owner。
2. telegram 调度器在同 fileId 从 `NEXT_PRELOAD` 切到 `CURRENT_STARTUP` 时会取消旧活动请求并重建；旧请求的迟到回调被忽略，形成“取消后重新申请”。

## 3. 候选状态机与所有权顺序

领域边界增加单一 `PreloadOwnerHandoffSnapshot`，由 `VideoPreloadManager` 维护唯一 generation。候选状态流为：

`IDLE → NEXT_WARMING → TARGET_COMMITTED → SHARED_WITH_CURRENT → RELEASED`

目标不匹配、反向/回弹、硬策略变化和完整释放分别进入 `ABANDONED/CANCELLED` 终局。状态事实只在 player 层；ViewModel 只传递领域模型 `IndexedVideo`，不接触 TDLib 或 owner 实现。

候选的严格顺序是：

1. 手势不稳定时开始本 generation 的目标判定，不盲目停止匹配 owner。
2. target prepare 得到最终 fileId 后，只在 key/fileId/generation 全匹配时 commit。
3. settled bind 仍按既有单播放器门槛执行；DataSource 以 `CURRENT_STARTUP` acquire 同 fileId。
4. 当前 lease 已建立并登记到 binding session 后、等待媒体字节前，才通知 handoff 成功并释放旧 `NEXT_PRELOAD` owner。
5. 当前 acquire 失败、目标改变或回调 generation 过期时，释放旧 owner 且禁止迟到恢复。

telegram 候选允许同 fileId、已包含所需范围的活动请求在原请求上提优，保持同一 ActiveRequest 和连续进度；跨 fileId 和不包含范围仍使用既有取消/排队规则。STARTUP 首区间完成后的 CONTINUATION 降级行为保持不变。

## 4. 指标与测试

Debug 脱敏观测新增：`promotionAttempt`、`promotionMatched`、`reusedActiveRequest`、`cancelledBeforeCurrentAcquire`、owner handoff 耗时，以及既有 bind→first byte/READY/first frame。解析器只把明确的 `NEXT_PRELOAD→CURRENT_STARTUP result=REUSED_ACTIVE` 计为 scheduler 复用，不能把 `CURRENT_STARTUP→CURRENT_CONTINUATION` 误计入。证据不保存 owner token、路径、remote id、内容、凭证、设备或网络标识。

新增/扩展测试覆盖：

- 匹配下一项在页面初次 unstable 时不取消；当前 owner 建立前保留 next owner，建立后释放。
- 反向、目标改变、慢拖回弹、快速 A→B→C、迟到 acquire callback 的 generation 隔离。
- 相同 `VideoKey` 但质量 fileId 改变时拒绝错误晋升。
- acquire 失败、网络硬阻断、seek、页面/账号 release、logout 完整释放。
- `supportsStreaming=false` 不 acquire；仅有一个 speculative lease/fileId。
- DataSource 当前 lease 回调严格发生在 acquire 成功之后、等待字节之前。
- 同 fileId 活动请求候选原位提优不 cancel，既有 STARTUP→CONTINUATION 降级不回归。
- 既有 Compose/manager 测试继续证明单 ExoPlayer 与单活动 PlayerView 上限不变。

## 5. 候选主机与 Compose Proof

性能测试前，启用候选的 telegram/player/app 模块测试、fresh full test、lint、assembleDebug 和完整 Compose Path B 均通过。发现性能不达标并恢复生产默认后，又重新执行全部规定 Proof：

- `:telegram:testDebugUnitTest`：PASS。
- `:player:testDebugUnitTest`：PASS。
- `:app:testDebugUnitTest`：PASS。
- `test --rerun-tasks`：PASS，345 个 Gradle task 全部执行。
- `lint --rerun-tasks`：PASS，220 个 Gradle task 全部执行。
- `assembleDebug`：PASS。
- instrumentation Kotlin 编译：PASS。
- Login/Channel/ComposeSmoke Robolectric-Compose：PASS。
- CacheSettings Robolectric-Compose：PASS。
- API 36 AOSP x86_64 emulator Compose UI：PASS，40/40。
- 当前 Android 13/API 33 ARM64 实体机 install+launch smoke：PASS。

## 6. RANDOM 真机 benchmark

第一次报告 `random-swipe-first-frame-normal-forward-20260801-131312.md` 为 0/12；检查发现应用未处于播放页，因此它是无效的 setup 样本，不参与性能比较。

安全进入已登录的播放测试页并从 UI 确认 RANDOM selected 后，使用与 13A Normal Forward 相同的 12 次、每次 12 秒协议得到有效报告：

- 报告：`build/reports/stage13b/random-swipe-first-frame-normal-forward-20260801-131509.md`。
- 严格结果：FAIL，FIRST_FRAME 4/12；成功样本均为 RANDOM/FORWARD、字段完整、promoted 4/4。
- bind→first-frame P50/P90/max：1,027/8,925/8,925ms。
- gesture→first-frame P50/P90/max：1,649/9,549/9,549ms。
- FAILED/UNSUPPORTED/rebuffer/crash：0/0/0/0。
- promotion attempt/matched/terminal：10/0/10；活动请求复用 0，scheduler 明确复用 0，提前取消 0。
- 自适应策略在样本期间因 `NETWORK_NOT_ALLOWED` 为 OFF，没有实际存在可匹配的 NEXT owner；因此没有获得一次真实 owner 晋升样本。

可比较的 13A Normal Forward 基线 bind→first-frame P90/max 为 7,533/7,533ms。本候选为 8,925/8,925ms，P90 与最大值均恶化约 18.5%；既未改善至少 15%，最大值也超过允许的 10% 恶化。样本并非“已完全缓存、指标无可比较性”：仍观察到 bind→first-byte P90 8,822ms，且只有 4/12 首帧成功。

13A Fast Reverse 11,342ms 来自不同方向和 3/3/3/1 快速协议，不能用作 Normal Forward 的有利比较。

### 6.1 2026-08-09 非计量 Wi-Fi 同设备续验

手机重新连接后，只读确认活动网络为已验证的非计量 Wi-Fi，省电模式关闭。当前仓库已经由后续独立阶段实现 13C，因此本轮使用同一份 13C 代码分别构建“13B 开关关闭”与“13B 开关开启”APK；A/B 之间不清数据/缓存、不修改网络/VPN、不退出账号，也不改变 13C、质量、Pager、随机算法或 256 KiB 上限。

benchmark 原入口会无条件执行 warm `am start MainActivity`，在当前导航实现中把已经确认的播放页重置回频道页，造成无效 0/12。入口已修正为：只有尚未安全确认播放页时才启动 Activity；新增受限 `-ReportStage stage13b|stage13c` 参数并保持 stage13c 为默认值。解析器测试锁定“已在播放页时不重置导航”的合同。无效 setup/并发样本不参与 A/B。

关闭 13B 候选的同设备基线：

- 报告：`build/reports/stage13b/random-swipe-first-frame-normal-forward-20260809-132214.md`。
- 严格 PASS，FIRST_FRAME 12/12；RANDOM/FORWARD/字段完整，promoted 12/12。
- bind→first-frame P50/P90/max：630/1,599/1,783ms。
- gesture→first-frame P50/P90/max：1,256/2,228/2,407ms。
- preload YIELD/RESUME：12/12；promotion 0/0/0；rebuffer/crash 0/0。

临时开启两个 13B 开关后的候选：

- 报告：`build/reports/stage13b/random-swipe-first-frame-normal-forward-20260809-132439.md`。
- 严格 FAIL，FIRST_FRAME 11/12（91.7%），另有 SUPERSEDED 10、UNCHANGED 1；RANDOM/FORWARD/成功字段完整，promoted 11/11。
- promotion attempt/matched/terminal：11/11/11；`reusedActiveRequest` 8，明确 `NEXT_PRELOAD→CURRENT_STARTUP REUSED_ACTIVE` 2 次。
- `cancelledBeforeCurrentAcquire=0`；owner handoff P50/P90/max：457/466/467ms。因此“匹配 owner 保留到当前 acquire 后才释放”和“活动请求原位提优”已获得真实证据。
- bind→first-frame P50/P90/max：969/3,033/4,013ms，较同设备关闭候选基线分别恶化约 53.8%/89.7%/125.1%。
- gesture→first-frame P50/P90/max：1,589/3,658/4,632ms；rebuffer/crash 0/0，日志中的 `playerInstances=1`。

候选是后测，缓存条件理论上更有利，但仍丢失 100% FIRST_FRAME、P90 未改善 15%，最大值远超 10% 恶化上限。故无需用 13A 的跨日期数据作有利比较，也不存在“完全缓存、无可比较性”例外；同设备 A/B 已足以否决候选。

## 7. 回滚结论

性能合同要求“没有可靠收益时必须回滚候选”。因此本阶段没有启用 owner 晋升：

- `VideoPreloadManager.PRODUCTION_OWNER_PROMOTION_ENABLED = false`，生产恢复 13A 的 unstable/Loading 停止行为。
- `TelegramFileManager.PRODUCTION_REUSE_CONTAINED_ACTIVE_REQUEST = false`，生产恢复取消并重建同 fileId 提优请求的 13A 行为。
- 两个常量都有回归测试锁定为 `false`；候选状态机与原位提优只能由测试构造函数显式启用，用于保留红测、回归覆盖和未来受控实验。
- benchmark 解析器、严格计数测试和脱敏失败证据保留。

2026-08-09 续验后再次把两个常量恢复为 `false`，重新构建并覆盖安装到手机。回滚后的 telegram/player/app fresh 模块测试、full test、lint、assembleDebug、完整 Compose Path B 和真机 launch smoke 全部通过。

同日 13A/13B/13C 联合复核再次执行 `:telegram:testDebugUnitTest`、`:player:testDebugUnitTest`、`:app:testDebugUnitTest`、fresh full `test` 345/345、lint 220/220 与 assembleDebug，全部 PASS；生产常量测试继续锁定两个开关为 `false`。后续修正的 Fast 单 batch PowerShell `StrictMode` 问题只影响 Fast runner 在发送手势前的数组计数，不影响本阶段 Normal Forward A/B 报告、owner handoff 计数或性能回滚结论。

结论：13B 候选的正确性与真实 owner 交接均已证明，但实体机性能验收失败，生产运行时仍关闭此能力。不得把本阶段写为 owner 无缝晋升已交付。仓库之后经单独授权进入的 13C 也继续保持这两个开关为 `false`。

## 8. 尚未验证与已知限制

- 在非计量 Wi-Fi 下，匹配 `NEXT_PRELOAD` 的真实 owner handoff、当前 acquire 后释放和 `REUSED_ACTIVE`：已验证；性能结果为负收益。
- 严格空缓存 benchmark、真实退出账号流程和真实网络切换：尚未验证；自动化仅覆盖 Fake/状态机释放行为。
- 反向、慢拖回弹和快速 10 次的候选真机 A/B：尚未验证；Normal Forward 已足以触发强制回滚门槛，不继续扩大失败候选的实验范围。
- 当前实体机是 Android 13/API 33 ARM64，并非历史 iQOO 12/OriginOS 6/Android 16；按仓库现有阶段合同执行当前设备 launch smoke。
