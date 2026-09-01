# VELORA（曜流）

> 曜流，让精彩自然流动。
> VELORA — Let Content Flow.

VELORA（曜流）是一个原生 Android Telegram 频道视频浏览器。它使用用户自己的 Telegram 个人账号，通过官方 TDLib 读取用户本来就有权访问的频道，把普通视频消息建立为本地元数据索引，并提供频道、话题标签和播放顺序筛选的竖向视频流。

本项目由 **麒轩（Kirxuan）** 创建。它是非官方 Telegram 客户端，与 Telegram 官方没有隶属、认可或合作关系。

## 项目定位

- 面向个人、自行构建和自行保管凭证的使用方式。
- 仓库公开源代码，但不提供包含维护者 Telegram API 凭证的 APK。
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

当前应用版本为 **1.0**，功能开发记录到 **Stage 23**。最近一次完整主机验证记录为 1,081/1,081 tests，Robolectric-Compose 28/28、API 36 AOSP x86_64 emulator Compose UI 99/99，lint、debug/release 构建以及 APK 权限/native/备份静态审计通过。

ARM64/Vivo 实体机、真实账号、真实 CDN 和超长频道上的最终公开版本验证仍标记为**尚未验证**。历史验证记录不是对所有设备和网络环境的保证；详见 [Stage 23 实施文档](docs/STAGE23_VIDEO_INDEX_SCAN_OPTIMIZATION.md)与[验收测试矩阵](docs/ACCEPTANCE_TESTS.md)。

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

## 快速开始

### 1. 克隆仓库

```powershell
git clone https://github.com/Kirxuan/TG-Video-Feed-Client.git
cd TG-Video-Feed-Client
```

### 2. 配置 Android SDK

用 Android Studio 打开仓库并完成 Gradle Sync。Android Studio 通常会在仓库根目录生成 `local.properties` 并写入本机 `sdk.dir`。

### 3. 配置自己的 Telegram 凭证

在根目录 `local.properties` 中加入：

```properties
TELEGRAM_API_ID=<your_api_id>
TELEGRAM_API_HASH=<your_api_hash>
```

不要把真实值粘贴到 Issue、Pull Request、日志或聊天中。`local.properties` 已被 Git 忽略；可提交的 [secrets.defaults.properties](secrets.defaults.properties) 只有空白默认值。

> Telegram API 参数会进入你自己构建的 APK，因此不要公开上传包含个人凭证的 APK。每位使用者都应使用自己的参数自行构建。

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
- [完整阶段文档目录](docs/)

## 参与贡献

Issue 和 Pull Request 欢迎用于缺陷修复、安全改进、文档完善和符合现有边界的功能建议。开始前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 与 [AGENTS.md](AGENTS.md)。

## 许可证

除明确标注的第三方组件外，本项目使用 [Apache License 2.0](LICENSE)。TDLib、OpenSSL 和其他依赖继续遵循各自许可证；详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 `telegram/tdlib/licenses/`。

## 免责声明

本项目按“现状”提供，不保证适用于所有 Telegram 账号、频道、设备、网络或司法辖区。使用者应自行遵守 Telegram 服务条款、频道规则、版权要求和所在地法律，并自行承担构建、凭证保管和使用风险。
