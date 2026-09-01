# VELORA（曜流）— 仓库协作规则

本文件适用于仓库根目录及全部子目录。开始任何修改前，必须先阅读 README.md，以及与任务相关的 docs 文档。

## 1. 项目定位

- 应用名称：VELORA；中文名：曜流。
- 包名：com.qixuan.channelvideoflow。
- 用途：仅供仓库所有者个人使用的原生 Android Telegram 频道视频浏览器。
- 当前状态：Stage 23 已完成频道视频索引扫描优化；VELORA（曜流）保持 1.0，开源准备只调整仓库文档、许可证和忽略规则，ARM64/Vivo 真机验证尚未执行。
- 未经用户明确批准，不得进入下一阶段。

## 2. 固定技术合同

- Kotlin、Jetpack Compose、Material 3。
- Kotlin Coroutines、Flow。
- AndroidX Media3 ExoPlayer。
- Room、DataStore。
- Telegram 官方 TDLib，使用个人用户账号授权；禁止 Bot API 和手写 MTProto。
- Gradle Kotlin DSL 与 gradle/libs.versions.toml 版本目录。
- MVVM、Repository、分层架构和依赖注入。
- 优先 Hilt。只有出现可复现的 TDLib 生命周期冲突并形成书面架构决定后，才可改用手动构造函数注入。
- minSdk 26；compileSdk 使用本机已安装的最新稳定正式平台；targetSdk 使用与已安装正式平台和稳定 AGP 兼容的最新正式 API。
- 只使用稳定依赖。任何 alpha、beta、RC 或 snapshot 都必须先说明必要性并取得用户同意。

## 3. 强制依赖方向

依赖必须保持以下方向：

Compose UI → ViewModel → UseCase/Repository 接口 → Repository 实现 → 基础设施适配器 → 官方 TDLib、Room 或 Media3。

- UI 不得直接使用 TDLib、Room DAO 或创建 ExoPlayer。
- ViewModel 只能调用 UseCase 或 Repository 接口。
- TDLib 类型、回调和错误不得越过 telegram 模块的数据边界。
- 任意时刻只允许一个主要 ExoPlayer 发声；播放器实例不得随页面数量增长。
- 视频字节只允许位于应用内部缓存目录；Room 只保存元数据。

## 4. 阶段工作合同

每个实现阶段开始前必须写明：

1. Outcome：一个可观察的用户行为或架构能力。
2. Scope：预计新增或修改的确切文件、模块。
3. Boundary：本阶段明确不做的事项。
4. Failure states：加载、空状态和适用的错误状态。
5. Proof：能够独立验证本阶段的测试或构建命令。

一次只完成一个阶段。若 Proof 失败，停止扩展功能，先定位首个根因。

## 5. 开始修改前的检查

每次必须先执行只读检查：

1. 确认仓库根目录和当前分支。
2. 执行 git status，保护用户的未提交修改。
3. 阅读所有适用的 AGENTS.md、README.md 和相关 docs 文件。
4. 查看当前模块、构建文件和实现路径，不创建平行架构。
5. 给出当前阶段计划和验证方法。

禁止覆盖、删除或格式化与当前阶段无关的用户文件。禁止 git reset --hard、git clean -fd 和自动 push。

## 6. Telegram 与授权安全

- 只能从未跟踪的 local.properties 读取 TELEGRAM_API_ID 和 TELEGRAM_API_HASH。
- 不得要求用户在聊天中粘贴真实 api_hash。
- 不得在源码、文档、测试、提交或日志中出现真实凭证。
- 不得记录验证码、两步验证密码、完整手机号、数据库密钥、会话数据或完整 TDLib 对象。
- 验证码和密码不得持久化；授权成功、取消或失败终止后清空内存引用。
- 授权状态必须来自 TDLib updateAuthorizationState，不得用延时、模拟数据或假登录代替。
- FLOOD_WAIT 必须遵守服务器等待时间；重试必须有上限、退避、可取消且不阻塞主线程。

## 7. 媒体、缓存与内容保护

- 不得把 Telegram 消息链接伪装成媒体 URL，也不得将消息链接交给 ExoPlayer 播放。
- 通过自定义 Media3 DataSource 将 DataSpec 的 position/length 转换为 TDLib downloadFile 的 offset/limit 区间请求。
- 区间请求必须可取消、可超时，并在 ExoPlayer 加载线程而非主线程等待。
- 第一版只播放 supportsStreaming=true 的普通 messageVideo。
- supportsStreaming=false 时不得自动完整下载，必须显示“该视频暂不支持流式播放。”
- 只预加载下一条的少量数据；移动数据默认禁用预加载。
- 默认媒体缓存上限为 500MB，可选 200MB、500MB、1GB、2GB、5GB、10GB、15GB、20GB。
- 不得同时建立 TDLib 完整缓存和 Media3 完整文件缓存。
- 当前播放和下一条预加载数据必须通过所有权令牌保护，清理时不可误删。
- 不得写入 MediaStore、Downloads、DCIM、Movies 或其他公共目录。
- 不得请求广泛存储权限。
- 对受保护内容不提供保存、导出或分享；播放期间按策略启用 FLAG_SECURE。

## 8. 数据与同步规则

- 视频唯一键是 chatId + messageId，禁止假设 messageId 全局唯一。
- 频道只包含当前账号可访问、chatTypeSupergroup 且 supergroup.isChannel=true 的聊天。
- 历史扫描分页写入 Room，每页独立事务提交并保存游标。
- 增量同步处理新消息、内容更新和删除更新；不得重复插入。
- 扫描阶段只写元数据，不得下载完整视频。
- 标签优先使用 formattedText 中的 textEntityTypeHashtag；仅在无实体时使用经测试的 Unicode 回退解析器。
- 英文标准化必须使用 Locale.ROOT；频道为 OR，频道与标签之间为 AND，标签支持 OR/AND。
- 退出账号时停止请求和播放，清理账号相关索引与缓存，不保留跨账号数据。

## 9. 权限和隐私

第一版清单只允许：

- android.permission.INTERNET
- android.permission.ACCESS_NETWORK_STATE

新增任何权限必须先说明理由并获得用户确认。禁止联系人、短信、电话、麦克风、摄像头、位置、通知和广泛存储权限。

Android 备份必须禁用，并同时用 dataExtractionRules/fullBackupContent 排除 TDLib 数据库、会话、本地密钥、媒体缓存、敏感 DataStore 和 Room 数据，覆盖云备份与设备迁移差异。

## 10. 测试与验证

业务逻辑必须依赖接口并可使用 Fake 测试。不得在自动化测试中使用真实 Telegram 账号、验证码、密码或 api_hash。

Windows 上优先执行：

    .\gradlew.bat test
    .\gradlew.bat lint
    .\gradlew.bat assembleDebug

连接授权真机时再执行：

    adb devices
    .\gradlew.bat installDebug
    .\gradlew.bat connectedDebugAndroidTest

阶段 0 没有 Gradle 工程，上述命令不可用时必须报告“尚未验证”，不得伪造结果。

### Compose 门槛（Path B）

在 iQOO 12 / OriginOS 6 / Android 16 上，完整 Compose instrumentation 曾被系统 `fast_freezer`/`single-cleaner` 杀死；这不是 Compose 代码、Room、SQL、Gradle 或 TDLib native 的失败证据。不得在没有改变设备策略的情况下重复同一条 Vivo `am instrument` 命令。

Compose Proof 固定为：

    Proof(Compose) = 编译通过 ∧ Robolectric-Compose 通过 ∧ emulator-Compose-UI 通过 ∧ Vivo 真机 install+launch smoke 通过

执行顺序和脚本如下：

    $env:JAVA_HOME = 'E:\Android Studio\jbr'
    .\gradlew.bat :app:compileInstrumentationKotlin --no-daemon --console=plain
    .\gradlew.bat :app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.auth.LoginScreenTest" --tests "com.qixuan.channelvideoflow.feature.channels.ChannelSelectionScreenTest" --tests "com.qixuan.channelvideoflow.test.ComposeSmokeTest" --no-daemon --console=plain
    .\gradlew.bat :app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.settings.CacheSettingsScreenTest" --no-daemon --console=plain
    .\scripts\run-emulator-compose-tests.ps1 -Serial <x86_64-emulator-serial>
    .\scripts\run-vivo-launch-smoke.ps1 -Serial <Vivo-serial>

- Emulator UI 只使用 API 36 AOSP `x86_64` AVD；不得用 ARM64 AVD，也不得在该 emulator 跑 TDLib native smoke。
- instrumentation build type 必须排除 target APK 的所有 `.so`，但不得改变 debug/release 生产 APK 中的 TDLib native `.so`。
- Vivo 只验证安装、冷启动、`MainActivity` resumed/top 和无目标包 crash；完整 Vivo instrumentation 仅保留作系统环境诊断证据，不是通过条件。
- Boundary：`Vivo/OriginOS 6 + Android 16 对 adb 安装包的后台/自启动管控属于设备环境限制，非代码缺陷；当且仅当步骤 2/3 已执行仍不可达时，真机完整 instrumentation 不计入 Failure，改由步骤 4 的等价组合证明。`

## 11. 日志规则

- Debug 仅允许记录状态名、请求类型、chatId/messageId、播放器状态、区间范围、缓存统计和脱敏错误码。
- Release 关闭详细调试日志。
- 默认不记录消息正文；只有用户主动开启调试开关时才允许短期记录，并仍不得包含凭证、密码或媒体字节。

## 12. 完成汇报

每阶段完成后必须按以下顺序汇报：

1. 一、本阶段目标
2. 二、实际完成内容
3. 三、修改文件
4. 四、关键架构决定
5. 五、执行的命令
6. 六、测试结果
7. 七、真机验证结果
8. 八、已知问题
9. 九、安全检查结果
10. 十、建议的下一阶段

所有未实际验证的内容必须写“尚未验证”。不得自动提交；测试通过后只可建议提交，并在提交前展示变更摘要。
