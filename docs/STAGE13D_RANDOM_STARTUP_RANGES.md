# 优化阶段 13D：随机模式 Media3 启动区间观测及有界预加载候选

日期：2026-08-09

结论：**FAIL（没有候选达到生产门槛）**。生产 `BuildConfig.STARTUP_RANGE_CANDIDATE` 保持 `BASELINE`，唯一下一条仍只请求头部 256 KiB。真实观测没有出现 TAIL startup miss，因此候选 A/B 没有进入真机实验；候选 C 在当前网络多次 `NO_PROGRESS` 的窗口中无法完成可比较的重复 A/B。最终结论是：**更大或尾部预加载无安全收益**。

## 1. 阶段合同

### Outcome

识别 RANDOM 播放 bind→READY 长尾来自 HEAD、TAIL、MIDDLE/其他 offset、网络无进展还是 first-byte 之后的准备，并且只允许一个经过重复真机 A/B、首帧完成率和长尾门槛全部通过的有界策略进入生产。

### Scope

- `player/TelegramMediaDataSource.kt` 与共享 `PlaybackRangeRequestSession` 的 DataSpec 观测。
- `player/VideoPreloadManager.kt` 的离散实验候选与同目标顺序有界头/尾 lease。
- `player/VideoPlayerManager.kt` 的首帧脱敏摘要。
- domain 自适应决策只新增不含网络身份的 `isUnmeteredWifi` 安全分类。
- 对应 JVM 测试、benchmark parser/runner/脚本测试。
- `README.md`、`docs/ACCEPTANCE_TESTS.md` 与本文。

`TelegramFileManager` 的生产调度实现没有修改；13B 的 owner promotion 与活动请求复用生产开关继续为 `false`。

### Boundary

- 一个 ExoPlayer、一个 PlayerView、当前项和唯一未来目标不变。
- 不完整下载、不解析 MP4、不定位或读取媒体结构、不创建 Media3 `SimpleCache` 或第二份缓存。
- 当前播放 4 MiB read-ahead、质量选择、Room schema、TDLib native 与 Media3 参数不变。
- 文件大小未知时不猜尾部；移动/计费网络默认 0 下一项请求；`supportsStreaming=false` 为 0 请求。
- 同一 APK 的离散 BuildConfig 值只能是 `BASELINE`、`TAIL_64`、`TAIL_128`、`HEAD_512_WIFI` 之一。
- 未进入 13E。

### Failure states

- unknown size：只保留安全头部。
- 小文件：头部截到文件大小，尾部从不为负且不与头部重叠。
- 已覆盖 tail：不重复 acquire。
- head 成功、tail acquire/await 失败：只关闭 tail lease，安全 head 保留到目标取消或 stop，当前播放继续走原 startup 请求。
- target、方向、页面、网络 generation 或硬资源状态变化：generation 失效并关闭该目标的全部 lease。
- OFF、默认移动网络、unsupported、logout/release：0 活动候选。

### Proof

主机按用户给定顺序执行模块单测、fresh test/lint/assemble，再执行 Compose Path B。真机协议不清数据/缓存，不改网络、VPN 或质量；所有失败报告保留。

## 2. 13C 与 owner handoff 前置复核

- 13C current/upcoming 随机轮次、边界 PlaybackPlan 原子晋升和方向感知唯一 target 的主机、Compose 与历史真机证据已通过。
- 2026-08-09 的 13B 补验中 owner promotion matched 11/11、提前取消 0，并两次复用活动请求，证明 handoff 路径正确；但候选 FIRST_FRAME 11/12，P90/max 为 3,033/4,013ms，相比关闭候选的 1,599/1,783ms 明显回退。
- 因此 `VideoPreloadManager.PRODUCTION_OWNER_PROMOTION_ENABLED=false`、`TelegramFileManager.PRODUCTION_REUSE_CONTAINED_ACTIVE_REQUEST=false` 保持不变。13D 的 `currentReusedNextOwner` 为 0 是预期生产状态，不是 handoff 回归。

## 3. A：只加观测，不改请求

### 3.1 观测字段

每个成功首帧只输出一条 `CVF-StartupRange` 摘要：

- `firstMissCategory=HEAD|TAIL|MIDDLE|UNKNOWN|NONE`；
- `coveredBeforeCurrentBytes`；
- `dataSpecOpenCount` 与 `extractorRangeSwitchCount`；
- `currentReusedNextOwner`；
- `firstByteToReadyMs` 和离散 candidate 名称。

benchmark 同时汇总 bind→first byte、first byte→READY、已完成 speculative 覆盖、实际请求/完成的额外字节、TDLib switch/merge/cancel、NO_PROGRESS 与 rebuffer/crash。观测只保留 offset 分类、字节数和计数，不记录本地路径、remote id、网络身份、媒体内容或媒体字节。

### 3.2 最终 256 KiB 基线

初始两轮观测报告：

| 报告 | FIRST_FRAME | 首个 miss HEAD/TAIL/MIDDLE/UNKNOWN/NONE | bind→首帧 P90/max | NO_PROGRESS | rebuffer/crash |
|---|---:|---:|---:|---:|---:|
| `random-swipe-first-frame-normal-reverse-20260809-145307.md` | 12/12 | 12/0/0/0/0 | 1,812/5,767ms | 0 | 0/0 |
| `random-swipe-first-frame-normal-reverse-20260809-145420.md` | 12/12 | 11/0/0/0/1 | 2,416/3,070ms | 0 | 0/0 |
| `random-swipe-first-frame-fast-reverse-20260809-221855.md` | 4 checkpoints | 3/0/0/0/1 | 1,022/1,022ms | 0 | 0/0 |

因此在 28 个成功首帧中，首个未命中为 HEAD 26 次、NONE 2 次，TAIL/MIDDLE/UNKNOWN 均为 0。正常轮长尾主要位于 bind→first byte；first byte→READY 有次级波动，但没有尾部 startup miss 证据。

随后发现实体机可在 UI dump/准备期间灭屏，导致手势只唤醒而不触发 pager。runner 增加了已验证播放页之后、每个手势之前的幂等 `KEYCODE_WAKEUP`，并保留所有此前 0 样本失败报告。修正后的相同协议结果：

| 报告 | 结果 | FIRST_FRAME | HEAD/TAIL/MIDDLE/UNKNOWN/NONE | bind→首帧 P90/max | 额外字节 | rebuffer/crash |
|---|---|---:|---:|---:|---:|---:|
| `random-swipe-first-frame-normal-reverse-20260809-225023.md` | PASS | 12/12 | 11/0/0/0/1 | 4,305/4,369ms | 0 | 0/0 |
| `random-swipe-first-frame-normal-reverse-20260809-225226.md` | FAIL | 8/12 | 8/0/0/0/0 | 6,794/6,794ms | 0 | 0/0 |
| `random-swipe-first-frame-fast-reverse-20260809-225843.md` | FAIL | 0 checkpoints、SUPERSEDED 4 | 0/0/0/0/0 | n/a | 0 | 0/0 |

第二轮正常与快速轮说明同一未修改网络窗口已出现严重自然长尾；没有通过放宽超时、清缓存、关 VPN、换质量或放大缓存制造 PASS。

## 4. B：候选决策与结果

### 4.1 候选 A/B 未进入真机

- A：head 256 KiB + known-size tail 64 KiB，总上限 320 KiB。
- B：head 256 KiB + known-size tail 128 KiB，总上限 384 KiB。

二者的前置条件是 BASELINE 证明存在 tail startup miss。全部有效观测中 TAIL=0，因此按照阶段合同没有构建或运行 A/B 真机 APK。自动化仍验证其安全计划、顺序同目标 lease、unknown size、小文件、tail 已缓存、tail 失败和双 lease 取消，避免未来实验开关绕过边界。

### 4.2 候选 C 无可接受证据

C 只在 `isUnmeteredWifi=true` 时把唯一头部从 256 KiB 改为 512 KiB，不叠加 tail，理论额外请求为每个实际候选 256 KiB；计费网络回退 256 KiB，默认移动网络仍由自适应策略 OFF 为 0。

候选 APK 使用 `-PcvfStartupRangeCandidate=HEAD_512_WIFI` 单独构建。早期一次未修正灭屏协议的报告为 0 样本，明确排除。修正交互并重新进入 RANDOM 页后，候选 C 的不计样本 Forward-prime 在 60 秒内仍未到首帧，期间记录 4 次脱敏 `NO_PROGRESS`；自适应策略在当前项首帧前保持 OFF，因此该轮没有启动下一条 speculative lease，实际额外字节为 0。证据保存在 `build/reports/stage13d/candidate-head512-wifi-prime-failure-20260809.log`。

这个窗口不能把网络无进展归因于 512 KiB，也无法形成两轮 12+10 的可比较 A/B；它更不能证明 P90 改善 15%。根据“没有可靠收益就保留 256 KiB”的合同，C 不进入生产。没有候选达到 FIRST_FRAME 100%、P90 改善、max 上界和重复轮次四个门槛。

## 5. 自动化覆盖

- `StartupPreloadCandidateTest`：四个候选精确硬上限、unknown size 无 tail、小文件无负 offset/重叠、C 只在非计费 Wi-Fi 为 512 KiB、OFF 全部 0、BuildConfig 值一对一映射。
- `VideoPreloadManagerTest`：C 单头 512 KiB、默认移动网络 0、unsupported 0、同一目标顺序头/尾、target 改变关闭两段且只留下一个视频 fileId、tail 已缓存不重复、tail 失败只关闭 tail、stop/release 全部关闭。
- 既有 `TelegramFileManagerTest` 继续覆盖 CURRENT owner 优先级、取消、超时、owner release 与单请求调度；本阶段没有改变该实现。
- `StartupRangeObservationTest` 与 `TelegramMediaDataSourceTest` 覆盖 HEAD/TAIL/MIDDLE/UNKNOWN、small-file 分类和跨 DataSource 的 extractor DataSpec switch。
- benchmark 脚本测试覆盖字段解析、脱敏、额外请求/完成字节分离、NO_PROGRESS、stage13d 目录、Fast 3/3/3/1 与手势前 WAKEUP。

## 6. 执行与结果

主机 Proof 全部退出码 0：

1. `:telegram:testDebugUnitTest`
2. `:player:testDebugUnitTest`
3. `:app:testDebugUnitTest`
4. `test --rerun-tasks`：345/345 tasks executed
5. `lint --rerun-tasks`：220/220 tasks executed
6. `assembleDebug`：PASS
7. `scripts/tests/SwipeFirstFrameBenchmark.Tests.ps1`：PASS

Compose Path B：

- `:app:compileInstrumentationKotlin`：PASS。
- Login、ChannelSelection、ComposeSmoke Robolectric：PASS。
- CacheSettings Robolectric：PASS。
- API 36 AOSP x86_64 `CVF_AOSP_API36_X86_64`：40/40 PASS；instrumentation target 无 `.so`。
- 当前 Android 13/API 33 ARM64 实体机：默认 BASELINE APK 覆盖安装成功，`MainActivity` focused/top/resumed，目标包 crash=0。
- Vivo/iQOO Android 16 专项 `run-vivo-launch-smoke.ps1`：**尚未验证（该设备未连接）**；没有把当前 21091116UC 冒充 Vivo。

候选停止后又在无实验属性下执行 `:player:testDebugUnitTest` 与 `assembleDebug`，均 PASS，并把默认 APK 覆盖安装回实体机。

## 7. 安全审计与最终生产状态

- 有效权限仍只有 INTERNET、ACCESS_NETWORK_STATE；未修改 manifest、备份或公共存储行为。
- 未读取或记录凭证、验证码、密码、完整手机号、路径、remote id、Telegram 正文或媒体字节。
- 不清应用数据/缓存，不改网络/VPN/质量，不完整下载，不引入第二份缓存。
- 当前播放 4 MiB read-ahead、单 ExoPlayer/PlayerView、owner token、超时、pin 与 release 合同不变。
- 生产默认：`STARTUP_RANGE_CANDIDATE=BASELINE`，head=256 KiB，tail=0，额外 speculative 字节=0。
- 阶段在 13D 停止；未进入 13E。
