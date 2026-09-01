# Stage 22：创造者名称本地化

日期：2026-08-28
状态：主机与 API 36 AOSP x86_64 emulator Proof 已通过；ARM64/Vivo 真机尚未验证

## Outcome

- 中文 locale 的登录页品牌区显示“创造者：麒轩”。
- 英文 locale 与默认回退显示 `Created by Kirxuan`。
- 署名加入后，手机号、验证码和密码等授权状态仍保持可见、可滚动且不被裁切。

## Scope

- `app/src/main/res/values*/branding.xml` 的创造者名称与署名模板。
- 登录页品牌区和稳定测试标签。
- `LoginScreenTest` 的 `en-US`、`zh-CN`、`zh-HK` 回归断言。
- 当前状态、产品、开发、架构、安全与验收文档。

## Boundary

- 不改变应用名、标语、图标、包名、`versionCode` 或 `versionName=1.0`。
- 不新增 About 页面，不修改 TDLib、Room、Media3、缓存、权限、备份、日志、依赖或业务流程。
- 不把包名中的历史内部标识视为用户可见创造者名称并重命名。

## Failure states

- 中文 locale 显示英文署名，或英文 locale 显示中文署名。
- Android 的简繁体中文回退遗漏创造者名称。
- 署名增加高度后把授权卡片内容挤出默认视口。
- 非品牌代码或安全边界发生无关变化。

## Proof

### 实际完成

- 默认和英文资源增加 `creator_name=Kirxuan` 与 `creator_credit=Created by %1$s`。
- 中文通用及 CN/HK/MO/SG/TW 回退链增加 `creator_name=麒轩` 与 `creator_credit=创造者：%1$s`。
- 登录页品牌区以 `stringResource` 组合名称和模板，并增加 `login-creator` 测试标签。
- 品牌区内部间距由 7dp 调整为 4dp，主区块间距由 22dp 调整为 18dp；Stage 21 的 `headlineSmall` 品牌名保持不变。

### 失败与修复记录

- 首次定向测试为 16 项中的 1 项失败：新增署名高度使验证码标题在 Robolectric 默认视口中不再满足 `assertIsDisplayed`。
- 根因属于 Compose 布局回归，不是资源、TDLib 或测试环境失败。
- 收紧上述两处纵向间距后，同一条定向测试重新通过 16/16；没有删除或弱化既有可见性断言。

### 验证结果

- `:app:processDebugResources`：通过。
- `:app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.auth.LoginScreenTest"`：修复后通过 16/16。
- Compose Path B 主机三条命令：全部通过。
- `scripts/run-emulator-compose-tests.ps1 -Serial emulator-5554`：API 36 AOSP x86_64，98/98 通过，目标包无 crash/ANR。
- 英文与 `zh-HK` 平台 locale override 冷启动视觉/UI 树：署名正确且互斥，无可见裁切；截图位于 `build/reports/stage22-creator/`。
- `gradlew.bat test`：1052/1052 通过，0 failure/error/skipped。
- `gradlew.bat lint`：通过。
- `gradlew.bat assembleDebug`：通过；`versionName` 仍为 `1.0`。
- 最终 debug APK SHA-256：`0980B2D2EADBE168D686165CF8758CA2E6E6376B695309B7FAB38F1AF955AFD4`。
- `local.properties` 继续被 Git 忽略，源码凭证赋值扫描为 0。

## 尚未验证

- 本次没有连接 ARM64/Vivo 实体机，因此真机安装、冷启动、系统全局中英文切换及 OriginOS 显示均为“尚未验证”。
- 真实 Telegram 账号、频道与媒体未在本阶段重复验证。
