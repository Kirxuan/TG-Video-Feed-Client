# 优化阶段 12C：TDLib 区间调度、owner 协调与当前项优先

状态：已完成；安全调度、取消、红绿测试、完整主机 Proof、Compose Path B 和 Android 13 ARM64 真机协议均已通过。两阶段小头窗已因 A/B 回退；最终连续缓存窗口复用与定点 owner 释放配置两轮各 12/12 到达首帧，P90 可重复优于 12B 且最大值未回退。未进入 12D。

## 1. 阶段合同

Outcome：

- 用显式优先级保证当前 startup/seek/continuation 高于唯一下一条预加载。
- 同一 `fileId` 的兼容区间复用等待、受限合并；旧需求、seek、页面退出、错误和 owner 释放可立即取消。
- 降低 12B 暴露的 TDLib 首区间长尾，同时不增加完整下载、缓存副本、第二播放器或移动数据预加载。

Scope：

- `core:domain` 的 Telegram 文件请求契约。
- `telegram` 的 official TDLib `downloadFile` 调度、`updateFile` 隔离、owner/超时/取消协调。
- `player` 的 DataSource 请求会话、seek/close/首帧优先级转换和唯一 next preload。
- `app` 的 seek 前预加载停止边界。
- 对应 Fake 单测、本文档与 README。

Boundary：

- 不进入 12D。
- 不增加权限、依赖、播放器或 Media3 完整文件缓存。
- 不修改账号、Wi-Fi、VPN、质量偏好、缓存上限或移动数据默认策略。
- 不清理真实账号、Room、DataStore、TDLib 数据库或媒体缓存。
- 不解析 MP4、不猜测 `moov` 位置、不手写 MTProto、不使用 Bot API。

Failure states：

- DataSource close、快速连滑、seek、页面退出、播放器 error、账号退出、硬/进展超时和质量 `fileId` 变化都释放旧 lease。
- owner 释放立即唤醒等待者；错误 `fileId` 的响应和 stale `updateFile` 不完成当前区间。
- `supportsStreaming=false` 继续返回不支持状态，不触发下载。

Proof：

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :telegram:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :player:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat lint --rerun-tasks --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

另按根目录 `AGENTS.md` 执行完整 Compose Path B 和 Android 13 ARM64 实体机协议。

## 2. 前置条件与 12B 新基线

开始前已完整阅读 `AGENTS.md`、`README.md`、阶段 12A、12B、10、11 文档。阶段 12B 文档、fresh Proof、Compose Path B、保留数据覆盖安装和 12/12 真机证据均存在。

本阶段只采用 12B 最终同条件 A/B，不沿用 12A 或 12B 的后续新媒体压力样本：

| 12B bind → first frame | P50 | P90 | 最大 |
|---|---:|---:|---:|
| 暖/混合缓存顶部 12 条 | 212ms | 240ms | 241ms |

## 3. 修改前真实调用链

```text
ProgressiveMediaSource / extractor
  → TelegramMediaDataSource.open(DataSpec)
  → read；跨过当前 256KiB 区间时再次 acquire
  → extractor seek 时创建/打开新的 DataSpec
  → TelegramFileGateway.acquireRange(fileId, offset, length, owner)
  → TelegramFileManager 建立 owner lease 并等待连续可读前缀
  → TelegramClientManager.downloadFile
  → 官方 TdApi.DownloadFile(fileId, priority, offset, limit, false)
  → TDLib TdApi.UpdateFile
  → TelegramClientManager 映射 TelegramFileSnapshot
  → TelegramFileManager 仅按目标 fileId/连续区间唤醒
  → RandomAccessFile.seek/read app-private TDLib path
  → DataSource.close / rebind / seek / logout
  → lease.close
  → TelegramFileManager.release + notifyAll
  → 必要时官方 TdApi.CancelDownloadFile
```

修改前日志还确认：

- Media3 真实路径可能先读头部、再主动 seek 到文件尾、再回到 offset 48，不能假设 `moov` 在头部。
- 低优先级 next preload 曾在 current 启动前占用 TDLib 请求。
- 同 fileId 的重复/重叠需求没有显式的包含共享和受限部分合并结果。
- 原日志缺少 owner 类型、优先级与合并结果，无法稳定统计让路和复用。

## 4. 最终设计

### 4.1 显式优先级

领域边界增加 `TelegramFileRequestPriority`，UI/ViewModel 不接触 TDLib 数值：

| 领域优先级 | TDLib priority |
|---|---:|
| `CURRENT_STARTUP` | 32 |
| `CURRENT_SEEK` | 30 |
| `CURRENT_CONTINUATION` | 24 |
| `NEXT_PRELOAD` | 8 |

`PlaybackRangeRequestSession` 把同一次 bind 的所有 DataSource 连接在一起：

- bind 初始为 startup。
- 用户 seek 先取消所有旧 range，再把下一个 DataSpec 标为 seek。
- seek 的首个连续区间可读后降为 continuation。
- 第一帧后把所有仍活动和后续 range 降为 continuation，并允许唯一下一条恢复。
- rebind、页面退出、播放器 error 和 release 关闭整个 session。

### 4.2 同 fileId 协调

每个 `fileId` 只有一个协调状态：

- snapshot 已覆盖目标区间：直接读取现有 TDLib 私有文件。
- 活动请求完全包含需求：共享等待，不重复调用 TDLib。
- 部分重叠：合并为受限 union；总跨度最大 4MiB，不扩成完整文件。
- 不相交：更高优先级需求取消并切换旧请求；低优先级需求按 owner sequence 等待，避免永久饥饿。
- 不同 `fileId` 永不合并。
- `downloadFile` 返回的 `fileId` 不匹配时忽略；无目标区间连续进展的 stale `updateFile` 不唤醒等待者。

DataSource 仍只等待当前真实 256KiB 需求，但在 `updateFile` snapshot 已证明后续字节连续可读时，可在同一 4MiB read-ahead、DataSpec 剩余和已知文件大小三重上限内继续读取，不再每 256KiB 重建 lease 与 `RandomAccessFile`。这只复用 TDLib 已有 app-private 字节，不扩大 downloadFile，不建立第二缓存。

每个 range/protection lease 关闭时按自身 `fileId + owner token` 定点释放；公开的 token-only 兼容入口仍可释放同 token 的所有 entry。只有释放仍处于 startup/seek 的前台阻塞 owner 时才扫描并恢复被让路的 next preload，continuation/chunk 关闭不再进行无效全表调度。即使不同 fileId 意外复用同一 token，关闭一个 lease 也不会误释放另一个文件。

所有等待使用单调时钟，在 Media3 加载线程执行。15 秒表示“目标连续字节无进展”的 stall 窗口；每次实际连续进展刷新 stall，硬上限仍为 6 个窗口（90 秒）。owner 释放在 monitor 内立即 `notifyAll`。

### 4.3 请求和缓存预算

- current 单次和累计协调 read-ahead 上限仍为 4MiB。
- next preload 仍只有唯一下一条的 256KiB。
- 移动网络默认禁用策略和 `supportsStreaming=false` 过滤未改变。
- 数据只来自 TDLib app-private 缓存；没有 Media3 完整文件缓存。
- 当前与下一条的 owner/protection lease 继续参与缓存清理保护。

### 4.4 首窗口 A/B 决定

曾实现“首个 256KiB 只请求实际需求，Media3 继续读取后才扩至 4MiB”。真实 A/B 没有收益且明显回退，因此按合同恢复 12B 已证明的受限 4MiB read-ahead。保留的是优先级、区间协调、取消和诊断，不保留小头窗。

Media3 主动 seek 到尾部时仍严格服务实际 DataSpec；没有自行解析、扫描或复制文件。

### 4.5 脱敏诊断

Debug 可记录：

- `fileId`、owner 类型、优先级、DataSpec/请求 offset 和 length/limit。
- wait、first continuous byte、有效速率、包含复用、受限合并、切换、重排和取消结果。
- bind → first byte；只保存该 bind 第一个连续可读 range 的单调时间戳。

缓存立即命中的逐 256KiB 日志已从首帧关键路径移除；网络等待、合并、取消和超时仍记录。Release 的详细日志由 `BuildConfig.DEBUG` 关闭。日志不包含 path、remote id、caption、频道名、owner token、凭证或媒体字节。

## 5. TDD 红绿记录

按测试先行逐项建立红灯：

1. 缺少 typed priority，编译失败。
2. startup 仍直接使用旧隐式窗口，优先级/窗口测试失败。
3. seek session 不存在，编译失败。
4. 部分重叠活动请求没有合并，预期 2 个受限请求但只有旧请求。
5. 错误 `fileId` 的 download 响应被发布。
6. 小头窗第二阶段行为缺失。
7. DataSource open 等待期间 close 未及时解除阻塞。
8. 首帧没有把活动 startup lease 重排为 continuation。
9. ViewModel seek 没有立即停止唯一下一条。
10. 真机否决小头窗后，回滚测试先期望恢复已证明 read-ahead 并失败，再修改生产代码至绿色。
11. bind → first byte 首区间记录先因接口缺失编译失败，再实现为只记录一次。
12. 连续 snapshot 已覆盖多个 chunk 时，第二个 256KiB 仍创建新 lease；增加跨 chunk 行为测试后，改为在既有受限 read-ahead 内复用。
13. 首帧指标使用回调后的同步工作时间；先写显式 callback-entry 时间测试，再让 READY/首帧状态机使用回调入口的单调时间。
14. 两个不同 fileId 复用同一 owner token 时，关闭一个 lease 会把另一个也释放；先以 `protectedFileIds()` 建立红灯，再改为按 fileId 定点释放。

绿色覆盖包括：

- startup 优先于 next、首帧后 next 恢复。
- seek 优先级和旧 startup 取消。
- 完全重叠单次有效请求、包含共享、部分重叠受限 union。
- 不同 fileId 隔离、错误 download 响应与 stale update 隔离。
- owner release、logout、DataSource close 立即唤醒/取消。
- stall/hard timeout 语义。
- bind/session/快速目标变化无旧请求泄漏。
- 首帧前 next 网络请求为 0、首帧后可恢复。
- 移动数据策略和非流式视频不回退。
- 所有 Fake 均位于测试源集，不使用真实 Telegram。

## 6. 真机 A/B

条件：

- Android 13 ARM64 实体机 `<device-serial>`。
- `adb install -r -t` 保留账号、Room、DataStore 和 TDLib 缓存。
- Wi-Fi + VPN、质量设置、缓存上限和移动数据策略未修改。
- 未清缓存；仍使用阶段 12B 的暖/混合缓存顶部序列。
- 每轮首次进入 1 条独立样本，再以 150ms 手势执行顶部 12 次向上滑动；分位数只统计 12 次手势，nearest-rank。

候选迭代：

| 配置 | P50 | P90 | 最大 | 决定 |
|---|---:|---:|---:|---|
| 小头窗 | 246ms | 311ms | 322ms | 否决；P90/最大分别恶化 29.6%/33.6% |
| 恢复 4MiB 窗口 | 222ms | 299ms | 314ms | 保留回滚，仍不满足 |
| 去除逐块 cache-hit 双重日志 | 220ms | 281ms | 283ms | 保留诊断降噪 |
| 再去除零等待 range-ready 日志 | 224ms | 262ms | 311ms | 保留诊断降噪 |
| 连续缓存窗口复用（诊断轮） | 208ms | 223ms | 241ms | 保留；另一冷进程轮仍为 208/269/290ms，继续查 owner 释放开销 |
| 连续复用 + 定点 owner 释放，第 1 轮 | 195ms | 225ms | 231ms | 通过 |
| 连续复用 + 定点 owner 释放，第 2 轮 | 195ms | 210ms | 231ms | 通过且可重复 |

复核旧证据时发现，先前文档把 `final.log` 的 nearest-rank P90 错写为 233ms；原始 12 条的正确值是 260ms（最大 264ms）。这一错误已纠正，不再把旧轮次当作通过证据。最终结论只使用定点 owner 释放后重新安装 APK 得到的两轮原始日志。

最终源码的两次重复：

| 指标 | 12B | 12C 第 1 轮 | 12C 第 2 轮 |
|---|---:|---:|---:|
| bind → first byte P50 | 尚未记录 | 33ms | 35ms |
| bind → first byte P90 | 尚未记录 | 41ms | 42ms |
| bind → first byte 最大 | 尚未记录 | 44ms | 43ms |
| bind → first frame P50 | 212ms | 195ms | 195ms |
| bind → first frame P90 | 240ms | 225ms | 210ms |
| bind → first frame 最大 | 241ms | 231ms | 231ms |
| first byte → first frame P90 | 尚未记录 | 200ms | 174ms |

两轮均为 12/12 手势到首帧、FAILED=0、SUPERSEDED=0、非零 rebuffer=0、目标包 crash/ANR=0。相对 12B：

- P50 两轮均改善 17ms（8.0%）。
- P90 分别改善 15ms（6.25%）和 30ms（12.5%），改善可重复；没有达到建议的 15% 目标，但该目标是建议值，未通过线是无可重复改善。
- 最大值两轮均改善 10ms（4.1%），没有超过“最多恶化 10%”门槛。
- 小头窗已回滚；没有为数字扩大 4MiB current/256KiB next 预算、清缓存或修改网络、VPN、质量、50–60 秒缓冲与 2.5/12 秒播放门槛。

这批暖缓存顶部数据的 request-level 包含共享、部分合并和显式 `PREEMPTED_BY_CURRENT` 都是 0 次：没有 current 网络请求，第一轮首帧后启动 next 网络 12 次，第二轮 next 已缓存因而为 0 次；首帧前 next network start 违规两轮均为 0。连续缓存窗口复用发生在 DataSource 内，不输出逐 chunk cache-hit 日志以免重新污染首帧路径；确定性单测证明跨 chunk 只产生一个 lease。强制包含共享、部分合并和让路由 TelegramFileManager 单测证明。

## 7. 额外真机协议

### 快速连续滑动 10 次

- 最终 APK 以约 560ms 间隔连续滑动 10 次：前 9 个目标全部以 `SUPERSEDED` 结束，最终目标 `FIRST_FRAME` 1 次；另有进入页首条首帧。
- 旧 owner 取消 13 次，FAILED=0、非零 rebuffer=0、crash=0。
- 最终绑定的 message key 与第 10 个 settled 目标一致，没有 stale bind。
- 另做一轮 80ms 手势 + 100ms 间隔的极快输入，Pager 合并为唯一最终目标且 key 仍正确；该轮没有制造旧 bind，不用于宣称 startup 取消。

### 播放中前后 seek

- 两次 seek 均观察到 `CURRENT_SEEK→CURRENT_CONTINUATION`。
- 向前到约 44.5 秒、向后到约 15.1 秒；旧 range 和唯一 next owner 先取消，两次均从 BUFFERING 返回 READY。
- request failure、range timeout 和非零 rebuffer 均为 0。

### 进入后立即返回

- 100ms、180ms 和 400ms 三种窗口都能立即回到频道页并调用 `release binding`；这些窗口早于实际 bind/owner 建立，因此没有伪造 `RELEASED` 或 owner cancel 数字。
- `MainActivity` 始终 resumed/top，crash buffer 和目标包 crash/ANR 均为 0。
- “DataSource open 正在等待时 close 立即唤醒”由真实 DataSource + Fake gateway 的线程测试确定性覆盖；本轮页面返回没有产生可供真机观察的活动等待。

原始脱敏证据：

- `build/reports/stage12c/owner-targeted-1-valid.log`
- `build/reports/stage12c/owner-targeted-2.log`
- `build/reports/stage12c/final-rapid-supersede.log`
- `build/reports/stage12c/seek-hit.log`
- `build/reports/stage12c/seek-back.log`
- `build/reports/stage12c/final-immediate-back.log`
- `build/reports/stage12c/final-immediate-back-active.log`
- `build/reports/stage12c/final-immediate-back-400.log`

## 8. Proof 结果

最终源码按固定顺序完成：

- `:telegram:testDebugUnitTest`：通过，96 tests、0 failure/error/skip。
- `:player:testDebugUnitTest`：通过，37 tests、0 failure/error/skip。
- `:app:testDebugUnitTest`：通过，68 tests、0 failure/error/skip。
- `test --rerun-tasks`：通过，345/345 tasks executed。
- `lint --rerun-tasks`：通过，220/220 tasks executed。
- `assembleDebug`：通过，190 tasks；最终 APK 44,628,876 bytes，SHA-256 `00464CC98D5A6517259BA79801F5ADD2DA2327E56808479B4B593B4DBC2849FD`。
- `:app:compileInstrumentationKotlin`：通过。
- Robolectric-Compose：登录、频道选择、Compose smoke 与缓存设置两组均通过。
- API 36 AOSP x86_64 emulator Compose UI：`EMULATOR_COMPOSE_RESULT=PASS`。
- 当前在线 Android 13 ARM64 实体机保留数据覆盖安装与冷启动 smoke：固定脚本返回 `VIVO_LAUNCH_SMOKE_RESULT=PASS`；设备实际型号为 `21091116UC`，不能据脚本名冒充 iQOO 12 / OriginOS 6 / Android 16 证据。

最终一轮 `test --rerun-tasks`、`lint --rerun-tasks`、Compose emulator 和真机 launch smoke 都一次通过；没有删除 build、停止账号、清缓存或改变网络来获得通过结果。

## 9. 被否决方案

- 两阶段 256KiB 小头窗：真机 P90 和最大值明显回退，已删除。
- 把旧 `final.log` 的错误 P90 继续当作通过证据：重新按 nearest-rank 解析后否决并更正文档。
- 调低 Media3 2.5 秒首播门槛或 50–60 秒缓冲：属于播放器弱网合同且没有 12C 证据，未修改。
- 假设 `moov` 位于头部并自行扫描 MP4：违反真实 extractor seek 行为和阶段边界，未实现。
- 扩大 4MiB current 或 256KiB next 预算：没有证据，未实现。
- 完整文件预下载、第二缓存、第二播放器：违反固定合同，未实现。
- 清缓存制造冷基线、关闭 VPN 或改质量设置：会破坏同条件 A/B，未执行。
- 为首帧抖动修改 Media3/解码器：超出 12C，未进入。

## 10. 未验证与停止边界

- 真实账号退出未执行，因为会修改用户账号；logout 取消由 Fake 单测验证。
- `supportsStreaming=false` 的真实 Telegram 条目未在本轮手工触发；生产过滤与 Fake 单测已验证。
- 移动数据网络未切换；默认禁用策略由单测验证，避免修改用户网络。
- 严格冷缓存未验证，因为没有清缓存授权。
- iQOO 12 / OriginOS 6 / Android 16 未连接，因此 Compose Path B 的该特定 Vivo install+launch smoke 尚未验证；编译、Robolectric-Compose、API 36 x86_64 emulator UI 和当前 Android 13 实体机 smoke 均已通过。
- 建议的 P90 至少改善 15% 尚未达到；实际两轮为 6.25%/12.5%，但都可重复优于 12B，最大值也未回退，因此按本阶段验收语义通过并如实保留差距。
- 未开始 12D，未暂存、提交或推送。
