# 阶段 6 上下滑动信息流交接

日期：2026-07-27
状态：信息流实现、沉浸式界面重构、主机、API 36 x86_64 emulator 与实体设备安装/启动验证通过。本文件记录阶段 6 当时尚未实现的旧 200MB 缓存合同；阶段 7 已由仓库所有者改为 500MB 默认值和八档上限，以阶段 7 交接为准。

## Outcome

已索引视频以 Compose `VerticalPager` 竖向浏览。共享 `VideoPlayerManager` 只在最终稳定页面绑定一个视频；当前页面可播放，滑动时旧音频立即暂停，快速连续滑动只保留最后一次稳定页请求。

## 2026-07-27 沉浸式界面修订

- 播放页使用全屏视频背景、顶部“最新/随机”标签、右侧静音/原消息操作栏和底部渐变元数据，不再使用遮挡画面的大块半透明信息卡。视频画面可点按暂停/继续；暂停时中央显示低透明度继续播放提示；底部使用贴边细进度线，拖动时显示当前时间/总时长并在松手后 seek。
- `PlayerView` 原生控制器和原生缓冲指示器关闭，页面只展示应用自己的单一控制层；播放器实例与绑定策略不变。
- 正在准备视频时使用简洁黑底加载页；网络/超时/文件/解码/播放器失败使用图标、标题、说明和宽“重试”按钮。
- 空列表使用同一视觉层级，但显示真实的“暂无可播放视频”原因，并提供“返回频道选择”，不把空索引伪装成网络失败。
- `supportsStreaming=false` 仍显示精确文案“该视频暂不支持流式播放。”，不绑定播放器，并允许打开原消息或继续滑动。
- 未增加点赞、评论、收藏、分享、头像或虚构互动数据，也未复制短视频品牌元素。

## 2026-07-28 播放交互细节修订

- 竖向翻页或拖动进度时，视频简介和底部渐变在 90 ms 内降至 30% 透明度；交互结束后保留 320 ms，再用 220 ms 恢复，减少文字遮挡移动画面。
- 进度线在系统导航栏安全区之上再上移 16 dp；简介底部同步调整到 36 dp，避免文字、时间和进度线相互挤压。
- 仅当视频元数据满足 `width > height && height > 0` 时，在画面下缘外侧显示“全屏观看”描边按钮。进入后请求传感器横屏并隐藏系统栏，锁住当前 Pager 页面，隐藏顶部栏、简介、渐变和右侧操作，只保留点按暂停、中央暂停提示、进度拖动和退出全屏。
- `MainActivity` 接管方向与屏幕尺寸配置变化，横竖屏切换不重建 Activity；仍复用原来的单个 `PlayerView`/ExoPlayer，没有创建第二播放器。返回键和右上角按钮都先退出全屏并恢复竖屏与系统栏。
- `PlayerView` 明确使用 `RESIZE_MODE_FIT`，不裁剪 16:9 内容；竖屏视频不显示全屏入口。

### 本次验证

- `VideoPlaybackScreenTest`：8/8 通过，新增横屏入口、全屏控制层和竖屏不显示入口的覆盖。
- fresh `test`、`lint`、`assembleDebug`：通过；`compileDebugKotlin` 与 `compileInstrumentationKotlin`：通过。
- API 36 AOSP `x86_64` emulator：Compose UI 套件 27/27 通过。
- Xiaomi 21091116UC、Android 13/API 33：安装和冷启动通过，`MainActivity` 保持前台，目标包 crash buffer 为空。
- 同一实体设备的真实信息流中已检查：翻页中简介淡化并恢复、拖进度时简介淡化并恢复、进度线底部间距、横屏入口位置、进入 2400×1080 全屏、系统栏和信息层隐藏、退出全屏并恢复竖屏；均符合本次预期。
- iQOO 12 / OriginOS 6 / Android 16 本次未重新执行视觉人工验证；Compose Path B 的 Vivo 限制不变。

## 2026-07-28 随机历史视频文件引用修复

- 根因：随机模式更容易选中较早索引的视频；其 Room 元数据中的 TDLib `fileId` 可能已经失效，而原“重试”只会重新绑定同一个旧 `fileId`，因此重复点击无法恢复。
- `FILE_UNAVAILABLE` 的重试现在先按 `chatId + messageId` 调用官方 `getMessage`，将最新普通 `messageVideo` 文件引用和元数据原子更新回 Room，再把刷新后的应用模型交给唯一播放器。刷新失败时保留原错误页，不伪造成功，也不无限重试。
- 同一随机轮收到 Room 元数据更新时不再整轮重洗牌；现有键保持顺序、删除项移除、新增项只追加到随机尾部。这样当前页的文件引用刷新不会悄悄换成另一条视频。
- 主机针对性回归覆盖 TDLib 消息刷新、随机模式重试绑定新 `fileId`、随机轮更新顺序稳定；fresh `test`、`lint`、`assembleDebug` 均通过。Xiaomi 21091116UC Android 13 安装和冷启动 smoke 通过；真实登录态的随机历史视频恢复尚未验证。

## 2026-07-28 随机首次播放免手动重试修复

- 后续真实账号复测确认：上一项修复只能让用户点击“重试”后恢复，但随机模式每个稳定页的第一次播放仍先绑定 Room 中的旧 `fileId`，所以每刷一条都会先显示“Telegram 文件已失效”。
- 随机模式现在把同一条官方 `getMessage` 元数据刷新前移到稳定页首次绑定之前；刷新成功后直接把新 `fileId` 交给唯一播放器。顺序模式不执行这项额外刷新，避免改变其现有加载路径。
- 刷新前后都按稳定页 generation 和 `chatId + messageId` 复核当前项。快速滑走、离开页面、切换顺序或筛选会取消旧刷新；Room 写回导致对象元数据变化时不会误判成换页，也不会让旧页晚返回后抢占播放器。
- 刷新失败时回退到现有绑定和可恢复错误状态，不自动循环请求；原 `FILE_UNAVAILABLE` 手动重试仍作为第二层恢复路径保留。
- JVM 回归覆盖随机首条、随机轮连续三页、刷新期间离页取消，以及顺序模式不额外刷新；fresh `test`、`lint`、`assembleDebug` 均通过。真实登录账号下不再逐条出现失效页仍需用户复验。

## 实现边界

- `VideoPlaybackViewModel` 只依赖 `TelegramMessageRepository`、`TelegramChatRepository` 和 `VideoPlaybackController`；Compose 不直接接触 TDLib、Room、下载或 ExoPlayer。
- 页面只创建一个 `PlayerView`，应用范围只有一个 `VideoPlayerManager`/ExoPlayer。新绑定先暂停、停止并清空旧媒体，再设置新 `TelegramMediaDataSource`，不会有两路音频重叠。
- 信息流显示频道名、caption、标签、发布时间、加载、播放错误、重试、点按暂停/继续、中央暂停态提示、可拖动进度、静音和“打开 Telegram 原消息”。
- 原消息链接先调用官方 `getMessageProperties` 检查 `canGetLink`，再调用 `getMessageLink`；结果仅通过 HTTPS Android Intent 导航，绝不作为媒体地址。链接不可用显示“无法打开 Telegram 原消息”。
- 最新模式按 `publishTime DESC, chatId DESC, messageId DESC`。随机模式由纯领域 `VideoPlaybackQueue` 洗牌；每轮不重复，轮次边界避免上一轮最后一项，单项队列允许重复。随机 Pager 使用常量大小的逻辑页窗口，不累积历史页面元数据。
- Room Flow 移除已删除或不可访问消息后，当前绑定被释放；频道/标签筛选或顺序切换会立即取消稳定页任务、停止旧绑定并建立新队列。

## 本阶段明确未做

- 阶段 6 当时未做下一条预加载、旧 200MB LRU、缓存设置、网络/电量/热策略及内容保护 `FLAG_SECURE`；其中预加载、缓存与策略由阶段 7 接续。
- 自动化已覆盖点按暂停/继续、中央提示和松手 seek；不声明真实账号 seek、断网和外部 Telegram Intent 跳转已通过。本次为保护个人内容，没有触发外部原消息 Intent。
- 阶段 6 当时旧 200MB LRU 尚未实现。真机的 `cache/tdlib/files` 在测试前已为 180,124 KB，播放和静置后达到 323,192 KB；该历史结果是阶段 7 有界缓存的回归基线。

## 主机验证结果

- `:core:domain:test`：通过（随机队列 4 项加既有测试）。
- `:telegram:test`：通过（包含官方消息属性/链接边界测试）。
- `:app:testDebugUnitTest --tests VideoPlaybackViewModelTest`：通过（稳定页、取消、生命周期控制）。
- `:app:testInstrumentationUnitTest --tests VideoPlaybackScreenTest`：5/5 通过（沉浸式网络/播放错误、空列表、右侧控制、顺序切换和非流式文案）。
- fresh `test`、`lint`、`assembleDebug`：通过。
- `:app:compileInstrumentationKotlin`、共享 Robolectric Compose suite、instrumentation APK 打包：通过；target APK `jar tf` 无 `lib/` 条目。

## 真机验证结果

- 设备：iQOO 12（V2307A），Android 16 / API 36，`arm64-v8a`，ADB 状态 `device`。
- `run-vivo-launch-smoke.ps1`：通过。debug APK 安装、冷启动、`MainActivity` resumed，目标包 crash buffer 为空。Vivo 准备脚本的部分厂商不支持命令按 `BEST_EFFORT_UNSUPPORTED` 记录，但应用后台限制最终为 `unrestricted`。
- 真实信息流：以 90 ms 手势、150 ms 间隔连续上滑 50 次。测试后 `MainActivity` 仍在前台，目标包未发现崩溃；页面稳定后信息流与控制项仍可见，未出现播放错误。
- 请求所有权：该轮 logcat 记录 25 个 TDLib 区间请求、3 个“cancel unused range”、8 个“skip stale cancel”、0 个区间请求失败；返回频道页释放播放器后又有取消记录，20 秒尾部写入约 2,820 KB，随后连续 30 秒缓存增量为 0。
- 内存：快速滑动前后 PSS 为 229,321 KB / 325,184 KB；稳定 30 秒为 323,797 KB，未继续增长。该观察不足以替代长时间内存剖析。
- 黑屏/音频：稳定页的非持久化屏幕像素采样中 72.46% 为近黑像素，且播放控制和信息流 UI 可访问；未发现崩溃或播放器错误。自动化环境无法听到扬声器，故“没有双音频”由单一 ExoPlayer 设计、Fake/生命周期测试和播放器释放日志共同支持，仍应由人工听感复验。
- 操作：暂停/继续、静音/恢复声音均在真机上可见地切换并恢复；“打开 Telegram 原消息”控件存在。最新优先可切换到随机播放并恢复，过程无播放错误。

## 本次界面修订验证

- API 36 AOSP `x86_64` emulator：`run-emulator-compose-tests.ps1 -Serial emulator-5554` 通过，Login、ChannelSelection、Smoke 与 VideoPlayback 共 25/25。
- 实体设备：Xiaomi 21091116UC、Android 13/API 33、arm64-v8a；`installDebug` 通过，`MainActivity` 冷启动 1,108 ms、处于 `topResumedActivity`，目标包 crash buffer 为空。
- 界面窗口截图在 emulator instrumentation 环境分别检查播放中、网络错误和空列表；网络错误页的分页遮挡层级在检查中被发现并修正，最终截图保留顶部导航且无控件重叠。
- 本次没有重新执行 iQOO 12 / Android 16 的 50 次快速滑动；上节历史结果仍有效，但新视觉布局在该设备上的人工观感尚未验证。

Vivo 完整 instrumentation 不是本项目的 Compose 通过条件；OriginOS 6 的 `fast_freezer`/`single-cleaner` 诊断限制仍适用。
