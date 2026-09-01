# 优化阶段 12F：自适应播放策略、性能回归工具和滑动首帧最终验收

日期：2026-07-30
状态：完成。12F 代码、主机 Proof、Compose Path B、Android 13 当前实体机性能协议与 install/launch smoke 均已完成；仓库所有者于 2026-07-30 明确指定只以当前手机为实体机门槛，并免除原 iQOO/Vivo 专属 smoke 与所有真实网络/UID 流量实验。严格空缓存、真实退出账号和真实 decoder 不支持样本仍未执行，但按既定安全边界不属于本阶段关闭阻断项

## 1. 阶段合同

### Outcome

形成可长期回归的滑动性能系统：只用网络计费状态、低存储/低内存、用户质量选择、当前项缓存命中、最近五个 bind→首帧样本、首帧/rebuffer/失败状态，在内存中决定唯一下一条的 OFF、CONSERVATIVE、NORMAL 状态；同时提供一键、脱敏、可重复、样本不足必失败的实体机性能采样。

### Scope

- 扩展领域预加载策略与 Android 设备信号源。
- 在现有单例播放器和唯一下一条预加载管理器间接入内存状态机。
- 增加策略 Fake 单测、预加载集成单测、PowerShell 分位数/解析/脱敏测试。
- 增加 `scripts/run-swipe-first-frame-benchmark.ps1` 与解析模块。
- 汇总 12A→12F 指标、最终矩阵、Proof、安全审计、限制与回滚。

### Boundary

- 不新增产品功能、权限、依赖、播放器、公共缓存或第二份完整媒体缓存。
- 不改变用户质量选择，不保存行为轨迹，不使用设备型号、账号、频道或内容名称决策。
- 不清账号、数据库或缓存，不退出真实账号，不切换网络，不关闭或修改 VPN/Clash Meta。
- 不自动完整下载，不扩大为多条或 AGGRESSIVE 预加载。
- 2026-07-30 仓库所有者更新验收边界：当前 Android 13 ARM64 手机是唯一实体机目标；原 iQOO/Vivo 专属 smoke 和真实网络切换/UID traffic 不再要求。历史“尚未测量”的事实继续保留，但不再列为 12F 未完成项。

### Failure states

- 离线、网络不允许、低存储、低内存、省电、温度达到 MODERATE、网络 generation 改变、当前项未出首帧、连续两次失败或 rebuffer：立即 OFF/让路。
- 近期 P90 超过 650ms、未有足够自然冷样本、处于恢复期或用户明确允许的计费网络：CONSERVATIVE。
- 非计费 Wi-Fi、当前项已出首帧、无失败，且满足恢复 hysteresis：NORMAL。
- benchmark 未安全确认播放页、不是实体机、包未安装、样本不足、目标包 crash、FAILED/UNSUPPORTED 或门槛不满足：非零退出，不生成假 PASS。

### Proof

策略/脚本测试 → fresh `test`/`lint`/`assembleDebug` → instrumentation 编译 → 两组 Robolectric-Compose → API 36 x86_64 emulator 38 项 Compose UI → 实体机 install+launch smoke → 脱敏实体机 normal/fast 协议 → manifest/备份/日志/存储审计。

## 2. 12A→12F 最终指标总表

下表使用每阶段最终保留的正常滑动协议。所有分位数均为 P50/P90/max，单位 ms；12C 为文档中两轮最终组合统计。阶段间真实内容、缓存和网络并非严格同一实验室输入，因此同时保留绝对值、相对值和失败样本，不能把跨阶段差异解释为单一代码因果。

| 阶段 | FIRST_FRAME | gesture→首帧 | bind→首帧 | rebuffer | 流量 | 最终失败结果/限制 |
|---|---:|---:|---:|---:|---|---|
| 12A 原始基线 | 12/12 | 1089/1737/2115 | 212/853/1243 | 0 | 尚未验证（未单独采集 UID bytes） | 最终协议 0 FAILED/0 crash；P90 超过 1400ms 建议线 |
| 12B plan promotion | 12/12 | 818/838/844 | 212/240/241 | 0 | 尚未验证 | 最终协议 0；另有自然冷压力 3795/10102/13137ms，2 个样本超过 10s |
| 12C range scheduler | 24/24（两轮） | 806/829/843 | 195/225/231 | 0 | 尚未验证（next start 12、cancel 0，不等于 bytes） | 两轮最终协议 0 FAILED/0 crash；两阶段小头窗已在该阶段否决 |
| 12D Media3 startup | 12/12 | 694/839/857 | 89/226/252 | 0 | 尚未验证 | 最终协议 0；独立负向候选曾出现约 63s TIMEOUT 与约 87s 的 1 次 rebuffer，未并入最终参数 |
| 12E Pager/Surface | 12/12 | 719/865/889 | 88/229/259 | 0 | 尚未验证 | 最终协议 0 FAILED/0 crash；视觉人工验收通过 |
| 12F 最终 256 KiB | 12/12 | 868/1136/1217 | 241/506/581 | 0 | 尚未验证（设备未提供可安全聚合的 UID netstats） | PASS；0 FAILED/0 crash，promoted 12/12，yield/resume 12/12 |

12E/12F 已有的主要体感指标：

| 阶段 | release→首帧 P50/P90/max | target-known→首帧 P50/P90/max |
|---|---:|---:|
| 12E | 565/708/738 | 521/662/689 |
| 12F | 711/973/1063 | 663/921/1016 |

12F 相比 12A：gesture→首帧 P50 改善 20.3%，P90 改善 34.6%；绝对门槛 P50≤900ms、P90≤1400ms、bind→首帧 P90≤650ms 均满足。12F 相比 12E 的本轮 P50/P90 和 release/target 指标变慢，不能隐藏：本轮自然内容/缓存/网络不同，且 UID 流量无法安全聚合，所以结论仅为“12F 达到 12A 相对门槛和绝对建议线”，不是“每个阶段单调变快”。

## 3. 每阶段关键改变

| 阶段 | 关键改变 |
|---|---|
| 12A | 建立脱敏 gesture/bind/首帧基线与按 VideoKey 隔离的 transition 状态机。 |
| 12B | 删除稳定后固定 250ms；target 提前准备、settled 最终 bind，PlaybackPlan 原子晋升。 |
| 12C | 四级 TDLib range 优先级、同 file owner 协调、受限合并、抢占/恢复和单调时钟等待。 |
| 12D | 单 ExoPlayer 复用生命周期、首帧分段、decoder/surface/实例观测；否决无证据参数。 |
| 12E | snap 中点空间 hysteresis、单 Pager PlayerView、ticker 重组隔离、占位与 surface 稳定。 |
| 12F | 三态内存策略、网络 generation/低内存/计费信号、首帧/rebuffer反馈、可重复 benchmark 与最终验收。 |

## 4. 12F 自适应策略

### 4.1 输入与边界

- 输入仅含 `NetworkTransport`、是否计费、power save、低存储、低内存、thermal、匿名内存 network generation、用户质量偏好、当前 64 KiB 连续缓存命中、最近最多 5 个 bind→首帧耗时、失败/rebuffer/首帧状态。
- `qualityPreference` 只被观察以保证决策与用户设置同 generation；策略从不写回或替换 AUTO/DATA_SAVER/HD_720/ORIGINAL。
- 状态、样本、失败 streak 和 network generation 全部只驻留内存；DataStore 没有新增行为轨迹。
- 移动数据或计费网络默认 OFF；只有用户既有显式开关允许时，最多进入 CONSERVATIVE，仍只有唯一下一条。

### 4.2 离散状态与 hysteresis

| 状态 | 最大下一条前缀 | 进入条件摘要 | 恢复条件 |
|---|---:|---|---|
| OFF | 0 | 硬阻断、网络变化、当前无首帧、连续 2 次失败、rebuffer | 当前首帧完成且硬阻断解除；降级立即生效 |
| CONSERVATIVE | 256 KiB | 最近 P90>650ms、自然冷样本少于 3、计费网络显式允许、恢复中 | 非计费 Wi-Fi 下连续 2 个 normal-eligible 首帧 |
| NORMAL | 256 KiB | 非计费 Wi-Fi、当前稳定、无近期失败，且通过恢复 streak | 任一硬阻断或长尾立即降级 |

CONSERVATIVE 与 NORMAL 有意保留同一 256 KiB 上限。真机 64 KiB 候选仅完成 5/12 后停滞，第二轮虽 12/12 但 P50/P90 为 1121/1622ms；128 KiB 候选只完成 8/12。继续用未证明的小前缀会把“保守”变成首帧完成率回退，因此本阶段的安全收缩来自 OFF 让路、硬阻断和恢复 hysteresis，而不是伪造一个更小但已被实测否决的字节档位。没有增加 AGGRESSIVE。

### 4.3 可测试性

领域状态机 Fake 测试覆盖当前项让路、缓存 miss 恢复、五样本 P90 长尾、网络切换、低资源、连续失败、rebuffer、移动/计费默认关闭、显式 opt-in 上界和质量不被覆盖。预加载管理器 Fake 测试覆盖 OFF 取消、CONSERVATIVE/NORMAL 恢复、当前项不稳定清理 stale target、选择后 fileId 一致、unsupported 不请求和缓存命中范围。

## 5. 候选失败与最终选择

| 候选/尝试 | 结果 | 处理 |
|---|---|---|
| benchmark 首次实现 | 在采样前因 PowerShell Nullable UID 被拆箱后访问 `.Value` 失败 | 修正；脚本测试增加真实空值路径，不计为性能样本 |
| 64 KiB 第一轮 | 5/12；一个 bind 约 9507ms，后续样本超过 12s，0 rebuffer/crash | 否决，失败报告保留在 `build/reports/stage12f` |
| 64 KiB 第二轮 | 12/12，但 gesture 1121/1622/5121、bind 328/995/4500，较 12A P50/P90 为 -2.9%/+6.6% | 否决 |
| 128 KiB | 8/12；成功样本 gesture 870/1266/1266、bind 244/643/643，0 rebuffer/crash | 因首帧完成率失败而否决，不能只引用成功样本的相对改善 |
| 256 KiB 最终 | 12/12；gesture 868/1136/1217、bind 241/506/581；0 rebuffer/crash | 接受；保持 12C→12E 已证明的单下一条上限 |
| emulator 首次最终套件 | 37/38；1 个 Login Compose 测试在 `setContent→Espresso.onIdle` 超时 120s，无 ANR/OOM/crash | 保留失败；隔离复跑 PASS，随后完整复跑 38/38 PASS |

## 6. 性能回归脚本

入口：

```powershell
.\scripts\run-swipe-first-frame-benchmark.ps1 `
    -Serial <physical-device-serial> `
    -SwipeCount 12 `
    -PerSwipeTimeoutSeconds 12 `
    -Mode Normal `
    -SkipBuild
```

快速连滑使用 `-SwipeCount 10 -Mode Fast`。脚本验证实体机、安装包和安全播放页语义；默认不清数据、不清缓存、不改网络/VPN，只清本轮 main/crash logcat buffer。无法安全进入播放页时停止并提示人工进入，不执行盲点。报告和脱敏 evidence 只写 `build/reports/stage12f`。

输出包含 outcome、gesture/release/target/settle/bind/first-frame 分段、nearest-rank P50/P90/max、promoted、preload yield/resume、rebuffer、目标包 crash 和可用时的 UID traffic delta。设备未暴露可安全聚合的 UID netstats 时必须写“尚未验证”，不能以 0 bytes 代替。正常模式样本不足或任何安全失败返回非零；Fast 模式要求至少最终可见项首帧成功且 stale 全部安全结束。

## 7. 最终测试矩阵

| 项 | 结果 | 证据与限制 |
|---|---|---|
| A 正常暖/混合缓存 12 次 | 通过 | FIRST_FRAME 12/12；最终报告 `swipe-first-frame-normal-20260730-144442.md` |
| B 快速连滑 10 次 | 通过 | 最终可见项 FIRST_FRAME 1，SUPERSEDED 18，FAILED 0，rebuffer/crash 0 |
| C 慢拖不跨页 | 通过 | 最终 APK：UNCHANGED 1，bind/prepare 0，rebuffer/crash 0 |
| D 反向滑动 | 通过（12F 当前手机） | 最终 APK 跨页后立即反向：SUPERSEDED 2、UNCHANGED 1，preload yield/resume 1/1，FAILED/rebuffer/crash 0/0 |
| E 前后台 | 通过 | Home→前台恢复播放页且 MainActivity top-resumed；返回频道再进入通过；0 rebuffer/crash |
| F 暂停/继续/seek | 通过 | 最终 APK 暂停/继续语义往返且 0 rebuffer/crash；同一当前手机的 12E seek 已证明 CURRENT_SEEK→CURRENT_CONTINUATION、rebuffer 0，12F fresh 定向测试再次证明 seek 不计普通 rebuffer。最终 APK 的重复 live seek 因自然当前项持续加载而安全停止，没有伪造第二次成功 |
| G 网络状态变化 | 免验（用户明确更新边界） | Fake 仍覆盖 network generation/计费/离线立即 OFF；不再执行真实切换或 UID traffic。VPN/Clash Meta、Wi-Fi、移动数据均未关闭或修改 |
| H 非流式/错误视频 | 通过（自动化 + 既有同机证据） | Fake/Compose 覆盖 supportsStreaming=false、decoder unsupported、timeout、refresh fallback；既有当前手机 non-streaming 证据保留。真实网络 timeout 属免验范围，真实 decoder unsupported 未另造样本 |
| I 设置变化 | 通过（12F 当前手机 + 自动化） | 当前手机依次确认 AUTO→DATA_SAVER→HD_720→ORIGINAL，随后恢复原 DATA_SAVER；fresh 测试证明质量变化使旧 plan generation 失效、错误视频 plan 不能晋升；crash 0 |
| J 账号与释放 | 通过（按合同使用 Fake） | Fake 覆盖退出停止请求、播放和账号数据清理；未获真实退出授权，因此不退出真实账号，真机退出事实仍为未执行 |
| K 缓存场景 | 通过（无需清用户缓存） | 暖/混合 12/12；自然冷压力由 64/128 候选真实形成并保留失败。严格空缓存未获授权且非最终门槛，没有擅自清理 |

## 8. 最终性能门槛

| 门槛 | 实际 | 结论 |
|---|---:|---|
| FIRST_FRAME 100% | 12/12 | PASS |
| gesture→首帧 P50≤900ms | 868ms | PASS |
| gesture→首帧 P90≤1400ms | 1136ms | PASS |
| bind→首帧 P90≤650ms | 506ms | PASS |
| 相比 12A P50 改善≥20% | 20.3% | PASS，余量较小 |
| 相比 12A P90 改善≥15% | 34.6% | PASS |
| 无新增 rebuffer/crash/错绑 | 0/0/未观察到 | PASS |
| 单播放器、单下一条、无双完整缓存 | 保持 | PASS（静态边界 + 自动化） |

结论：最终协议为 PASS，但有三项限制必须与结论同时出现：阶段间不是严格相同内容/网络输入；12F 本轮慢于 12E；UID 流量尚未验证。因此不能把本结果宣称为所有网络与自然冷内容上的确定上界。

## 9. 主机、模拟器与实体机 Proof

### 主机

- `scripts/tests/SwipeFirstFrameBenchmark.Tests.ps1`：PASS。
- `gradlew.bat test --rerun-tasks`：`BUILD SUCCESSFUL`，345/345 Gradle tasks executed。最终 test-results 聚合为 69 个 XML、498 次执行、0 failure/0 error/0 skipped。外层工具在 120 秒边界返回 timeout 标记，但 Gradle 已先输出完整成功结论；没有将外层标记伪装成 Gradle failure。
- `gradlew.bat lint --rerun-tasks`：PASS，220/220 tasks；TDLib strip 与 Android deprecated callback 为 warning。
- `gradlew.bat assembleDebug`：PASS，190 tasks。
- `:app:compileInstrumentationKotlin`：PASS。
- 两组指定 Robolectric-Compose：PASS。
- 当前手机补验后的 fresh 定向回归：`VideoPlaybackViewModelTest.qualityPreferenceChangeInvalidatesPreparedPlan`、`preparedPlanForAnotherVideoCannotBePromoted`、`PlaybackSessionMetricsTest.seekBufferingAndPausedBufferingAreNotCounted` 与完整 `PlaybackTransitionMetricsTest` 均 PASS；app/player 两条 Gradle 命令分别 `BUILD SUCCESSFUL`。

### API 36 x86_64 emulator

- 最终完整复跑：38/38 PASS。
- 第一次：37/38，单个 Login Compose `Espresso.onIdle` 120s timeout，无 ANR、OOM 或目标包 crash；隔离复跑 PASS 后完整复跑通过。该失败没有删除或改写。
- instrumentation target APK 继续排除 `.so`；没有在 emulator 运行 TDLib native smoke。

### 实体机

- Android 13 ARM64 实体机保留数据安装、冷启动、MainActivity resumed/top、目标包无 crash：PASS。
- 仓库所有者明确指定该当前手机为 12F 唯一实体机门槛；原 iQOO/Vivo 专属 smoke 已免除，不再是尚未完成项。
- 当前手机补验：反向手势 stale 取消通过；四种质量选择逐项确认并恢复原值；目标包 crash 0。
- 补充聚合证据位于 `build/reports/stage12f/current-device-final-supplement.md`；只含计数、状态和测试层级，不含 Telegram 内容或设备/网络标识。
- 未运行已知会被 OriginOS `fast_freezer`/`single-cleaner` 杀死的完整 Vivo instrumentation，也不再计划补跑。

## 10. 安全审计

| 检查 | 结果 |
|---|---|
| 有效权限 | 仅 `INTERNET`、`ACCESS_NETWORK_STATE`，共 2 项 |
| 备份 | `allowBackup=false`；`dataExtractionRules`/`fullBackupContent` 均引用规则，root 全排除 |
| Release 性能日志 | 12F 的 4 个详细 Log 调用均位于含 `BuildConfig.DEBUG` 门控的文件；不持久化 |
| 报告脱敏 | 12 个 12F 报告/evidence 扫描 0 个未脱敏 chat/message/file/owner/path/network/凭证/手机号命中 |
| 公共存储 | 12F 改动中 0 个 MediaStore/external/DCIM/Movies/Downloads 调用 |
| 完整文件自动下载 | 无；下一条最大 256 KiB，unsupported 不请求，Media3/TDLib 双完整缓存未建立 |
| owner token | 当前与下一条 lease 都受保护；最后 owner 才允许取消/删除；全量单测通过 |
| 退出账号 | Fake/Repository/FileManager 覆盖停止请求、唤醒等待、清理保护与账号数据；真实退出未执行 |
| 依赖 | 未修改 `gradle/libs.versions.toml`，未新增依赖或非稳定版本 |
| 凭证 | `local.properties` 继续被忽略；未读取或输出真实值；自动化未使用真实验证码/密码 |
| VPN/Clash Meta | 未关闭、未修改；benchmark 明确保持网络/VPN 不变 |

## 11. 尚未验证

- 严格空缓存样本：未获授权，不清用户缓存；自然冷候选证据已满足 12F 当前门槛。
- 真实 decoder unsupported 样本：自动化已覆盖，没有为验收寻找或制造真实错误媒体。
- 真实退出账号；未获明确授权，不自行退出。
- 30 分钟以上长期热态观测；作为维护项而非 12F 阻断项。

以下两项是仓库所有者明确免验，不属于“尚未完成”：原 iQOO/Vivo 专属 smoke；真实网络切换与 UID traffic。历史表中的流量“尚未验证”继续表示没有测得 bytes，不能改写为 0。

## 12. 回滚方案

若长期观测发现自适应策略导致首帧或请求回归：

1. 保留 benchmark 与文档证据，先将 `VideoPreloadManager`/`VideoPlayerManager` 对 `AdaptivePreloadController` 的接入回退到阶段 12E 的直接策略调用。
2. 删除 `AdaptivePreloadPolicyManager` 和 Android low-memory/network-generation 扩展，恢复阶段 12E `DevicePreloadSignals`；不改用户 DataStore，因为本阶段没有持久化策略状态。
3. 保留单 ExoPlayer、唯一下一条 256 KiB、当前项优先、TDLib owner token 与阶段 12C 调度边界。
4. 重新执行 fresh host、完整 Path B 和同一 benchmark 协议；不通过清账号/缓存或关闭 VPN 来制造对照。

仓库没有提交基线，因此回滚必须按上述文件级变更审阅执行，禁止使用 `git reset --hard` 或 `git clean`。
