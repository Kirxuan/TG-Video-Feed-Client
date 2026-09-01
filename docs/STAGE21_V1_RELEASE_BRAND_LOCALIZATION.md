# Stage 21：1.0 品牌本地化与发布收口

日期：2026-08-28
状态：主机与 API 36 AOSP x86_64 emulator Proof 已通过；ARM64/Vivo 真机尚未验证

## Outcome

- 中文系统中，启动器与登录页显示品牌名“曜流”和标语“曜流，让精彩自然流动”。
- 英文系统中，启动器与登录页显示品牌名 `VELORA` 和标语 `VELORA — Let Content Flow.`。
- 登录页品牌名使用更大的排版层级。
- Android `versionName` 更新为 `1.0`。

## Scope

- `app/src/main/res/values*/branding.xml` 与既有中文 UI 字符串的可翻译性声明。
- 登录页品牌排版及中英文 Compose 回归测试。
- `app/build.gradle.kts` 版本名。
- 当前状态与本阶段验证文档。

## Boundary

- 1.0 只本地化品牌名称与标语；既有功能 UI 继续使用中文。
- 保留 applicationId/namespace、数据库、Application、Theme、Kotlin 类型和内部文件路径。
- 不修改 TDLib、Room、Media3、缓存、权限、备份、日志、依赖或业务流程。

## Failure states

- 不完整翻译导致资源或 lint 失败。
- 中文限定符未覆盖中文系统，或英文系统错误回退中文品牌。
- 放大后的品牌名在标准屏幕或大字体下溢出。
- APK manifest 的 label/version 与资源或 Gradle 配置不一致。

## Proof

### 实际完成

- 默认与英文资源显示 `VELORA` / `VELORA — Let Content Flow.`。
- 中文通用资源与 CN/HK/MO/SG/TW 地区资源显示“曜流”/“曜流，让精彩自然流动”，覆盖 Android 对简繁中文的特殊 locale 回退。
- 登录页品牌名由 `labelLarge` 提升为 `headlineSmall`，并增加稳定测试标签；共享 Compose 测试断言品牌名高度至少 28dp。
- 既有 144 个功能 UI 字符串显式声明为 1.0 不翻译，避免把局部品牌双语误报为整套 UI 翻译缺失。
- `versionName` 从 `0.1.0` 更新为 `1.0`；`versionCode` 保持首次版本的 `1`。

### 修改文件

- `app/build.gradle.kts`
- `app/src/main/java/com/qixuan/channelvideoflow/feature/auth/LoginScreen.kt`
- `app/src/sharedTest/java/com/qixuan/channelvideoflow/feature/auth/LoginScreenTest.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/branding.xml`
- `app/src/main/res/values-en/branding.xml`
- `app/src/main/res/values-zh/branding.xml`
- `app/src/main/res/values-zh-rHK/branding.xml`
- `app/src/main/res/values-zh-rMO/branding.xml`
- `app/src/main/res/values-zh-rSG/branding.xml`
- `app/src/main/res/values-zh-rTW/branding.xml`
- `AGENTS.md`、`README.md` 与当前产品/开发/架构/安全/验收文档

### 验证结果

- `:app:processDebugResources`：通过。
- `:app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.auth.LoginScreenTest"`：通过；覆盖 `en-US`、`zh-CN`、`zh-HK` 名称、标语互斥与放大后的最小高度。
- Compose Path B 主机三条命令：全部通过。
- `scripts/run-emulator-compose-tests.ps1 -Serial emulator-5554`：API 36 AOSP x86_64，98/98 通过，目标包无 crash/ANR。
- 英文与 `zh-HK` 平台 locale override 冷启动视觉/UI 树：名称与标语均正确且互斥，品牌名无裁切；截图位于 `build/reports/stage21-v1/`。
- `gradlew.bat test`：1052/1052 通过，0 failure/error/skipped。
- `gradlew.bat lint`：通过；没有使用 lint baseline 或翻译规则抑制。
- `gradlew.bat assembleDebug`：通过。
- `aapt dump badging`：`versionCode=1`、`versionName=1.0`；默认/英文 label 为 `VELORA`，`zh`/`zh-CN`/`zh-HK`/`zh-TW` label 为“曜流”。
- APK 权限：仅 `INTERNET`、`ACCESS_NETWORK_STATE`；`allowBackup=false`、`usesCleartextTraffic=false`。
- 生产 APK native：仅 `arm64-v8a` 白名单三个 `.so`；instrumentation APK 为 0 个 `.so`。
- 最终 debug APK 使用 Android Debug 证书、APK Signature Scheme v2 签名；SHA-256 为 `A53DE2460382840980BA8414020B326BA8E3696A122EE8714C545B63A4513FE0`。
- `local.properties` 继续被 Git 忽略，源码凭证赋值扫描为 0。

### 尚未验证

- 本次没有连接 ARM64/Vivo 实体机，因此 1.0 debug APK 的真机安装、冷启动、系统设置内的全局中文/英文切换、OriginOS 启动器名称/图标显示均为“尚未验证”。
- 真实 Telegram 账号、真实频道和真实媒体未在本阶段重复验证。
- 当前交付物是可安装的 debug 签名 APK；正式 release keystore 与 release 签名未配置，未擅自创建或保管发布密钥。
