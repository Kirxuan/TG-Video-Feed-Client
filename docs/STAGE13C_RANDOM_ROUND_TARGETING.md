# 优化阶段 13C：随机下一轮预生成、轮次边界原子晋升与方向感知目标

日期：2026-08-03；13A/13B/13C 联合复核与实体机补证：2026-08-09
状态：实现、主机/模拟器 Proof、当前手机 install+launch smoke、RANDOM Normal Forward/Reverse、慢拖回弹与连续 Fast Reverse 10 次均通过；当前大队列的真实随机轮次边界尚未验证；未进入 13D

## 1. 阶段合同

### Outcome

- 当前随机轮最后一项播放前，内存中已经存在下一轮及其真实第一项。
- 当前轮最后一项的唯一 next plan 与 256 KiB 预加载目标指向 `upcoming.first`，不再指向旧轮第一项。
- 跨轮 settle 原子晋升已生成的 upcoming round 及其 PlaybackPlan，不在边界现场重新洗牌或重复刷新。
- Pager 越过 snap 中点后，唯一 speculative target 可按实际方向替换原 forward next；settle 前仍不 bind、不发声。

### Scope

- `core/domain` 的随机队列状态与确定性测试。
- app ViewModel 的 round generation token、target/settle 协调和对应 JVM 测试。
- Compose Pager 的 current/upcoming 映射、虚拟 key 与共享测试。
- 复用既有 `VideoPreloadController.setNextVideo` 单目标能力；不修改 player 参数。
- transition benchmark 解析器、脚本测试、README、验收矩阵和本文档。

### Boundary

- 只保存 current round 和一个 upcoming round；晋升后丢弃旧 current，不积累历史。
- 仍只有一个 speculative video owner，不并行预加载 forward/backward。
- 下一条上限仍为 256 KiB；没有修改 Media3、TDLib、Room schema、质量选择、缓存或播放器参数。
- 不持久化随机轮、行为轨迹或完整 PlaybackPlan。
- 阶段 13B 的两个生产 owner/reuse 开关继续为 `false`；本阶段没有把未通过实体机门槛的 13B 候选翻为生产能力。

### Failure states

- 空集合保持 Empty 且不生成 upcoming；单项集合允许跨轮重复，但不建立无意义 next preload。
- 删除、失去访问、筛选或顺序变化会共同失效旧 current/upcoming 和旧 plan generation。
- Room 元数据刷新保留 current/upcoming 既有 key 顺序并替换对象引用；upcoming 同时按最新来源集合复核。
- 新视频沿既有语义进入当前轮随机尾部，并进入 upcoming 的来源集合。
- 删除当前轮最后项后重新计算 index 和真实边界，不越界；删除 upcoming 项后旧计划失效。
- 反向、换向或回弹取消旧 target generation；快速跨页只允许最终 settled key 绑定。

### Proof

按用户给定顺序执行 domain/app/player 测试、fresh 全量 test/lint、assembleDebug，再执行 Compose Path B。实体机仅在设备可达且不破坏用户筛选/数据时验证随机边界。

## 2. 前置核查与红测

开始前完整阅读了 `AGENTS.md`、`README.md`、`ARCHITECTURE.md`、`PRODUCT_SPEC.md`、`ACCEPTANCE_TESTS.md`、`STAGE6`、`STAGE12B` 至 `STAGE12F`、`STAGE13A` 与 `STAGE13B`。仓库根目录是 `E:\Telegram Android Developer`，分支为 `main`；仓库仍没有 commit，所有现有文件均为用户的未跟踪文件，本阶段没有暂存、提交、push、覆盖或删除无关文件。

13C 开始时，13B 的 fresh correctness Proof 和回滚后主机/Compose 证据已有通过记录，但当时真实 owner handoff 尚未出现：有效报告为 promotion attempt/matched/terminal `10/0/10`，生产 owner promotion 与活动请求复用开关均为 `false`。2026-08-09 后续同设备 A/B 已补到 matched `11/11`、提前取消 0 和明确活动请求复用 2 次，证明候选交接正确发生；但 FIRST_FRAME 从关闭候选的 12/12 降到 11/12，bind→首帧 P90/max 从 1,599/1,783ms 恶化到 3,033/4,013ms。故 13B 性能结论仍为 FAIL，两个生产开关继续为 `false`；13C 只交付随机轮次与 target 正确性，没有启用 13B 候选。

红测先用确定性随机源证明旧行为：当前轮最后项的 `nextVideo` 返回旧轮第一项，settle 后才重新 shuffle 出另一个新轮第一项。随后 domain API、ViewModel 边界测试、方向切换测试、Compose current/upcoming 映射测试和 benchmark 新字段测试分别经历预期红灯，再实现到绿灯。

## 3. RandomRoundState

`VideoPlaybackQueue` 现在维护进程内的 `RandomRoundState`：

- `current`：不可变 items 与单调 generation；另有 current index。
- `upcoming`：最多一个不可变 items 与独立 generation。
- `previousBoundaryKey`：边界去重事实，不是轮次历史。

`startRandomSession` 一次生成 current 和 upcoming。生成只操作已索引的 `IndexedVideo` 元数据，不调用消息刷新、媒体下载或预加载接口。`nextEntry()` 在轮内返回下一项，在当前最后项返回 `upcoming.first`；空/单项返回 null。

`settleRandom` 只接受 current 或 upcoming 的合法 generation/index/key。upcoming settle 直接把同一个 `RandomRound` 对象事实晋升为 current，保留 generation 与 items 顺序，再生成唯一的新 upcoming；没有边界二次 shuffle。来源刷新使用复合 `VideoKey(chatId, messageId)` 对账：保留已有顺序、更新对象引用、移除失效项，并把新增项放入随机尾部。

## 4. PlaybackPlan 与 Pager target

`PlaybackPlanToken` 增加 `randomRoundGeneration`。合法性 gate 同时接受当前 current 或唯一 upcoming generation；upcoming 晋升后 token 仍匹配同一 generation，因此已准备计划可由原有原子槽位直接晋升，不能用移除 generation gate 的方式绕过安全检查。筛选、顺序、账号、质量或来源集合变化仍通过既有 queue/account/quality generation 失效旧计划。

随机 Pager 在 UiState 暴露 current items、upcoming items 和当前轮绝对起始页。当前轮末页之后映射到 upcoming，边界 settle 后更新 anchor 而不跳页。媒体身份继续是 `chatId + messageId`；随机虚拟 Compose key 继续加入绝对 pagerPage，避免同视频跨虚拟页 key 冲突。

Compose 只把 `pagerState.currentPage` 视为越过 snap 中点后的 committed page，不把提前变化的 `targetPage` 当成承诺。ViewModel 在 committed target 改变且不再等于既有 forward next 时，先以 `setNextVideo(null)` 释放唯一旧候选，再安装最终目标；player 的单 owner generation 保证迟到回调不能恢复旧 fileId。所有准备都发生在 unstable 期间，真正 bind 仍只在最终 `settledPage`。

## 5. 自动化覆盖

- 每轮每项恰好一次，新轮首项不等于旧轮末项。
- 当前最后项 next 等于 `upcoming.first`，边界晋升不二次洗牌、不重复消息刷新。
- upcoming PlaybackPlan token 晋升后仍合法；stale round/filter callback 不能 bind。
- 空/单项、删除当前最后项、删除 upcoming 项、新增项和筛选变化。
- Room fileId 更新保持 current/upcoming key 顺序并替换对象引用。
- forward、reverse、snap 中点、慢拖回弹、快速换向和仅最终 settled key bind。
- speculative target 切换序列为 `forward → null → committed`；player 测试继续证明 `maxActiveLeases=1`，即任意时刻 speculative fileId 数量不超过 1。
- 随机虚拟 Pager 的复合媒体 key 与绝对页 key 均保持唯一语义。

## 6. 主机 Proof

以下命令退出码均为 0：

- `:core:domain:test`：PASS。
- `:app:testDebugUnitTest`：PASS。
- `:player:testDebugUnitTest`：PASS。
- `:telegram:testDebugUnitTest`：PASS；再次锁定 13B scheduler 生产复用开关为 `false`。
- `test --rerun-tasks`：PASS，345/345 tasks executed。
- `lint --rerun-tasks`：PASS，220/220 tasks executed。
- `assembleDebug`：PASS。
- `scripts/tests/SwipeFirstFrameBenchmark.Tests.ps1`：PASS。

构建仍有既有 Android deprecated API 与 SDK XML 工具版本警告，但没有 test/lint/build failure。

## 7. Compose Path B

- `:app:compileInstrumentationKotlin`：PASS。
- Login/ChannelSelection/ComposeSmoke Robolectric-Compose：PASS。
- CacheSettings Robolectric-Compose：PASS。
- API 36 AOSP x86_64 `CVF_AOSP_API36_X86_64`：PASS，40/40 tests；instrumentation target 继续排除 `.so`。
- 2026-08-09 当前 Android 13/API 33 ARM64 手机 install+launch smoke：PASS；`MainActivity` resumed/top，未发现目标包 crash。

因此 Compose Path B 四项 Proof 已通过。真机完整 instrumentation 仍不作为 Vivo/OriginOS 门槛；本次没有把 launch smoke 冒充完整 instrumentation。

## 8. Transition benchmark

解析器新增 `RandomRoundBoundaryPlanPromotedCount/EligibleCount/RatePercent`，只在成功样本同时满足 `randomRoundBoundary=true` 且 `promoted` 合法时计入，用于证明随机边界计划是否原子晋升。runner 默认标题和输出目录保持 `build/reports/stage13c`，另提供受限的 `-ReportStage stage13b|stage13c` 供 13B 续验证据隔离；继续保持 RANDOM、方向、字段完整性、脱敏和非零失败门槛。runner 在已经安全确认播放页时不再发送 launcher intent，避免 benchmark 自己把当前导航重置为频道页。

2026-08-09 的 13B 同设备 A/B 同时为当前 13C、13B 生产开关关闭的基线补充了真机证据：`build/reports/stage13b/random-swipe-first-frame-normal-forward-20260809-132214.md` 为 FIRST_FRAME 12/12、RANDOM/FORWARD、promoted 12/12、rebuffer/crash 0/0，严格 PASS。

同日继续补齐方向专项：

- Normal Reverse 第一次预热样本为 FIRST_FRAME 12/12，但首项是同页 `UNCHANGED`，其余 11 项为 REVERSE，因此 `random-swipe-first-frame-normal-reverse-20260809-135705.md` 按严格门槛保留 FAIL，不冒充通过。稳定后重跑 `random-swipe-first-frame-normal-reverse-20260809-135906.md` 严格 PASS：FIRST_FRAME 12/12、REVERSE 12/12、promoted 12/12、rebuffer/crash 0/0、`playerInstances=1`。
- 慢拖使用连续 `DOWN/MOVE/UP` 跨过 snap 中点后返回原页。脱敏终局为 forward target `SUPERSEDED=1`、最终 `UNCHANGED=1`、新 FIRST_FRAME/bind=0、FAILED/crash=0、`playerInstances=1`，且唯一预加载从 YIELD 恢复为既有 262144 bytes。报告为 `build/reports/stage13c/random-slow-drag-rebound-20260809-140055.md`。
- 连续 Fast Reverse 10 首次在发手势前暴露 runner 的 PowerShell `StrictMode` 缺陷：单一 batch 被解包为标量后读取 `.Count`。先加入会失败的静态回归断言，再把 `$fastBatches` 显式声明为 `int[]`；脚本测试转绿。修复后 `random-swipe-first-frame-fast-reverse-20260809-140948.md` 严格 PASS：单批 10 次、最终 FIRST_FRAME 1、REVERSE 1/1、SUPERSEDED 15、promoted 1/1、rebuffer/crash 0/0。
- 上述 normal/fast 报告的随机边界均为 0。当前用户筛选约 5810 项，不能通过少量安全手势到达 forward upcoming 边界；按阶段合同，真实随机轮次边界仍写“尚未验证”，没有清数据、改筛选、退出账号或修改网络/VPN制造小队列。自动化的确定性边界、upcoming token 晋升及 stale callback gate 已通过。

## 9. 安全与架构检查

- 没有修改权限、备份规则、TDLib、Room schema、质量策略、缓存上限、Media3 参数或播放器实例模型。
- 唯一下一条预算仍由既有策略限制为 256 KiB；没有批量刷新、完整下载、公共存储或第二 speculative owner。
- 随机状态与 PlaybackPlan 仅驻留内存，不包含正文、凭证、路径、TDLib 对象或 owner token。
- benchmark 证据仍脱敏；本阶段没有读取或输出 `local.properties` 的真实值。

## 10. 结论与停止点

13C 的队列、generation、Pager target 和自动化正确性实现完成，主机、emulator 与当前手机 install+launch smoke 均通过；当前 13C 且 13B 关闭的 RANDOM Normal Forward 12/12、Normal Reverse 12/12、慢拖跨中点回弹与连续 Fast Reverse 10 均 PASS。真实大队列 forward 随机轮次边界仍尚未验证；确定性 domain/ViewModel/Compose 测试已证明同一 upcoming generation 与 PlaybackPlan 的原子晋升。不能安全构造小队列时仍不得破坏用户筛选或数据。

本阶段到此停止，不自动提交，不进入 13D。
