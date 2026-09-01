# 优化阶段 13F：Pager settle 单变量与阶段 13 最终验收

日期：2026-08-10
状态：**候选已安全回滚，但最终验收有保留，不能签署为全量 PASS。**生产保留 Compose 默认 Pager snap；阶段 13 自动化、API 36 模拟器、当前 Android 13 实体机启动、RANDOM 两轮正常与快速反向均通过。严格同媒体/同缓存 13A→13F 因果 A/B、实体机受保护页 pause/resume/seek 和 UID 总流量仍为尚未验证；既有后台行为是暂停并停止预加载，不是本次 Failure state 字面要求的 full release。

## 1. 阶段合同与最终结论

本阶段只允许评估 Pager release→settle 动画，不允许提前 bind/发声、增加播放器、扩大预加载、改变 TDLib 区间、质量、缓存、权限、Room 或 DataStore。开始前已复核 13A–13E 的生产常量、失败候选、Proof 与真机报告；13B 和 13D 的失败能力都保持关闭，没有未定位的前置生产失败。

单变量实验只评估一个稳定候选：`PagerDefaults.flingBehavior(snapAnimationSpec = tween(360))`。候选把 release→settle P90 从新鲜 13E 默认值约 470 ms 降到 375–376 ms，但两轮现代 jank 为 11.66%/10.79%，高于默认基线 9.89%，P90/P95 帧耗时也从 11/19 ms 增至 15/34 ms 和 16/30 ms。因此按“jank 不得增加、无安全收益恢复原动画”否决候选。

最终生产重新使用 Compose 默认 Pager fling，没有 `PagerDefaults`、自定义 `flingBehavior`、360 ms 常量或候选专用 Compose 测试。最终保留的 13F 变更只有 benchmark 报告阶段、脚本回归断言和收口文档；应用运行时没有 13F Pager 参数变更。由于下述未验证项和后台释放合同差异，本报告完成“评估、否决、回滚和证据收口”，不把阶段 13 最终验收写成无条件通过。

## 2. 13A→13F 阶段目标与最终保留修改

| 阶段 | 目标 | 最终保留 |
|---|---|---|
| 13A | 新会话默认 RANDOM，并补齐 order/direction/round、release/target/settle、首字节/READY/首帧观测 | `DEFAULT_VIDEO_FEED_ORDER=RANDOM`、严格 RANDOM runner 和脱敏指标；历史自然冷 FAIL 原样保留 |
| 13B | 评估唯一下一条 owner 晋升和同 fileId 活动请求复用 | 候选状态机、测试和观测可用于受控实验；生产 `PRODUCTION_OWNER_PROMOTION_ENABLED=false`、`PRODUCTION_REUSE_CONTAINED_ACTIVE_REQUEST=false` |
| 13C | 预生成 current/upcoming 两轮随机队列，并在轮次边界原子晋升 | `RandomRoundState`、单调 generation、真实方向和 snap 中点 hysteresis；始终只保留一个 speculative target |
| 13D | 分类 startup range，并评估 tail64/tail128/head512 | Debug 分类与安全上限测试；生产 `STARTUP_RANGE_CANDIDATE=BASELINE`、head 256 KiB、tail 0、额外 speculative 字节 0 |
| 13E | `VideoKey` 消息解析 single-flight、3 秒可见软期限、索引回退与一次透明 FILE_UNAVAILABLE 恢复 | 当前项和唯一下一项共享解析；普通失败回退，真实旧引用最多刷新/重绑一次；迟到结果受 generation/key 隔离 |
| 13F | 单变量缩短 release→settle，并完成阶段 13 最终验收 | 自定义 360 ms 动画已回退；保留默认 Pager、`stage13f` 报告分类、失败/最终证据与本报告 |

## 3. 失败候选与否决原因

| 候选或失败样本 | 证据 | 否决原因 | 生产状态 |
|---|---|---|---|
| 13B owner promotion + contained active-request reuse | 关闭候选 12/12，bind→首帧 P90/max 1,599/1,783 ms；开启候选 11/12，3,033/4,013 ms | FIRST_FRAME 非 100%，P90 约恶化 89.7%，max 约恶化 125.1% | 两个生产开关均为 `false` |
| 13D tail64/tail128 | 有效 BASELINE 中 tail startup miss 为 0 | 未满足进入真机 A/B 的前置条件，不能凭理论收益启用 | tail 0 |
| 13D head512 Wi-Fi | prime 窗口多次 `NO_PROGRESS`，无法形成两轮可比较样本 | 没有 15% 收益证据，且每个候选理论增加 256 KiB | head 256 KiB |
| 13F 360 ms Pager snap | 报告 `012450`/`012553`；release→settle P90 375–376 ms | 虽较默认约改善 20%，但现代 jank 11.66%/10.79%，P90/P95 帧耗时均回退 | 已删除自定义 fling，恢复 Compose 默认 |
| 13F 默认配置预热前失败 `013814` | FIRST_FRAME 1/12，旧视频在第一手势前已播放约 120 秒并累计一次 12.4 秒 rebuffer | 不是滑动后新 key 的 rebuffer；该轮仍按 runner 规则 FAIL，不纳入正式两轮 | 报告保留；随后不改配置完成两轮有效协议 |

13A 的 4/12、5/12 自然冷/计量网络报告和 13E 的早期媒体长尾报告不是被删除的候选；它们继续作为真实限制和 runner 非零失败证据。

## 4. 13A 基线、13E 新鲜默认与 13F 最终指标

### 4.1 RANDOM Normal Forward

| 样本 | FIRST_FRAME | release→settle P50/P90/max | bind→首帧 P50/P90/max | release→首帧 P50/P90/max | refresh P50/P90/max | rebuffer/crash |
|---|---:|---:|---:|---:|---:|---:|
| 13A 历史自然冷 `20260801-115204` | 4/12，FAIL | 469/471/471 ms | 919/7,533/7,533 ms | 1,388/8,003/8,003 ms | 21/35/35 ms | 0/0 |
| 13A 累积生产基线 `20260809-132214` | 12/12，PASS | 469/470/472 ms | 630/1,599/1,783 ms | 1,100/2,072/2,254 ms | 30/47/98 ms | 0/0 |
| 13E 新鲜默认 `20260810-011909` | 12/12，PASS | 469/470/471 ms | 122/1,398/4,910 ms | 591/1,871/5,382 ms | 25/44/52 ms | 0/0 |
| 13F 最终默认轮 1 `20260810-014233` | 12/12，PASS | 469/471/471 ms | 502/1,193/1,325 ms | 971/1,663/1,796 ms | 18/58/86 ms | 0/0 |
| 13F 最终默认轮 2 `20260810-014319` | 12/12，PASS | 469/469/471 ms | 458/619/1,578 ms | 928/1,089/2,048 ms | 16/48/80 ms | 0/0 |

最终两轮 promoted 均为 12/12；preload yield/resume 为 16/16、14/14；speculative requested/completed extra bytes 均为 0。首区间覆盖仍为每个实际候选最多 262,144 bytes，只存在当前项和唯一下一项。

13F 两轮相对 13A 累积生产基线的 bind→首帧 P90 表面算术分别低 25.4% 和 61.3%，max 分别低 25.7% 和 11.5%；相对历史自然冷 7,533 ms 的差异更大。但是 RANDOM 抽到的媒体、缓存温度和自然网络窗口不同，不能把这些百分比写成严格因果 A/B。它们只证明同设备、相同生产开关下的方向性门槛；严格同媒体、同缓存、同网络的“至少 15%”因果验证为**尚未验证**。

### 4.2 Pager 候选与 jank

| 配置 | release→settle P90/max | 现代 jank | legacy jank | frame P50/P90/P95/P99 |
|---|---:|---:|---:|---:|
| 13E 默认 clean gfx | 470/471 ms | 9.89% | 12.76% | 5/11/19/61 ms |
| 360 ms 候选轮 1 | 376/376 ms | 11.66% | 21.26% | 5/15/34/69 ms |
| 360 ms 候选轮 2 | 375/376 ms | 10.79% | 22.06% | 5/16/30/73 ms |
| 恢复默认后的 13F 最终轮 1 | 471/471 ms | 10.03% | 14.43% | 5/12/28/69 ms |

候选 release→settle 达到约 20% 改善，但 jank 与帧耗时均回退，所以收益不安全。最终配置没有修改 Pager 动画，因此“修改后 release→settle 至少改善 15%”的条件不适用；默认值恢复到 469–471 ms。

### 4.3 快速反向与边界

最终 Fast Reverse 10 次报告 `20260810-014359` 严格 PASS：FIRST_FRAME 1、SUPERSEDED 17、FAILED/UNSUPPORTED/UNCHANGED 0，最终方向 REVERSE，bind→首帧 107 ms，release→settle 445 ms，rebuffer/crash 0/0。慢拖未跨中点得到 UNCHANGED 1、FIRST_FRAME/FAILED/bind terminal 0；正向后立即反向得到旧 FORWARD target `SUPERSEDED=1`、最终原页 `UNCHANGED=1`、新 bind terminal 0。

13F 两轮正常与快速轮没有自然到达 random round boundary。开始候选前、相同最终生产配置的 13E 新鲜基线 `20260810-011210` 曾在 12 次正常滑动中自然命中 1 次边界并 PASS；13F 没有清数据、改筛选或退出账号制造边界，因此本轮边界实体机复验写为**尚未验证**。

## 5. warm/mixed 与自然冷限制

- 所有 13F 运行都保留现有账号、TDLib 数据、应用缓存、质量、网络和 VPN；没有清缓存制造冷样本。
- 正常两轮包含缓存命中与非命中混合：第一轮 first uncached HEAD/TAIL/NONE 为 4/1/7，第二轮为 2/1/9；因此属于 warm/mixed，不是严格自然冷。
- UID netstats 无法在设备上安全聚合，完整网络字节为**尚未验证**。runner 只能证明 speculative requested/completed extra bytes 为 0，不能把 UID 流量写成 0。
- 13A 历史自然冷长尾、13E 早期网络长尾和 13F 第一手势前旧视频 rebuffer 都保留，不能用后续 warm/mixed PASS 覆盖。

## 6. 自动化、模拟器与实体机证据分级

### Unit / Host / Robolectric

- fresh `test --rerun-tasks` PASS，345 个 Gradle task 全部重新执行。
- fresh `lint --rerun-tasks` PASS，220 个 task；只有既有 native strip/deprecated API warning。
- fresh `assembleDebug --rerun-tasks` PASS，190 个 task。
- `:app:compileInstrumentationKotlin` PASS。
- Login/ChannelSelection/ComposeSmoke 与 CacheSettings 两组 `testInstrumentationUnitTest` 均 PASS。
- benchmark parser/runner 测试 PASS；`stage13f` 标题、目录和比较提示有回归断言。

### API 36 AOSP x86_64 emulator

- 候选 APK 的完整 Compose UI 为 42/42 PASS；多出的一项是候选专用 settle 测试。
- 恢复默认 Pager 后重新运行完整脚本为 41/41 PASS。
- 覆盖慢拖、正常 forward、target 回摆、跨页反向、快速手势、ticker 不重组 Pager、VideoKey 占位一致、PlayerView attach/detach、`ActivityScenario.recreate()` 活动绑定上限 1、离页和前后台状态；`PlaybackSessionMetrics` JVM 回归覆盖 pause/resume/seek 不计普通 rebuffer。

### 当前 Android 13/API 33 ARM64 实体机

- 默认 APK 保留数据覆盖安装和 install+launch smoke PASS，目标 Activity focused/top/resumed，crash 0。
- 两轮 RANDOM Normal Forward 各 12/12，Fast Reverse 10 次严格 PASS；慢拖和立即反向协议通过。
- Home 后记录非用户 `background pause`，前台恢复保留 RANDOM 播放路由且无 crash。按既有 `ACCEPTANCE_TESTS` 的 PLAYER-04，后台是立即暂停并停止预加载；它没有调用页面退出式 full `release()`，因此与本次 13F Failure state“后台完整释放”的字面要求不一致。本阶段没有越过 Pager 单变量边界去新增前台重绑生命周期。
- 系统 Back 返回频道时 `release binding=1`、surface detach=1；再次进入只有一个活动 PlayerView。日志 `playerInstances=2` 是同一进程内“已累计创建两个串行 engine”，源码顺序为旧 engine `release()`/置空后才创建新 engine，不是两个同时活动实例。正式滑动报告均为 `playerInstances=1`，活动绑定上限为 1。
- `am kill` 在后台终止旧 PID 后，不清数据冷启动恢复账号/频道页、PID 改变、crash 0；重新进入播放页后 `playerInstances=1`。
- 受保护播放页上 OEM 接受 Pager swipe，但拒绝 adb 注入的 Compose click/seek；实体机 pause/resume/seek 为**尚未验证**，没有用截图、移除 `FLAG_SECURE` 或修改内容保护绕过。

## 7. 最终性能门槛

| 门槛 | 结果 |
|---|---|
| 每轮 FIRST_FRAME=100%、FAILED=0 | 两轮正常均 12/12、FAILED 0；PASS |
| crash=0、普通滑动 rebuffer=0 | 正常、快速、慢拖、反向与最终 crash buffer 均为 0；PASS |
| 错绑、上一帧残留、串音=0 | VideoKey 占位、settled bind、callback token 和 emulator Compose 回归通过；实体机无错页/串音观测；PASS |
| 一个 ExoPlayer、活动 PlayerView binding≤1 | `ExoPlayer.Builder` 生产扫描只有 1 处；正式报告 `playerInstances=1`；recreate/离页测试和实体机 detach/attach 证明活动绑定≤1；PASS |
| speculative video≤1、移动数据默认预加载=0 | 13C 状态机/13D 上限/13F 回归保持；PASS |
| bind→首帧 P90 相对 13A 至少改善 15% | 同设备方向性算术满足；不同 RANDOM 媒体/缓存下的严格因果 A/B 尚未验证 |
| 若修改 release→settle，至少改善 15% 且 jank 不增加 | 360 ms 的 settle 收益满足但 jank 不满足，已回滚；最终未修改，条件不适用 |
| max 长尾不恶化超过 10% | 相对 13A 累积生产基线的方向性 max 未恶化；严格同媒体比较尚未验证 |
| 新增网络字节有上限 | speculative extra requested/completed=0，唯一 next 262,144 bytes 上限保持；UID 总流量尚未验证 |
| 页面/后台/退出完整释放 | 页面返回和进程终止的释放已证明；后台只有 pause + preload stop，不是 full release；按本次字面合同 FAIL |

## 8. 安全审计

- 合并 Debug Manifest 权限恰好为 `INTERNET`、`ACCESS_NETWORK_STATE`。
- `allowBackup=false`，并同时引用 `@xml/data_extraction_rules` 与 `@xml/backup_rules`；两套规则排除 root/file/database/sharedpref 及 device 域。
- `ExoPlayer.Builder` 生产实现只有一处；没有 `SimpleCache`/`CacheDataSource`/Media3 完整缓存。
- 没有 `MediaStore`、external storage、Downloads/DCIM/Movies API；媒体仍只在 app-private TDLib 缓存。
- 生产 `VideoPlaybackScreen.kt` 没有 13F 自定义 Pager fling；没有第二播放器、额外预加载项或错误 poster/上一帧伪装。
- Debug 日志和报告只保留状态、范围、脱敏 key/错误码与统计；不记录真实凭证、手机号、验证码、密码、消息正文、文件路径或完整 TDLib 对象。
- `local.properties` 仍被 git ignore；版本目录没有 alpha/beta/RC/snapshot。
- 未清用户数据/缓存、未退出账号、未修改网络/VPN/质量，未截图、缓存、导出或分享受保护内容。
- 仓库仍为 `main`、无提交基线且全部文件属于用户的未跟踪工作；本阶段没有暂存、提交、push、reset 或 clean。

## 9. 尚未验证项

1. 严格同一媒体、相同缓存温度、相同网络窗口的 13A→13F bind→首帧因果 A/B。
2. UID 级完整新增网络字节；只能证明 speculative extra bytes 为 0。
3. 13F 实体机自然随机轮次边界；相同最终生产配置已有 13E 边界证据。
4. 当前受保护实体机页的 pause/resume/seek；自动化和 API 36 模拟器已覆盖，OEM adb 点击/seek 未被接受。
5. 严格自然冷、空缓存和人工制造 stale `fileId`；合同禁止为本阶段清缓存、退出账号或改网络制造样本。
6. Vivo/iQOO Android 16；当前目标真机为 Android 13/API 33 ARM64。
7. 后台 full release：当前生产按既有合同 pause + preload stop；若要改为 full release，必须另行设计前台安全重绑，不能在 13F 单变量实验中顺手修改。

## 10. 精确回滚顺序

当前生产已经是回滚后的默认 Pager，不需要运行时回滚。若审阅者要撤销 13F 的报告工具和文档，顺序如下：

1. 先确认 `VideoPlaybackScreen.kt` 没有 `PagerDefaults`、自定义 `flingBehavior`、`tween(360)` 或候选常量；若存在，删除这些候选行并恢复无参数的 `VerticalPager`。
2. 删除候选专用 Compose 测试；保留既有慢拖、回摆、反向、ticker、VideoKey、PlayerView 和 recreate 回归。
3. 从 `run-swipe-first-frame-benchmark.ps1` 的 `ReportStage` 白名单、标题和比较提示移除 `stage13f`；同步移除脚本测试中的 13F 断言。
4. 移除 README、`ACCEPTANCE_TESTS.md` 对本报告的索引，再删除本报告。历史 `build/reports/stage13f` 失败/通过证据默认保留，不自动删除。
5. 运行 fresh `test`、`lint`、`assembleDebug`、Compose Path B；构建不带实验属性的默认 APK，并以保留数据方式覆盖安装和 launch smoke。
6. 重新确认 13B 两个生产开关为 `false`、13D 为 `BASELINE`、Manifest/备份/权限不变。任何一步失败立即停止，不继续安装或扩展功能。

## 11. 最终收口

阶段 13F 没有把较短 Pager 动画交付到生产。最终选择是保持已验证的默认 settle 行为，以避免用约 20% 的 settle 数字换取可观察的 jank 回退。阶段 13 的 RANDOM 正确性、单播放器/单 Surface、唯一下一项、消息引用恢复和正式随机性能证据已形成长期回归入口；不同媒体与网络条件没有被描述成严格因果 A/B。由于后台 full release 合同不满足、实体机受保护页 pause/resume/seek 与严格同条件性能仍未验证，最终结论是“安全回滚并完成报告收口”，不是“阶段 13 无条件验收通过”。
