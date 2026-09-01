# Stage 20：VELORA（曜流）品牌更新

日期：2026-08-28
状态：主机与 API 36 AOSP x86_64 emulator Proof 已通过；ARM64/Vivo 真机尚未验证

## Outcome

Android 启动器显示国际品牌名“VELORA”并使用仓库所有者提供的新图标；授权页显示 `VELORA` 与中文品牌标语，产品文档同时记录中英文名称与标语。

## Scope

- 更新应用标签、品牌名称与中文界面标语，并在产品文档记录对应英文标语。
- 将 Manifest 启动器图标切换为标准 adaptive/round mipmap 资源。
- 更新授权页品牌区、定向 Compose 测试和共享组件预览。
- 更新当前有效的仓库、产品、架构、安全、验收与 TDLib 文档品牌名。

## Boundary

- 保留 `com.qixuan.channelvideoflow` applicationId/namespace、数据库名、Application、Theme、Kotlin 类型和测试包名，避免数据迁移与无收益的大范围重命名。
- 不修改 Telegram、Room、Media3、缓存、权限、备份、日志、依赖或业务流程。
- 不重绘仓库所有者提供的图标，只生成 Android 所需的密度与 adaptive-icon 包装资源。

## Failure states

- 图标源文件无法解码或缩放时，不替换现有可构建资源。
- Android 资源或 Manifest 打包失败时，停止扩展并定位首个资源错误。
- 授权页品牌文案导致 Compose 回归时，先修复定向测试再执行全量 Proof。

## Proof

### 资源与 Compose

- `:app:processDebugResources`：通过。
- `:app:compileInstrumentationKotlin`：通过。
- Login/Channel/ComposeSmoke Robolectric-Compose 定向组：通过。
- CacheSettings Robolectric-Compose 定向组：通过。
- `run-emulator-compose-tests.ps1 -Serial emulator-5554`：API 36 AOSP x86_64，96/96 通过；instrumentation target APK 的 native entry 为 0。
- 最终 instrumentation 冷启动：`MainActivity` resumed/top，品牌页显示 `VELORA` 与“曜流，让精彩自然流动”，无目标包崩溃。

### 主机与 APK

- `gradlew.bat test`：1050/1050 通过，failure/error/skipped 均为 0。
- `gradlew.bat lint`：最终通过。首次运行因部分 `values-en` 造成 145 个 `MissingTranslation` 失败；根因修复为移除不完整英文资源并将启动器统一为国际品牌名 `VELORA`，未增加 lint baseline 或 suppression。
- `gradlew.bat assembleDebug`：通过；debug APK SHA-256 为 `0861C2A2EE01DC8D4B829AFA1B64D3F94B921731EE4C682D233AA2BEA72CC3FF`。
- `aapt dump badging`：application label 为 `VELORA`，各密度图标均解析到 `res/mipmap-anydpi-v26/ic_launcher.xml`。
- 新图标原图 SHA-256 为 `4F1D11F765D1DB5AA92C13035C9B3224B60323077D84185B1FC2FBD0DB322AD1`；已生成 48/72/96/144/192 px legacy/round 与 108/162/216/324/432 px adaptive foreground。

### 安全与边界

- merged manifest 仍只有 `INTERNET`、`ACCESS_NETWORK_STATE`，`allowBackup=false`、`usesCleartextTraffic=false` 保持。
- debug APK 仍保留唯一 ARM64 TDLib 生产链路；instrumentation APK 仍排除全部 `.so`。
- 未新增依赖、权限、日志、存储、缓存、数据库、TDLib 或 Media3 行为；未读取或写入真实 Telegram 凭证。

### 尚未验证

- 没有连接 ARM64/Vivo 实体机，因此生产 debug APK 的 install+launch、Vivo 启动器最终遮罩效果和真机桌面图标显示均为“尚未验证”。
- x86_64 emulator 安装生产 APK 得到预期的 `INSTALL_FAILED_NO_MATCHING_ABIS`；按仓库规则未在该 emulator 运行 TDLib native smoke，改用无 `.so` instrumentation APK 完成 Compose UI 与品牌页 cold-launch proof。
