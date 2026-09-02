# VELORA（曜流）

> 曜流，让精彩自然流动。
> VELORA — Let Content Flow.

VELORA（曜流）是一个原生 Android Telegram 频道视频浏览器。它使用用户自己的 Telegram 个人账号，通过官方 TDLib 读取用户本来就有权访问的频道，把普通视频消息建立为本地元数据索引，并提供频道、话题标签和播放顺序筛选的竖向视频流。

本项目由 **麒轩（Kirxuan）** 创建。它是非官方 Telegram 客户端，与 Telegram 官方没有隶属、认可或合作关系。

## 项目定位

- 面向个人使用；每位使用者自行申请并保管自己的 Telegram API 参数。
- 仓库公开源代码；正式 release APK 永远不包含维护者或构建者的 Telegram API 参数，首次启动由使用者在设备上自行配置。
- 只访问当前 Telegram 账号已有权限的内容，不扩大账号权限。
- 不提供保存、导出、公共存储写入或内容保护绕过能力。
- 不包含广告、统计、遥测、服务器或 Web 后台。

## 主要能力

- 官方 TDLib 个人账号授权，支持手机号、验证码和两步验证密码流程。
- 发现、搜索和多选当前账号可访问的频道。
- 使用 TDLib 视频过滤搜索建立 Room 本地索引，并支持断点恢复和增量同步。
- 按频道和 hashtag 组合筛选；标签支持 OR/AND 语义。
- 最新优先或随机轮次的竖向短视频信息流。
- 单一 Media3 ExoPlayer，避免每页创建播放器。
- 通过 TDLib `downloadFile(offset, limit)` 和自定义 Media3 DataSource 进行私有分段读取。
- Telegram HLS、混合 ABR 和有界下一条预加载；移动数据默认不预加载。
- 应用内部媒体缓存、可配置容量上限、账号退出清理和受保护内容 `FLAG_SECURE`。
- 中英文品牌名、标语和创造者署名本地化。

## 当前状态

当前应用版本为 **1.1.0**，功能开发记录到 **Stage 24**。Stage 24 增加首次启动 API ID/API Hash 配置、Android Keystore AES-GCM 加密保存、损坏凭证恢复和凭证变更后的 TDLib 安全重建；所有非 debug 构建均强制排除本机 `local.properties` 中的值。

Stage 24 的 1106 项主机测试、lint、debug/release 构建、release BuildConfig 空值、正式签名和 APK 本机凭证反向扫描已经通过。仓库所有者报告当前 1.1.0 在 ARM64 真机安装与正常使用通过；本次正式签名 APK 未由 Codex 连接设备重复安装，真实 Android Keystore instrumentation 与逐项登录证据仍标记为**尚未验证**。仓库所有者确认已取得 Telegram 对本次无 sponsored messages/广告发行的书面例外许可；许可文件不进入仓库。历史验证记录不是对所有设备和网络环境的保证；详见 [Stage 24 实施文档](docs/STAGE24_USER_CONFIGURED_CREDENTIALS.md)与[验收测试矩阵](docs/ACCEPTANCE_TESTS.md)。

## 技术栈与边界

- Kotlin、Jetpack Compose、Material 3
- Coroutines、Flow、Hilt
- Room、DataStore
- AndroidX Media3 ExoPlayer
- Telegram 官方 TDLib 1.8.66
- Gradle Kotlin DSL 与 Version Catalog
- minSdk 26；当前 native 产物只包含 `arm64-v8a`

依赖方向固定为：

```text
Compose UI
    ↓
ViewModel
    ↓
UseCase / Repository 接口
    ↓
Repository 实现
    ↓
TDLib / Room / Media3 适配器
```

UI 不直接使用 TDLib、Room DAO 或创建 ExoPlayer。TDLib 类型不会越过 `telegram` 模块的数据边界；视频字节只位于应用内部缓存，Room 只保存元数据。

## 构建要求

- Android Studio 与 JDK 21（可直接使用 Android Studio 自带 JBR）
- Android SDK 36.1，以及 Gradle 同步时提示的构建工具
- ARM64 Android 设备，Android 8.0（API 26）或更高版本
- 自己在 [my.telegram.org](https://my.telegram.org/) 申请的 Telegram API ID 和 API Hash
- Windows 是当前 TDLib 重建脚本的主要已验证主机；仓库已经包含可追溯的 ARM64 TDLib Java/JNI 产物

## 使用正式免凭证 APK

正式 APK 发布在 GitHub Releases。安装后：

1. 用浏览器登录 [my.telegram.org](https://my.telegram.org/)，进入 **API development tools**，申请自己的 API ID 和 API Hash。
2. 首次启动 VELORA，在应用界面填写这两个值。
3. 应用完成本机加密保存后，继续按界面输入手机号、验证码和两步验证密码。

API 参数使用 Android Keystore 的设备密钥进行 AES-GCM 加密，并保存在不可备份的应用私有目录。验证码和两步验证密码不会持久化。

## 从源代码构建

### 1. 克隆仓库

```powershell
git clone https://github.com/Kirxuan/TG-Video-Feed-Client.git
cd TG-Video-Feed-Client
```

### 2. 配置 Android SDK

用 Android Studio 打开仓库并完成 Gradle Sync。Android Studio 通常会在仓库根目录生成 `local.properties` 并写入本机 `sdk.dir`。

### 3. 可选：配置开发用 Telegram 凭证

在根目录 `local.properties` 中加入：

```properties
TELEGRAM_API_ID=<your_api_id>
TELEGRAM_API_HASH=<your_api_hash>
```

不要把真实值粘贴到 Issue、Pull Request、日志或聊天中。`local.properties` 已被 Git 忽略；可提交的 [secrets.defaults.properties](secrets.defaults.properties) 只有空白默认值。

这一步只为本机 debug 开发构建提供便捷回退。`release`、`instrumentation` 及其他非 debug 构建会强制把 BuildConfig 凭证置空，即使 `local.properties` 中有值也不会注入；它们统一使用首次启动的设备端配置流程。

### 4. 构建与测试

Windows PowerShell：

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat lint --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

macOS/Linux：

```bash
./gradlew test --no-daemon --console=plain
./gradlew lint --no-daemon --console=plain
./gradlew assembleDebug --no-daemon --console=plain
```

Debug APK 位于 `app/build/outputs/apk/debug/`。连接已授权的 ARM64 Android 设备后可运行：

```powershell
adb devices
.\gradlew.bat installDebug
```

不要在自动化测试中使用真实 Telegram 账号、验证码、密码或 API Hash。

## 隐私与安全

- Manifest 有效权限仅为 `INTERNET` 和 `ACCESS_NETWORK_STATE`。
- Android 备份和设备迁移备份均被禁用/排除。
- TDLib 会话、数据库、Room、DataStore 和媒体缓存都保留在应用私有目录。
- 使用者填写的 API ID/API Hash 由 Android Keystore 的 AES-GCM 密钥加密，密文位于 `noBackupFilesDir`，不进入 Room/DataStore/SharedPreferences。
- 视频不写入 MediaStore、Downloads、DCIM 或 Movies。
- 受保护内容不提供保存/分享入口，播放时按策略启用 `FLAG_SECURE`。
- Release 关闭详细调试日志，项目不包含分析或遥测 SDK。

发现安全问题时请先阅读[安全设计与报告方式](docs/SECURITY.md)，不要在公开 Issue 中附带凭证、手机号、验证码、密码、会话文件、私有路径或 Telegram 内容。

## 文档

- [产品规格](docs/PRODUCT_SPEC.md)
- [架构设计](docs/ARCHITECTURE.md)
- [安全设计](docs/SECURITY.md)
- [验收测试](docs/ACCEPTANCE_TESTS.md)
- [开发计划](docs/DEVELOPMENT_PLAN.md)
- [TDLib 可复现构建](docs/TDLIB_BUILD.md)
- [TDLib provenance](telegram/tdlib/TDLIB_PROVENANCE.md)
- [Stage 23 视频索引优化](docs/STAGE23_VIDEO_INDEX_SCAN_OPTIMIZATION.md)
- [Stage 24 用户自行配置凭证](docs/STAGE24_USER_CONFIGURED_CREDENTIALS.md)
- [完整阶段文档目录](docs/)

## 参与贡献

Issue 和 Pull Request 欢迎用于缺陷修复、安全改进、文档完善和符合现有边界的功能建议。开始前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 与 [AGENTS.md](AGENTS.md)。

## 许可证

除明确标注的第三方组件外，本项目使用 [Apache License 2.0](LICENSE)。TDLib、OpenSSL 和其他依赖继续遵循各自许可证；详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 `telegram/tdlib/licenses/`。

## 免责声明

本项目按“现状”提供，不保证适用于所有 Telegram 账号、频道、设备、网络或司法辖区。使用者应自行遵守 Telegram 服务条款、频道规则、版权要求和所在地法律，并自行承担构建、凭证保管和使用风险。
