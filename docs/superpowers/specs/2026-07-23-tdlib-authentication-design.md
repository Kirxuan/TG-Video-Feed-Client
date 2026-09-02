# 阶段 2：官方 TDLib 与真实 Telegram 授权设计

日期：2026-07-23

状态：方案 A 和书面规格均已由仓库所有者批准

> 历史说明：本文记录 Stage 2 当时的本机自构建凭证合同。仓库所有者于 2026-09-01 明确批准 Stage 24 用户自行配置版；公开 release 的凭证来源、Keystore 存储和客户端重建以 `docs/STAGE24_USER_CONFIGURED_CREDENTIALS.md` 为准。本文其余 TDLib 授权状态与安全边界继续有效。

适用应用：Channel Video Flow
包名：`com.qixuan.channelvideoflow`

## 1. Outcome

本阶段完成一个可在真实 Android 手机上观察和验收的单账号 Telegram 授权闭环：

1. 应用加载由本机官方源码构建的 TDLib Java/JNI 产物。
2. 应用根据 TDLib `updateAuthorizationState` 显示手机号、验证码和两步验证密码输入。
3. 用户只在手机界面输入敏感信息，Codex 不读取、不要求、不记录验证码或密码。
4. TDLib 在应用私有目录保存会话；杀死并重启应用后继续保持已登录状态。
5. 用户明确退出账号后，应用等待 TDLib 完成退出和关闭，再返回手机号登录页。
6. 自动化测试能在不使用真实 Telegram 账号或 native 库的情况下验证授权状态转换和 ViewModel 行为。

## 2. Scope

本阶段只包含：

- 官方 TDLib 源码固定、构建、产物校验和 Android 模块集成。
- Telegram 客户端创建、初始化、关闭和退出生命周期。
- 手机号、验证码、两步验证密码授权。
- TDLib 会话恢复。
- 完整授权状态识别和安全降级。
- 授权错误、网络错误和 `FLOOD_WAIT` 映射。
- `TelegramAuthRepository`、真实实现和测试 Fake。
- `AuthViewModel` 与真实 Compose 登录界面。
- 应用私有目录、备份排除、日志脱敏和凭证边界验证。
- JVM、lint、debug 构建、仪器测试、真机安装、logcat 和人工授权验收。

现有 `docs/DEVELOPMENT_PLAN.md` 将 native 构建、客户端生命周期和真实授权分列为旧阶段 2、3、4。本次仓库所有者的明确指令优先：三部分合并为新的阶段 2，并在实现过程中同步修订项目文档。

## 3. Boundary

本阶段不创建或实现：

- 频道列表或频道选择。
- 历史消息或视频扫描。
- Room 业务数据库和账号索引。
- 视频文件请求、播放、预加载或媒体缓存。
- 标签、筛选、播放队列或信息流。
- 邮箱授权、邮箱验证码、新用户注册、二维码确认、Premium 购买或密码找回流程。
- Bot API、手写 MTProto、第三方 Telegram 网关或第三方预编译 TDLib。

若 TDLib 返回本阶段不支持的交互步骤，应用必须显示明确的安全阻断状态，不得自动跳过、模拟成功或误报已登录。

## 4. 已验证环境

- 仓库根目录：`E:\Telegram Android Developer`。
- Git 分支：`main`。
- 仓库尚无提交；现有阶段 1 文件全部为未跟踪文件，必须作为用户成果保护。
- ADB：`E:\AndroidStudio2.0\platform-tools\adb.exe`，版本 `37.0.0-14910828`。
- 真机：vivo V2307A，Android 16，SDK 36。
- 真机序列状态：`device`。
- 唯一 ABI：`arm64-v8a`；`ro.product.cpu.abilist` 也只有 `arm64-v8a`。
- Android SDK 根目录：`E:\AndroidStudio2.0`。
- 当前缺少 Android SDK Command-line Tools、NDK 和 CMake。
- 当前缺少完整 MSYS2 native 构建环境。
- `local.properties` 已被 `.gitignore` 忽略且不在 Git index 中。
- `TELEGRAM_API_ID` 和 `TELEGRAM_API_HASH` 当前未配置；真实人工授权前必须由仓库所有者在本机文件中填写。

## 5. 官方来源与固定版本

### 5.1 TDLib

- 官方源码：`https://github.com/tdlib/td.git`。
- 固定提交：`022d60202e446ad1287b9fb68e687c8a0760788b`。
- 该提交的 CMake 项目版本：`1.8.66`。
- 提交日期：2026-07-17 UTC。
- 接口：官方 Java/JNI，不使用 JSON 网关或第三方封装。
- Android C++ 运行库：`c++_static`，避免额外共享运行库和 ABI 文件。
- 目标 ABI：只构建 `arm64-v8a`。
- Android native 最低平台：`android-26`，与应用 `minSdk=26` 一致。

源码必须克隆到无空格的构建目录：

`E:\tdlib-build\channel-video-flow\022d60202e446ad1287b9fb68e687c8a0760788b`

构建脚本在执行任何编译前验证 `git rev-parse HEAD` 与固定提交完全一致。工作区不保存可漂移的 `master` 引用。

### 5.2 OpenSSL

- 官方源码包：OpenSSL 官方 GitHub Release。
- 固定版本：`3.5.7 LTS`。
- SHA-256：`a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8`。
- 构建方式：静态库，只构建 `android-arm64`，最低 API 26。

TDLib 官方 Android 脚本默认的 OpenSSL 1.1.1 已停止公开支持。本阶段不使用该默认值。若 TDLib 固定提交与 OpenSSL 3.5.7 出现可复现的源码兼容问题，停止 native 扩展并向仓库所有者提交首个根因；不得静默降级到停止支持的版本。

### 5.3 Native 工具链

- Android NDK：`23.2.8568313`，采用固定 TDLib 官方 Android 脚本当前默认值。
- Android CMake：`3.22.1`，采用固定 TDLib 官方 Android 构建说明要求。
- Android SDK Platform：保留现有 `android-36.1`；TDLib Java 文档生成需要的 `android-34` 平台若缺失，由官方 SDK Manager 安装。
- Host shell/toolchain：MSYS2 UCRT64，安装 GCC、Ninja、make、gperf、PHP、Perl、wget、unzip 等官方脚本实际检查到的依赖。
- JDK：`E:\Android Studio\jbr`。

安装完成后，构建记录必须保存 `sdkmanager --list_installed` 的相关组件版本、`pacman -Q` 的直接构建包版本、JDK/CMake/Ninja/编译器版本和命令退出码。

## 6. 可复现构建方式

仓库新增两个受审计脚本：

- `tools/tdlib/build-android-arm64.sh`：以固定官方 `example/android` 脚本为依据，只构建 `arm64-v8a` 和 `android-26`。
- `tools/tdlib/build-android.ps1`：验证工具链、源码 SHA、OpenSSL SHA，调用 MSYS2，并将产物复制到 Android 模块。

构建流程：

1. 从官方仓库克隆并 checkout 固定 TDLib 提交。
2. 验证 Git remote、完整提交 SHA 和干净源码树。
3. 下载 OpenSSL 3.5.7 官方源码包并验证固定 SHA-256。
4. 使用 NDK 23.2.8568313 为 `arm64-v8a` 构建静态 OpenSSL。
5. 生成 TDLib Java API 源码。
6. 使用 CMake 3.22.1、Ninja、`android-26` 和 `c++_static` 构建 `libtdjni.so`。
7. 复制官方生成的 `Client.java`、`TdApi.java` 与 `libtdjni.so` 到 `:telegram:tdlib`。
8. 计算源码、Java 生成文件和 `.so` 的 SHA-256，并写入 `docs/TDLIB_BUILD.md`。
9. 检查 APK 的 `lib/` 只包含 `arm64-v8a`，逐项列出 native 文件来源；允许固定构建的 `libtdjni.so` 与 Compose `ui-graphics 1.11.4` 传递的官方 `androidx.graphics:graphics-path:1.0.1` 所提供的 `libandroidx.graphics.path.so`，不允许其他 ABI、未知 native 文件或 `libc++_shared.so`。

不从 Maven、网盘、论坛、Telegram 消息、GitHub 非官方 fork 或其他未知来源下载 `.aar`、`.jar` 或 `.so`。

## 7. 模块与依赖方向

新增模块：

| 模块 | 职责 |
|---|---|
| `core:model` | 不依赖 Android 或 TDLib 的授权状态和错误模型 |
| `core:domain` | `TelegramAuthRepository` 接口 |
| `telegram:tdlib` | 官方生成 Java/JNI 绑定、来源记录和目标 ABI |
| `telegram` | TDLib 客户端生命周期、状态映射、Repository 实现和 Hilt 绑定 |
| `app` | BuildConfig 凭证适配、AuthViewModel、Compose UI 和导航 |

依赖方向固定为：

`Compose UI → AuthViewModel → TelegramAuthRepository → TdLibTelegramAuthRepository → TelegramClientManager → 官方 TDLib`

禁止：

- `app` UI 引用 `org.drinkless.tdlib`。
- ViewModel 创建或关闭 TDLib Client。
- `telegram` 将 `TdApi` 类型、回调或原始错误暴露给领域层。
- TDLib 回调直接导航或修改 Compose 状态。

## 8. 主要接口

`TelegramAuthRepository` 对外提供：

- `val authState: StateFlow<TelegramAuthState>`，只读且不可由调用方修改。
- `suspend fun start()`。
- `suspend fun submitPhoneNumber(phoneNumber: String)`。
- `suspend fun submitCode(code: String)`。
- `suspend fun submitPassword(password: String)`。
- `suspend fun logout()`。

生产实现仅在 `telegram` 模块。`FakeTelegramAuthRepository` 位于测试源码，用于 ViewModel 和 UI 测试，不作为生产演示或运行时替代路径。

## 9. 客户端生命周期与目录

`TelegramClientManager` 是应用级单例和唯一 Client 所有者：

1. 凭证格式通过后加载 `tdjni`。
2. 创建唯一 Client，并将所有 update/response 送入单线程串行协程处理器。
3. 收到 `authorizationStateWaitTdlibParameters` 时只发送一次匹配当前状态 generation 的 `setTdlibParameters`。
4. TDLib 参数使用生产环境、官方 API ID/API Hash、设备/系统/应用版本和应用私有目录。
5. 普通应用关闭调用 `close` 并等待 `authorizationStateClosed`，不执行退出，从而保留会话。
6. 用户点击退出调用 `logOut`；收到 `LoggingOut`、`Closing`、`Closed` 后释放客户端并允许重新创建登录客户端。

目录固定为：

- 数据库/会话：`context.noBackupFilesDir/tdlib/database`。
- TDLib 文件：`context.cacheDir/tdlib/files`。

路径由 Android `Context` 生成，不硬编码真实设备路径，不使用外部存储。数据库加密 key 本阶段使用 TDLib 空 key 语义，不生成、记录或持久化额外秘密；安全边界依赖 Android 应用沙箱、`noBackupFilesDir` 和完全禁用备份。

## 10. 完整授权状态处理

固定 TDLib 提交包含以下 13 种状态：

| TDLib 状态 | 应用行为 |
|---|---|
| `WaitTdlibParameters` | `Initializing`，发送一次参数 |
| `WaitPhoneNumber` | 显示手机号输入 |
| `WaitCode` | 显示验证码输入 |
| `WaitPassword` | 显示两步验证密码输入 |
| `Ready` | `Authorized`，清除所有登录输入引用 |
| `LoggingOut` | 禁用交互并显示退出中 |
| `Closing` | 禁用交互并显示关闭中 |
| `Closed` | 释放旧 Client；退出场景重新进入登录初始化 |
| `WaitPremiumPurchase` | 安全阻断：当前登录步骤暂不支持 |
| `WaitEmailAddress` | 安全阻断：当前登录步骤暂不支持 |
| `WaitEmailCode` | 安全阻断：当前登录步骤暂不支持 |
| `WaitOtherDeviceConfirmation` | 安全阻断：当前登录步骤暂不支持 |
| `WaitRegistration` | 安全阻断：本应用不创建新 Telegram 账号 |

任何未来新增且当前生成 Java API 可识别、但映射器未支持的状态均进入 `UnsupportedAuthorizationStep`，绝不进入 `Authorized`。

## 11. 输入和 UI 状态

`AuthViewModel`：

- 只依赖 `TelegramAuthRepository`。
- 在 `viewModelScope` 中收集授权状态并输出不可变 `LoginUiState`。
- 手机号、验证码和密码只保存于普通内存字段/StateFlow，不使用 `SavedStateHandle`。
- 提交验证码或密码后立即清除 ViewModel 和 Compose 的对应输入引用；授权成功、退出、关闭和不可恢复错误时再次清理。
- 取消协程时重新抛出 `CancellationException`。

Compose UI：

- `UnconfiguredCredentials`：显示本机配置说明，仅显示缺失键名。
- `Initializing`：显示进度，不显示输入。
- `WaitingPhoneNumber`：手动输入国际格式手机号。
- `WaitingCode`：手动输入 Telegram 验证码。
- `WaitingPassword`：使用密码遮罩输入两步验证密码。
- `Authorized`：仅显示已登录状态和退出按钮；不进入频道功能。
- `LoggingOut/Closing`：显示不可重复点击的进度状态。
- `Unsupported/Error`：显示脱敏中文错误和允许的恢复操作。

手机输入法、系统键盘或 Telegram 本身的行为不由应用持久化；应用不请求短信、电话、联系人、通知或存储权限。

## 12. 错误模型

错误映射为应用自有封闭类型：

- `InvalidPhoneNumber`
- `InvalidCode`
- `InvalidPassword`
- `FloodWait(retryAt)`
- `NetworkUnavailable`
- `NativeLibraryLoadFailed`
- `TdLibInitializationFailed`
- `DatabaseFailed`
- `UnsupportedAuthorizationStep`
- `RequestRejected`
- `Unknown`

规则：

- 原始 TDLib `error.message` 不进入 UI，也不直接写入日志。
- `FLOOD_WAIT` 只从脱敏后的错误码/格式解析等待秒数，在 `retryAt` 前禁用提交。
- 手机号、验证码、密码错误不自动重试；TDLib 保持相应等待状态，由用户重新输入。
- 初始化、native 和数据库错误提供显式重试；不得自动删除会话目录。
- 所有重试有上限、可取消、不阻塞主线程。

## 13. 日志与敏感数据

应用 Debug 日志只允许：

- 授权状态名称。
- TDLib 请求类型。
- 非敏感关联 ID。
- 脱敏错误类别和数字错误码。

禁止记录：

- 完整或掩码不足的手机号。
- 验证码、密码、API Hash、数据库材料。
- 完整 TDLib 对象。
- 可能包含用户输入的异常 message。
- TDLib 私有目录绝对路径。

TDLib native 日志设为最低必要级别；真机观察使用应用包名和专用 Auth tag 过滤，不保存全量 logcat。

## 14. 备份与权限

现有清单必须继续只包含：

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

现有 `android:allowBackup="false"`、`dataExtractionRules` 和 `fullBackupContent` 继续全量排除 root、file、database、shared preferences 和 device-transfer 域。阶段 Proof 检查 merged manifest，而不是只检查源 XML。

## 15. 测试设计

### JVM 单元测试

- 13 种 TDLib 授权状态全部有明确映射测试。
- `WaitTdlibParameters` 同一 generation 只发送一次参数。
- 错误验证码后仍为验证码输入状态。
- 错误密码后仍为密码输入状态。
- `Ready` 清除验证码和密码。
- `LoggingOut → Closing → Closed` 顺序正确。
- `FLOOD_WAIT` 在截止时间前禁用提交。
- 未知/不支持状态不可能变成 `Authorized`。
- 日志事件不包含合成手机号、验证码、密码、API Hash 或数据库 key。
- `AuthViewModel` 只通过 Fake Repository 工作，不引用 TDLib。

### Android/Compose 测试

- 每个授权步骤只显示对应输入。
- 密码使用遮罩。
- 状态切换后敏感输入不保留。
- 加载、错误、不支持步骤和退出中状态禁用重复提交。
- Authorized 页面只显示退出，不出现频道、扫描或 Feed UI。

### Native 真机测试

- JNI 能加载并执行 TDLib 版本查询。
- APK 的 `lib/` 只包含 `arm64-v8a`；允许固定构建的 `libtdjni.so` 与 Compose `ui-graphics 1.11.4` 传递的官方 `androidx.graphics:graphics-path:1.0.1` 所提供的 `libandroidx.graphics.path.so`，不允许其他 ABI、未知 native 文件或 `libc++_shared.so`。
- 应用能接收真实 `updateAuthorizationState`。
- logcat 只观察脱敏状态和请求类型。

### 人工 Telegram 验收

1. 仓库所有者在手机 UI 输入手机号。
2. 仓库所有者在手机 UI 输入 Telegram 验证码。
3. 如账号启用两步验证，由仓库所有者在手机 UI 输入密码。
4. Codex 不代填、不请求、不读取以上秘密。
5. 观察 `Authorized` 后杀死应用进程并重新启动，验证仍保持登录。
6. 点击退出账号，等待 TDLib 完成关闭，再验证返回登录页。

任何未亲自观察到的人工步骤均记录为“尚未验证”。

## 16. Proof

阶段内按最小切片先运行定向测试；最终必须新鲜执行：

```powershell
E:\AndroidStudio2.0\platform-tools\adb.exe devices -l
E:\AndroidStudio2.0\platform-tools\adb.exe shell getprop ro.product.cpu.abi
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat installDebug
```

另执行：

- TDLib/OpenSSL 源码和产物 SHA-256 校验。
- APK ABI 内容检查。
- merged manifest 权限和备份检查。
- 源码、资源、日志调用和 Git 状态的敏感数据扫描。
- 过滤后的真机 Auth logcat 观察。
- 人工登录、重启恢复和退出验收。

若任一 Proof 失败，停止扩展功能，定位首个根因后只修复阶段 2 范围内问题。

## 17. 预计文件变更

### 根构建与文档

- 修改 `settings.gradle.kts`。
- 修改 `build.gradle.kts`。
- 修改 `gradle/libs.versions.toml`。
- 修改 `.gitignore`。
- 修改 `README.md`。
- 修改 `docs/ARCHITECTURE.md`。
- 修改 `docs/DEVELOPMENT_PLAN.md`。
- 修改 `docs/ACCEPTANCE_TESTS.md`。
- 新建 `docs/TDLIB_BUILD.md`。
- 新建 `tools/tdlib/build-android.ps1`。
- 新建 `tools/tdlib/build-android-arm64.sh`。

### `core:model`

- 新建 `core/model/build.gradle.kts`。
- 新建 `core/model/src/main/java/com/qixuan/channelvideoflow/model/auth/TelegramAuthState.kt`。
- 新建 `core/model/src/main/java/com/qixuan/channelvideoflow/model/auth/TelegramAuthFailure.kt`。

### `core:domain`

- 新建 `core/domain/build.gradle.kts`。
- 新建 `core/domain/src/main/java/com/qixuan/channelvideoflow/domain/auth/TelegramAuthRepository.kt`。

### `telegram:tdlib`

- 新建 `telegram/tdlib/build.gradle.kts`。
- 新建 `telegram/tdlib/src/main/AndroidManifest.xml`。
- 生成 `telegram/tdlib/src/main/java/org/drinkless/tdlib/Client.java`。
- 生成 `telegram/tdlib/src/main/java/org/drinkless/tdlib/TdApi.java`。
- 生成 `telegram/tdlib/src/main/jniLibs/arm64-v8a/libtdjni.so`。
- 新建 `telegram/tdlib/TDLIB_PROVENANCE.md`。
- 复制适用的 TDLib 和 OpenSSL 许可证文本。

### `telegram`

- 新建 `telegram/build.gradle.kts`。
- 新建 `telegram/src/main/AndroidManifest.xml`。
- 新建 `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/client/TelegramClientManager.kt`。
- 新建 `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/client/TelegramClientEvent.kt`。
- 新建 `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/auth/TdLibTelegramAuthRepository.kt`。
- 新建 `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/auth/TdLibAuthorizationStateMapper.kt`。
- 新建 `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/auth/TdLibAuthErrorMapper.kt`。
- 新建 `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/config/TelegramCredentials.kt`。
- 新建 `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/storage/TdLibDirectories.kt`。
- 新建 `telegram/src/main/java/com/qixuan/channelvideoflow/telegram/di/TelegramModule.kt`。
- 新建对应 `telegram/src/test` 授权状态、错误和 Repository 测试。

### `app`

- 修改 `app/build.gradle.kts`。
- 修改 `app/src/main/java/com/qixuan/channelvideoflow/config/BuildConfigTelegramCredentialStatusProvider.kt`。
- 修改 `app/src/main/java/com/qixuan/channelvideoflow/di/ConfigurationModule.kt`。
- 修改 `app/src/main/java/com/qixuan/channelvideoflow/navigation/ChannelVideoFlowNavHost.kt`。
- 修改 `app/src/main/java/com/qixuan/channelvideoflow/feature/auth/LoginScreen.kt`。
- 修改 `app/src/main/java/com/qixuan/channelvideoflow/feature/auth/LoginUiState.kt`。
- 新建 `app/src/main/java/com/qixuan/channelvideoflow/feature/auth/AuthViewModel.kt`。
- 修改 `app/src/main/res/values/strings.xml`。
- 删除被真实授权路由取代的 `feature/startup` 占位实现及对应过时测试。
- 新建 `app/src/test/.../FakeTelegramAuthRepository.kt`。
- 新建 AuthViewModel 和 UI 状态单元测试。
- 新建 `app/src/androidTest/.../TdLibNativeSmokeTest.kt`。
- 新建登录 Compose 仪器测试。

备份 XML 和 Manifest 预计保持功能不变；只有 merged manifest 证明存在缺口时才做最小修正。

## 18. 主要风险与停止条件

1. **OpenSSL 3.5.7 兼容性：** 固定 TDLib 提交若无法与支持中的 OpenSSL 构建，停止并报告，不使用 EOL 版本掩盖问题。
2. **Windows native 工具链：** MSYS2、Windows CMake、NDK 路径转换可能导致首个 host generator 或 linker 失败；保留完整首因并最小化构建路径。
3. **大型产物：** `TdApi.java` 和 `libtdjni.so` 体积较大；只保留一个 ABI并记录哈希，不生成无关架构。
4. **未提交基线：** 仓库没有可回退提交；修改前后持续保存 `git status` 和精确 diff，不格式化无关文件，不执行 clean/reset。
5. **凭证尚未配置：** 可先完成无秘密的构建和自动化测试，但真实授权在本机凭证有效前保持“尚未验证”。
6. **新增授权状态：** 固定提交包含本阶段不实现的交互步骤；必须安全阻断，不能遗漏分支或误报授权成功。
7. **用户秘密输入：** 真机人工登录必须由仓库所有者完成；Codex 不通过 ADB、聊天、截图、日志或文件读取获取验证码和密码。

## 19. 完成定义

只有以下条件全部满足，阶段 2 才能报告完成：

- 官方 TDLib 固定源码和目标 ABI 产物可复现并有哈希记录。
- 所有授权状态分支均有显式映射和测试。
- JVM test、lint、assembleDebug 新鲜通过。
- 真机 connectedDebugAndroidTest 和 installDebug 有真实成功结果。
- 登录由仓库所有者在手机上完成并实际观察 Authorized。
- 杀进程/重启后实际观察会话恢复。
- 退出后实际观察返回手机号登录页。
- logcat、源码、资源、构建配置和 Git 检查未发现敏感数据泄露。
- 未实现频道、视频、播放、标签、信息流或媒体缓存。

未完成的任何一项必须写“尚未验证”，不得根据 Fake 或构建成功推断真实 Telegram 登录成功。
