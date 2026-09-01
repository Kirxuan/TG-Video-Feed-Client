# 交付阶段 8：稳定性、安全和最终验收

日期：2026-07-29

结论：阶段 8 已修复验收中确认的缺陷，并完成主机、API 36 x86_64 emulator 和 Android 13 ARM64 真机验证。2026-07-29 补充完成了生产私有视频缓存清理、系统省电状态、强制横屏和实际音频焦点申请/释放验证。由于真实退出登录、低存储、物理耳机、外部应用抢占音频焦点、真实网络完全断开和真实受保护消息等条件没有实际完成，**不得宣称项目已经完全稳定**。

## 1. 阶段合同

- Outcome：为 34 个验收项建立可追溯证据；只修复稳定性、安全和资源生命周期缺陷，输出可安装 debug APK。
- Scope：`app` 的设备策略、信息流、窗口保护、ViewModel 和 Compose 测试；`player` 的音频/错误/重试/释放；`telegram` 的启动 I/O 调度；阶段文档。
- Boundary：不增加产品功能，不退出真实账号，不填满存储，不删除/退出真实频道或消息，不停止用户 VPN，不绕过设备锁屏或 OEM 安装安全策略。缓存清理只通过应用自身二次确认 UI 删除明确限定的私有视频缓存。
- Failure states：加载、空状态、网络/文件/解码/超时、低存储、省电、热状态和测试包安装受限都必须保留真实结果。
- Proof：fresh `test`、`lint`、`assembleDebug`；Compose Path B；Room emulator instrumentation；最终 APK 保留数据安装和安全真机验收。

## 2. 完成功能矩阵

| # | 验收项 | 结果与证据 |
|---:|---|---|
| 1 | 冷启动 | 通过（真机）：最终新鲜 APK 数据保留安装后 `LaunchState=COLD`，`TotalTime=1114ms`；缓存清理后的冷启动为 1041ms，均无目标包 crash。 |
| 2 | 热启动 | 通过（真机）：本轮后台返回为 60ms/72ms；同阶段较早两次为 68ms/58ms。 |
| 3 | 登录状态恢复 | 通过（真机）：强停、锁屏、进程杀死及真实缓存清理后均重新收到 READY，频道索引入口与私有账号数据仍在。 |
| 7 | 频道被删除或退出 | 通过（自动化）：频道成员状态映射、Room 对账、不可用隐藏/清选择均通过；真实频道场景尚未验证。 |
| 8 | 消息被删除 | 通过（自动化）：TDLib 删除更新、Repository 幂等删除和 Room 联合键删除测试通过；真实消息删除场景尚未验证。 |
| 9 | 不支持流式播放 | 通过（Compose）：显示精确文案“该视频暂不支持流式播放。”，播放器 attach 次数为 0；真实 Telegram 条目尚未验证。 |
| 11 | 连续滑动 100 条 | 通过（真机）：先确认真实播放页出现 `READY/isPlaying`，随后实际注入 100 次连续信息流上滑；进程存活、Activity=1、Views=54，无目标包 crash。 |
| 12 | 快速往返滑动 | 通过（真机）：30 组上/下快速往返，进程存活、Activity=1、Views=54，无目标包 crash。 |
| 13 | 后台后返回 | 通过（真机）：Home 后两次热返回，Activity resumed。 |
| 14 | 锁屏后返回 | 通过（真机）：`Awake → Asleep → Awake`，解锁后同一 `MainActivity` resumed。 |
| 17 | 清空缓存 | 通过（真机）：应用设置页二次确认后，私有 `cache` 从 65,738KB 降至 8,858KB；`databases` 保持 3,488KB，`files` 保持 15KB，`no_backup` 保留。随后冷启动恢复 READY 且视频索引入口存在。 |
| 19 | 省电模式 | 通过（真机状态注入 + 自动化）：使用电池服务 unplug 模拟后 `cmd power set-mode 1` 使 `low_power=1`，应用存活、无 crash；最终恢复 `low_power=0` 和原充电状态。 |
| 20 | 热状态升高 | 通过（真机注入 + 自动化）：`thermalservice` 从 0 注入 3，应用存活；随后 reset 恢复 0。领域策略覆盖 MODERATE+ 停止下一条预加载。 |
| 21 | 屏幕旋转 | 通过（真机 + Compose）：`wm user-rotation lock 1` 后系统报告 90° rotation，应用存活且无 crash；随后恢复原 `accelerometer_rotation=0/user_rotation=0`。全屏/退出全屏 Compose 测试通过。 |
| 22 | 进程被系统杀死后恢复 | 通过（真机）：后台 `am kill` 后 PID 消失，重启得到新 PID、冷启动成功并恢复 `READY`。 |
| 23 | Room 数据库迁移策略 | 通过（API 36 emulator）：12/12，包括 1→2、2→3、3→4 迁移并保留合法索引。 |
| 25 | ExoPlayer 正确释放 | 通过（代码/自动化/真机）：离开信息流调用完整 `release()`；真机系统返回记录 `CVF-Player: release binding`，音频焦点栈中的应用 owner 随离页消失，且仍停留同一 Activity 的频道页。 |
| 27 | 不存在无限重试 | 通过（自动化/静态）：扫描网络重试最多 3 次并遵守 FLOOD_WAIT；Media3 加载策略显式最多 3 次；等待均可取消/超时。 |
| 29 | 不存在真实密钥提交 | 通过（静态）：仓库尚无 commit、tracked file 数为 0；`local.properties` 被 `.gitignore` 命中；真实 API ID/Hash 在其余源码/文档中均 0 命中。 |
| 30 | 不存在敏感日志 | 通过（静态/动态）：生产日志调用只含状态、请求、范围、缓存、播放器状态和脱敏错误；真机日志中真实 API ID/Hash 和敏感字段模式均 0 命中。 |
| 31 | 不申请多余权限 | 通过（最终 APK）：`aapt2 dump permissions` 只有 `INTERNET` 与 `ACCESS_NETWORK_STATE`。 |
| 32 | 不写公共存储 | 通过（静态/设备路径）：无 MediaStore/外部存储 API 或存储权限；媒体只位于 app-private `cache/tdlib/files`。 |
| 34 | Android 备份不含敏感数据 | 通过（merged APK/XML）：`allowBackup=false`，同时引用 `fullBackupContent` 和 `dataExtractionRules`；云备份与设备迁移均排除全部 app 数据域。 |

## 3. 未完成功能矩阵

“自动化通过”不能替代真实账号或外部硬件场景；下列项目仍不满足完整人工证据。

| # | 验收项 | 已有证据 | 未完成/尚未验证 |
|---:|---|---|---|
| 4 | 退出登录 | ViewModel、Repository、TDLib `LogOut`、Closed 状态和清理顺序测试通过。 | 为保护真实会话和索引，未在生产账号执行；真机结果尚未验证。 |
| 5 | Wi-Fi 切移动数据 | 实际关闭 Wi-Fi、保持移动数据开启，应用未崩溃。 | 设备仅显示 VPN transport、没有 `CELLULAR CONNECTED`，无法证明真实移动数据切换；尚未验证。 |
| 6 | 网络断开后恢复 | 网络策略自动化通过；实际切换 Wi-Fi、数据和飞行模式后应用始终存活。 | 用户 VPN 在底层网络关闭后仍可达，未形成可证明的完全断网；恢复语义尚未验证。 |
| 7 | 真实频道被删除或退出 | 映射、Repository、Room 和 UI 自动化通过。 | 为避免改变账号状态，未再次退出真实频道；阶段 8 真机场景尚未验证。 |
| 8 | 真实消息被删除 | 删除 update、幂等 Repository 和 Room 自动化通过。 | 未删除真实消息；真机场景尚未验证。 |
| 9 | 真实不支持流式条目 | Compose 精确文案和不 attach 播放器通过。 | 未在当前真实队列定位到 `supportsStreaming=false` 条目；真机场景尚未验证。 |
| 10 | 视频解码失败 | Media3 解码器错误均映射 `DECODER_UNSUPPORTED`，Compose 显示“设备不支持该视频编码”。 | 未找到可稳定触发的真实硬件解码失败视频；真机尚未验证。 |
| 15 | 耳机拔出 | ExoPlayer 明确启用 `setHandleAudioBecomingNoisy(true)`。 | 真机无物理拔出；shell 发送受保护广播被系统拒绝，尚未验证。 |
| 16 | 音频焦点被其他应用抢占 | Media3 `AudioAttributes` 和自动焦点处理单元测试通过；真机播放页实际进入 Audio Focus stack，离页后 owner 消失。 | 未启动其他应用抢占真实焦点，焦点丢失后的真机暂停行为尚未验证。 |
| 18 | 存储空间不足 | 低存储广播/空间阈值和预加载阻断代码、领域策略已审计。 | 未填充设备或发送可能触发全局清理的广播，真机尚未验证。 |
| 24 | TDLib 客户端正确关闭 | `LogOut → LoggingOut/Closing/Closed`、旧 session 清除和不自动重启测试通过。 | 根 `connectedDebugAndroidTest` 的新 TDLib 测试包被 OEM 拒绝安装，真实账号退出也未执行；本阶段 ARM64 native 复验尚未验证。 |
| 26 | 协程作用域无泄漏 | 使用结构化 `viewModelScope`/应用范围 SupervisorJob，页面 jobs 明确 cancel；长滑后 Activity/Views 未增长。 | 未运行 LeakCanary、堆转储或 30 分钟 soak，不能证明绝对无泄漏；尚未完全验证。 |
| 28 | 不存在主线程磁盘或网络操作 | 启动文件统计和设备信号读取已移到 I/O dispatcher；lint、冷启动和日志无 `NetworkOnMainThreadException`。 | 未启用覆盖全路径的 StrictMode instrumentation；尚未完全验证。 |
| 33 | 受保护内容遵守限制 | `WindowSecurityController` 的 FLAG_SECURE 设置/恢复、无导出入口、缓存 pin/删除保护自动化通过。 | 前 200 个真实条目未遇到 `canBeSaved=false`，真机 FLAG_SECURE 尚未验证。 |

## 4. 自动测试结果

| 命令/层级 | 结果 |
|---|---|
| `gradlew.bat test --rerun-tasks --no-daemon --console=plain` | 通过；345 个 Gradle 任务全部执行。XML 共 400 次测试执行，0 failure、0 error、0 skipped；其中包含 debug/release 以及 instrumentation variant 对相同 JVM 测试的变体执行。 |
| `gradlew.bat lint --rerun-tasks --no-daemon --console=plain` | 通过；220 个任务全部执行。仅有已知 deprecated storage broadcast 编译警告。 |
| `gradlew.bat assembleDebug --rerun-tasks --no-daemon --console=plain` | 通过；190 个任务全部执行。 |
| Compose host compile | 通过。 |
| Robolectric Compose | 30/30 通过。 |
| API 36 AOSP x86_64 emulator Compose | 30/30 通过，26.875 秒；target APK native 条目数 0。 |
| API 36 emulator Room | 12/12 通过。 |
| ARM64 真机 install+launch smoke | 通过。 |
| 隔离 instrumentation 包 Monkey | 1000/1000 事件注入完成，无目标包 crash；未对生产账号包执行，避免随机触发退出/清缓存。 |
| 根 `connectedDebugAndroidTest`（Android 13 真机） | **未通过**：core/database、player、telegram 三个新测试 APK 均返回 `INSTALL_FAILED_USER_RESTRICTED`，实际 0 tests。串行、streamed/no-streaming 均复现；不是测试断言失败。 |

首次目标回归曾在 Robolectric 冷启动路径因 `PowerManager.addThermalStatusListener` 抛运行时异常而失败；注册已改为安全降级，随后目标测试和全量 Proof 全部通过。根 connected 失败仍保留，不改写为通过。

## 5. 人工测试结果

- 设备：`21091116UC`，Android 13 / SDK 33，ARM64；最终 APK 以 `adb install -r -t` 更新成功，`firstInstallTime` 保持不变，现有登录、Room 和 TDLib 会话数据库未被清除。
- 冷/热启动、登录恢复、后台返回、锁屏返回、杀进程恢复、系统返回、100 次连续信息流滑动、30 组快速往返、热状态、省电状态、90° 旋转、真实缓存清理和音频焦点申请/释放通过。
- 信息流实际播放和范围下载发生；长滑中出现一次可恢复 `FILE_UNAVAILABLE`，应用继续存活且后续可返回频道页。
- 未执行真实退出登录、真实频道/消息删除、填满存储、物理耳机拔出或其他应用音频抢占。
- 设备网络由 VPN 承载；关闭 Wi-Fi 后只有 VPN agent、没有 `MOBILE CONNECTED`，同时未形成可信的完全断网，因此 Wi-Fi→移动数据和断网恢复仍为尚未验证。

## 6. 性能观察

| 场景 | 观察 |
|---|---|
| 最终 APK 冷启动 | 1114ms；缓存清理后一次为 1041ms；launch smoke 为 1200ms。 |
| 最终 APK 热启动 | 后台返回 60ms、72ms；同阶段较早为 68ms、58ms。 |
| 100 次连续滑动 | 2088 frames；jank 100（4.79%）；P50/P90/P95/P99 为 5/9/13/16ms。 |
| 30 组快速往返 | 1105 frames；jank 56（5.07%）；P50/P90/P95/P99 为 5/6/11/16ms。 |
| 内存 | 100 滑前 PSS 225,248KB；滑后 230,567KB；快速往返后 190,703KB。Activity 始终 1，Views 始终 54。 |

这些是一次设备会话的观察，不是长期 soak 或跨机型性能承诺。

## 7. 缓存观察

- 本轮信息流验收前私有 `cache` 为 57,538KB；滑动和重新下载后最高观察到 65,738KB，没有随页面数线性增长。
- 通过应用设置页“手动清空视频缓存”二次确认后，`cache` 降至 8,858KB，界面显示清理成功。
- 清理前后 `databases` 均为 3,488KB、`files` 均为 15KB；`no_backup` 从 48,879KB 到 48,895KB，登录会话和 TDLib/索引数据未被清除。
- 清理后强停冷启动恢复 READY，频道页仍显示视频索引入口。默认上限仍为 500MB。

## 8. 权限与敏感信息检查

- 最终 APK 权限恰好为 `INTERNET`、`ACCESS_NETWORK_STATE`。
- `usesCleartextTraffic=false`；无联系人、短信、电话、麦克风、摄像头、位置、通知或存储权限。
- 源码无 MediaStore、公共 Downloads/DCIM/Movies 或外部存储写入路径。
- 真实 API ID/Hash 在 `local.properties` 以外均 0 命中；动态允许日志也均 0 命中。
- 最终 APK 只有 ARM64 native：`libtdjni.so`、`libandroidx.graphics.path.so`、`libdatastore_shared_counter.so`；无 `libc++_shared.so`。DataStore native 与官方 `datastore-core-android:1.2.1` AAR 的 SHA-256 一致。
- debug APK 会按设计包含本机 Telegram API 凭证，不得公开分发。

## 9. 修改文件

生产代码：

- `app/src/main/java/com/qixuan/channelvideoflow/cache/AndroidDevicePreloadPolicySource.kt`
- `app/src/main/java/com/qixuan/channelvideoflow/feature/video/VideoPlaybackScreen.kt`
- `app/src/main/java/com/qixuan/channelvideoflow/feature/video/VideoPlaybackViewModel.kt`
- `app/src/main/java/com/qixuan/channelvideoflow/feature/video/WindowSecurityController.kt`
- `player/src/main/java/com/qixuan/channelvideoflow/player/VideoPlaybackController.kt`
- `player/src/main/java/com/qixuan/channelvideoflow/player/VideoPlayerManager.kt`
- `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/di/TelegramCoroutineModule.kt`
- `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/media/TdLibMediaCacheManager.kt`

测试：

- `app/src/test/java/com/qixuan/channelvideoflow/feature/video/VideoPlaybackViewModelTest.kt`
- `app/src/test/java/com/qixuan/channelvideoflow/feature/video/WindowSecurityControllerTest.kt`
- `app/src/sharedTest/java/com/qixuan/channelvideoflow/feature/video/VideoPlaybackScreenTest.kt`
- `player/src/test/java/com/qixuan/channelvideoflow/player/VideoAudioPolicyTest.kt`

文档：

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/SECURITY.md`
- `docs/ACCEPTANCE_TESTS.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/TDLIB_BUILD.md`
- `docs/STAGE8_FINAL_ACCEPTANCE.md`

## 10. 已知问题与后续可选优化

1. 在用户可监督时单独执行真实退出登录并确认会话、Room 索引和媒体清理；这是验证，不是新功能。
2. 关闭/旁路 VPN 后复验 Wi-Fi→蜂窝→完全断网→恢复；当前设备条件无法提供可信 transport 证据。
3. 使用专用低剩余空间测试设备复验低存储；不得在日常设备上填满磁盘。
4. 使用物理耳机和第二个音频应用复验 becoming-noisy 与焦点丢失。
5. 准备一个明确 `canBeSaved=false` 的测试频道消息和一个不受设备支持的编码样本，复验 FLAG_SECURE 与真实解码失败。
6. 若需要更强泄漏结论，可做 30 分钟以上 soak、heap diff 和 StrictMode 专项；当前结果只证明本次 300 次滑动未出现线性 Activity/View/媒体缓存增长。

## 11. debug APK

- 路径：`E:\Telegram Android Developer\app\build\outputs\apk\debug\app-debug.apk`
- 大小：44,317,801 bytes
- SHA-256：`16EF0B58B27234900E5AF6A523D41E6AA78944EF4736E2B1F3BBB3BB73CA7F66`
