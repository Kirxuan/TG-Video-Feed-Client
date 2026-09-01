# 阶段 15A：加载热路径诊断与修改前基线

日期：2026-08-22（Asia/Hong_Kong）
状态：已完成

## 阶段合同

### Outcome

以可重复的主机测试、构建结果和代码调用链证据，定位竖向翻页从 target known 到真实首帧之间仍存在的非必要等待、重复解析/请求、错误 ownership 顺序或 UI 状态错位，并区分已经正确实现、已被历史实验否决和仍需验证的候选。

### Scope

- 审计 `app` 的 Pager、`VideoPlaybackViewModel`、PlaybackPlan、海报和高频进度状态。
- 审计 `player` 的唯一 PlayerView/ExoPlayer、`VideoPreloadManager`、`TelegramMediaDataSource`、bind/prepare/READY/first-frame gate。
- 审计 `telegram` 的消息引用 single-flight、`TelegramFileManager` 缓存覆盖、区间请求、优先级、owner release 与 stale callback。
- 运行现有 `player`、`telegram`、`app` 定向测试、全量主机测试与 Debug 构建，记录修改前结果。
- 调研与本地 Media3 1.10.1 对应的官方实现，以及 Android 官方性能/设计系统样例；记录采用或拒绝理由。
- 本阶段修改仅限本文和 `docs/STAGE15_GITHUB_REUSE_AUDIT.md`；只有现有指标无法区分候选时，才允许在既有 Debug 指标文件中增加最小、脱敏、单调时钟接缝及其测试。

### Boundary

- 不修改生产 TDLib 请求参数、256 KiB 唯一下一条上限、4 MiB 当前前读、Media3 LoadControl、Pager fling、质量策略、缓存额度或网络策略。
- 不启用阶段 13B owner promotion/contained active-request reuse，不重新实验 64/128 KiB、head 512 KiB、tail range、play-before-prepare 或 360 ms Pager snap。
- 不修改任何 UI 视觉、依赖、权限、Room schema、DataStore 字段或 native 产物。
- 不操作实体手机，不读取真实 Telegram 内容或 `local.properties` 值。

### Failure states

- 缓存 snapshot 声称覆盖但本地路径缺失：必须作为 miss/失效处理，不能在主线程检查或继续读取旧路径。
- 网络离线、区间无进展、硬超时、owner close：保持有限、可取消、能唤醒等待线程的错误路径。
- target、质量、网络、账号、队列或随机轮次 generation 变化：旧刷新、计划、READY、first-frame 和文件更新不得改变当前项。
- 现有测试或构建失败：保留首个根因，停止进入 15B；先最小修复并重跑本阶段完整 Proof。
- API 36 x86_64 emulator 不可用：记录“尚未验证”，不得选择实体机或 ARM64 AVD替代。

### Proof

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :player:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :telegram:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
.\gradlew.bat test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat assembleDebug --rerun-tasks --no-daemon --console=plain
```

## 调用链结论

`VerticalPager target known/settled` → `VideoPlaybackViewModel` 生成或复用 `PlaybackPlan` →
`VideoPlayerManager.bind/prepare` → `TelegramMediaDataSource.open/read` →
`TelegramFileManager.requestRange` → TDLib `downloadFile(offset, limit)` → READY → first frame。

下列边界在修改前已经正确，无需重写：

- 全应用只持有一个主要 ExoPlayer 和一个 PlayerView；页面数量不会增加实例。
- 当前项最多前读 4 MiB，唯一下一条预加载最多 256 KiB；移动数据默认关闭预加载。
- owner、generation、target key 和 first-frame gate 能拒绝过期回调。
- 视频字节只有 TDLib app-private 缓存一份；没有 Media3 `SimpleCache` 或公共存储副本。
- target-known、settled、bind、first byte、READY 与 first frame 均使用单调时钟并只记录脱敏键、范围和状态。

## 已确认根因

1. TDLib snapshot 仍可能指向已经被系统或缓存清理删除的本地文件。旧实现先相信覆盖区间，随后
   `RandomAccessFile` 打开失败，把本可恢复的 stale cache 误判为播放失败。
2. Room 元数据写回同一个 `chatId + messageId` 时，ViewModel 会把仍兼容的计划清空，造成不必要的
   reference refresh / plan rebuild，削弱 single-flight 和下一条准备结果。

两项分别进入 15B 与 15C，以最小边界修改修复。

## 明确拒绝的候选

- 不启用历史阶段已否决的 owner contained-request promotion、64/128 KiB 下一条、512 KiB head、tail
  range、play-before-prepare 或 360 ms Pager snap。
- 不增加第二播放器、第二 PlayerView、Media3 完整缓存、图片加载器或实时视频模糊。
- 不把 `message link` 当媒体 URL，不对 `supportsStreaming=false` 自动完整下载。

## 候选接受/拒绝表

| 优先级 | 候选 | 证据与预期收益 | 风险 | 验证 | 回滚 | 结果 |
|---|---|---|---|---|---|---|
| P0 | stale local snapshot 透明失效 | snapshot 覆盖但文件打开失败会造成可恢复播放失败；消除一次错误终态 | 错误清除更新后的新路径、形成循环 | Fake gateway + manager 路径相等测试；完整 player/telegram tests | 删除默认失效接口和一次重试分支 | 15B 接受 |
| P0 | 同 key 兼容 plan 保留 | Room 元数据写回会触发重复 plan rebuild；恢复 single-flight | 误复用质量/账号/queue 不兼容计划 | ViewModel FakeClock/single-flight 测试与 app tests | 恢复元数据变化即 clear | 15C 接受 |
| P1 | 420 ms 延迟进度 + 190 ms 首帧淡出 | 短等待减少 spinner 闪烁；长等待仍有明确反馈 | stale semantics、跨 key 继承 alpha | paused clock Compose tests、18 张 emulator 图 | 删除延迟 disclosure，恢复原 poster | 15D 接受 |
| P1 | Media3 DefaultPreloadManager/PlayerPool | 官方 main 有短视频预加载能力，但与本地 1.10.1、自定义 TDLib 256 KiB preload 重叠 | 多播放器、双请求、API 版本不匹配 | 必须先证明单播放器且无重复区间；当前无此证据 | 不接入即天然回滚 | 拒绝 |
| P2 | Cloudy 实时玻璃 | 可能增强视觉，但 alpha 依赖且不能安全采样 SurfaceView | GPU、Surface、FLAG_SECURE 与稳定依赖回退 | 需要真机 GPU/Surface 证据；本任务不具备 | 移除依赖/shader | 拒绝 |
| P2 | 重新调整 64/128/512 KiB、tail、LoadControl、360 ms snap | 历史阶段已有首帧完成率或 jank 回退证据 | 重复已否决实验 | 历史 benchmark 已足够 | 保持现有参数 | 拒绝 |

仍只是怀疑、没有新增证据的来源：质量选择本身耗时、TDLib priority 数值不足、LoadControl startup buffer
过大、Pager 默认 snap 过慢。它们没有进入生产修改，也没有以“理论上更快”为理由调参。

## 修改前与最终 Proof

- 15A 基线编译与定向测试可运行；阶段 0 的“无 Gradle 工程”限制已不再适用。
- 15B/15C 修复后，player、telegram、app 定向测试与全仓 `test --rerun-tasks` 均通过。
- 最终完整结果见 `STAGE15F_FINAL_ACCEPTANCE.md`；真机未操作、尚未验证。
