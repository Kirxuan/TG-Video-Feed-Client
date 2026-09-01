# 优化阶段 12A：滑动到真实首帧基线

日期：2026-07-29
状态：已完成；主机 Proof、Compose Path B、Android 13 ARM64 真机安装与 12 次真实滑动基线均已通过

## 1. 阶段合同

### Outcome

每次稳定翻页都能在 Debug 日志中得到从手势开始、页面稳定、播放计划启动、消息刷新、播放器绑定、首次 READY 到真实首帧渲染的分段耗时。播放器失败、非流式视频、页面释放和被新手势覆盖的旧转场会以明确终态结束。

### Scope

- `player` 模块新增只保存在内存中的转场指标状态机。
- `VideoPlaybackController` 增加默认 no-op 的脱敏转场事件入口。
- `VideoPlaybackViewModel` 在现有稳定页路径报告页面、计划和质量刷新边界。
- `VideoPlayerManager` 报告绑定、READY、`onRenderedFirstFrame`、失败和释放边界。
- 增加指标与 ViewModel JVM 回归测试。
- 使用已连接的 Android 13 ARM64 真机保留数据覆盖安装并采集真实滑动基线。

### Boundary

- 不删除现有 250ms 稳定页等待。
- 不改变 3 秒质量刷新上限、256KiB 下一条预加载、4MiB 当前前读、50–60 秒缓冲或 12 秒 rebuffer 恢复门槛。
- 不改变 500MB 默认缓存、唯一 ExoPlayer、网络/VPN、移动数据策略、权限或存储位置。
- 不清理用户账号、Room、DataStore 或 TDLib 私有媒体缓存。
- 本阶段只建立反馈环，不宣称滑动等待已经优化。

### Failure states

- 首帧未出现：播放器错误以 `FAILED` 结束。
- `supportsStreaming=false`：以 `UNSUPPORTED` 结束，不绑定媒体源。
- 快速滑动覆盖旧 generation：旧转场以 `SUPERSEDED` 结束。
- 离开页面或释放绑定：以 `RELEASED` 结束。
- 首次进入页面没有手势起点：手势相关耗时为 `null`，其余阶段仍可测量。
- 迟到的旧视频回调：按 `chatId + messageId` 复核，不得结束当前转场。

### Proof

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :player:testDebugUnitTest --tests "com.qixuan.channelvideoflow.player.PlaybackTransitionMetricsTest" --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.qixuan.channelvideoflow.feature.video.VideoPlaybackViewModelTest" --no-daemon --console=plain
.\gradlew.bat test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat lint --rerun-tasks --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
.\gradlew.bat :app:compileInstrumentationKotlin --no-daemon --console=plain
.\gradlew.bat :app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.auth.LoginScreenTest" --tests "com.qixuan.channelvideoflow.feature.channels.ChannelSelectionScreenTest" --tests "com.qixuan.channelvideoflow.test.ComposeSmokeTest" --no-daemon --console=plain
.\gradlew.bat :app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.settings.CacheSettingsScreenTest" --no-daemon --console=plain
.\scripts\run-emulator-compose-tests.ps1 -Serial emulator-5554
.\scripts\run-vivo-launch-smoke.ps1 -Serial <device-serial> -SkipBuild
```

## 2. 指标口径

`CVF-Transition` 的单条 summary 只包含：

- outcome；
- chatId、messageId；
- refreshOutcome；
- gesture→settle；
- settle→plan；
- refresh；
- plan→bind；
- bind→READY；
- READY→first frame；
- bind/settle/gesture→terminal。

所有时间使用单调时钟。日志不包含 caption、频道名、手机号、TDLib 私有路径、file remote id、凭证、会话或完整 TDLib 对象；Release 不产生该详细日志，指标不写入 Room、DataStore 或文件。

## 3. 当前验证

- 指标状态机先以缺少实现的编译失败建立红灯，再实现至目标测试通过。
- ViewModel 边界事件测试先以事件列表为空建立红灯，再接入生产路径至目标测试通过。
- 真机发现 Media3 在该设备上可能先派发 `onRenderedFirstFrame`、后派发 READY listener；新增“最早 READY 幂等”和首帧保守补边界，测试先红后绿。该兼容情况下 `READY→first frame=0ms` 只表示 READY 的上界，不用于判断实际解码耗时；`bind→terminal` 仍是可靠首帧指标。
- 真机发现同一次拖动可能重复报告 pager unstable；新增重复边界幂等测试，先红后绿，不再生成无 key 的伪 `SUPERSEDED`。
- `.\gradlew.bat test --rerun-tasks --no-daemon --console=plain`：通过，345/345 个任务重新执行。
- `.\gradlew.bat lint --rerun-tasks --no-daemon --console=plain`：通过，220/220 个任务重新执行。只有既有 `libtdjni.so` strip 警告和存储广播废弃警告。
- `.\gradlew.bat assembleDebug --no-daemon --console=plain`：通过，190 个任务。此前一次 `--rerun-tasks` 因 Windows 锁住生成的 `telegram/tdlib/.../R.jar` 失败；停止残留 Gradle daemon 后标准构建通过，未发现源码或资源错误。
- Compose Path B：instrumentation 编译、两组 Robolectric-Compose、API 36 AOSP x86_64 emulator Compose UI、Vivo 真机 install+launch smoke 全部通过；模拟器结果为 `EMULATOR_COMPOSE_RESULT=PASS`，真机结果为 `VIVO_LAUNCH_SMOKE_RESULT=PASS`。
- 最终 Debug APK 已以 `adb install -r -t` 定向覆盖安装到 `<device-serial>`，保留账号、Room、DataStore、TDLib 私有缓存与网络/VPN 状态；`MainActivity` resumed/top，目标包 crash buffer 为 0。

## 4. 真机滑动基线

测试条件：

- 设备：Android 13 ARM64，序列号 `<device-serial>`。
- 已有登录、索引与缓存全部保留；这是用户当前真实使用状态的暖/混合缓存基线，不是严格冷缓存基线。
- 活跃默认网络为 Wi-Fi，设备同时存在 VPN transport；测试未修改 Wi-Fi、VPN、移动数据策略或应用预加载策略。
- 首次进入播放页得到 1 个独立样本，随后执行 12 次向上滑动；以下分位数只统计 12 次有手势样本，使用 nearest-rank。

终态结果：

- `FIRST_FRAME`：13（首次进入 1 + 滑动 12）。
- `FAILED` / `UNSUPPORTED` / `SUPERSEDED` / `RELEASED`：本轮采样窗口内均为 0。
- 12/12 次滑动都在 10 秒采样上限内到达真实首帧。

| 指标 | N | 最小 | P50 | P90 | 最大 |
|---|---:|---:|---:|---:|---:|
| gesture → settle | 12 | 604ms | 620ms | 628ms | 628ms |
| settle → plan | 12 | 250ms | 250ms | 251ms | 251ms |
| refresh | 12 | 4ms | 5ms | 7ms | 7ms |
| plan → bind | 12 | 4ms | 5ms | 8ms | 8ms |
| bind → first frame | 12 | 190ms | 212ms | 853ms | 1243ms |
| settle → first frame | 12 | 446ms | 469ms | 1109ms | 1498ms |
| gesture → first frame | 12 | 1068ms | 1089ms | 1737ms | 2115ms |

结论：

- 3 秒质量刷新上限不是本轮瓶颈：实际刷新 P90 只有 7ms。
- 最稳定的可控浪费是 pager settle 后额外固定等待 250ms；删除后按本轮分段直接估算，gesture→first frame 的 P50 可从约 1089ms 降至约 839ms，P90 可从约 1737ms 降至约 1487ms。
- gesture→settle 自身约 620ms，属于拖动与分页动画完成时间，下一阶段应区分“用户仍在拖动”和“目标页已确定”，避免必须等完整 settle 才开始安全准备。
- bind→first frame P50 只有 212ms，但 P90 853ms、最大 1243ms，是第二个长尾来源；下一条已有数据没有被原子晋升为当前播放计划时，预加载收益可能没有完整兑现。

## 5. 下一阶段候选

阶段 12B 建议保持单播放器与单下一条预算，按以下顺序实施并以本页基线做 A/B：

1. 删除 pager 已稳定后的固定 250ms，generation 校验和取消语义保持不变。
2. 在目标页确定但动画尚未完全 settle 时启动可取消的消息刷新/播放计划准备；真正 `bind` 仍只发生在稳定页。
3. 把“下一条预加载结果”升级为带 `VideoKey + fileId + quality preference generation` 的原子播放计划，滑到该项时直接晋升，避免再次刷新和选择。
4. 当前项一旦缺首帧，立即暂停或取消下一条 owner 的网络请求；当前首帧后再恢复唯一下一条的 256KiB 预加载，防止同一 TDLib 下载队列争抢。
5. 保留失败、快速连滑、质量偏好变化、移动数据禁用预加载、非流式视频和 stale callback 的回归测试。
6. 复跑相同 12 次真机协议；目标至少为 gesture→first frame P50 降低 200ms，且 P90 不回退、无新增 rebuffer、无第二播放器或第二份完整缓存。

未经下一阶段明确批准，本阶段不修改上述生产参数或调度策略。
