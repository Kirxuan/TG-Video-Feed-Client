# 阶段 4 Compose Path B 交接

日期：2026-07-27
状态：Path B 四项 Proof 全部通过。

## 1. Outcome

把阶段 4 的 Compose 门槛从“必须在 iQOO 12 的 OriginOS 上完成完整 instrumentation”改成可判定的等价组合：

    Proof(Compose) = 编译通过 ∧ Robolectric-Compose 通过 ∧ emulator-Compose-UI 通过 ∧ Vivo 真机 install+launch smoke 通过

这证明共享 Compose 断言可在标准 AOSP x86_64 测试环境执行，同时保留 ARM64 Vivo 真实 APK 的安装与 Activity 冷启动安全检查。

## 2. Scope

- `app/build.gradle.kts`：`instrumentation` build type、共享 Compose 源集、Robolectric host resources，以及仅针对 instrumentation target APK 的 `.so` 排除。
- `app/src/sharedTest/java`：Login、ChannelSelection、Compose smoke 由 Robolectric 和 instrumentation 共用。
- `scripts/run-emulator-compose-tests.ps1`：只执行共享 Compose UI instrumentation；强制 API 36/x86_64，并拒绝 ARM64 AVD。
- `scripts/vivo-test-prep.sh` 与 `scripts/run-vivo-launch-smoke.ps1`：为 Vivo 安装/启动 smoke 提供准备和可保存的诊断证据。

## 3. Boundary

`Vivo/OriginOS 6 + Android 16 对 adb 安装包的后台/自启动管控属于设备环境限制，非代码缺陷；当且仅当步骤 2/3 已执行仍不可达时，真机完整 instrumentation 不计入 Failure，改由步骤 4 的等价组合证明。`

已知 Vivo 历史日志是 `am_app_frozen`（`fast_freezer`）和 `am_kill`（`single-cleaner`），instrumentation 对外只报告 `shortMsg=Process crashed`。当时 Activity 为 TOP、oom adj 0、屏幕亮、设备充电且 device idle 未触发；不得把它重判成 Compose、Room、SQL、Gradle、TDLib 或业务测试断言失败。

完整 Vivo `am instrument` 现在只可用于记录 OriginOS 系统行为，不能再作为 Compose 门槛；不要在没有改变设备策略时重复运行它。

## 4. Failure states

| 场景 | 结果定义 |
|---|---|
| Host 编译或 Robolectric suite 失败 | 产品范围失败：定位第一个测试 harness 或构建根因；不删测试、不改业务逻辑绕过。 |
| instrumentation target APK 含 `.so` | 构建配置失败：x86_64 UI runner 不能继续。 |
| ARM64 AVD / 非 API 36 emulator | 环境未满足：runner 直接拒绝，不能把结果算作 UI Proof。 |
| x86_64 AOSP emulator UI test 失败 | 产品范围失败：保存 instrumentation log，定位第一条断言或 runner 错误。 |
| Vivo debug APK 不能安装、启动、resumed/top 或出现目标包 crash | 产品范围失败：保存 install/start/activity/logcat 证据，定位首个根因。 |
| Vivo 完整 instrumentation 被 fast_freezer/single-cleaner 杀死 | OriginOS 环境限制；只在 Path B 其余项已执行且仍不可达时，不计入 Failure。 |

## 5. 执行命令与记录

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :app:compileInstrumentationKotlin --no-daemon --console=plain
.\gradlew.bat :app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.auth.LoginScreenTest" --tests "com.qixuan.channelvideoflow.feature.channels.ChannelSelectionScreenTest" --tests "com.qixuan.channelvideoflow.test.ComposeSmokeTest" --no-daemon --console=plain
.\gradlew.bat :app:assembleInstrumentation :app:assembleInstrumentationAndroidTest --no-daemon --console=plain
& "$env:JAVA_HOME\bin\jar.exe" tf app\build\outputs\apk\instrumentation\app-instrumentation.apk | Select-String '^lib/'
```

预期最后一条无输出。生产 debug APK 仍保留 ARM64 TDLib native `.so`；不得为让 emulator 通过而修改生产 ABI 或删除生产 native 库。

```powershell
# SDK Manager：安装 system-images;android-36;default;x86_64，创建并启动 CVF_AOSP_API36_X86_64
.\scripts\run-emulator-compose-tests.ps1 -Serial <x86_64-emulator-serial>
.\scripts\run-vivo-launch-smoke.ps1 -Serial <Vivo-serial>
```

emulator runner 报告保存在 `build/reports/emulator-compose/`；Vivo smoke 报告保存在 `build/reports/vivo-launch-smoke/`。日志只保存目标包、启动状态和 crash 证据；不要将真实账号、消息正文、验证码、密码、session 或 TDLib 完整对象写入报告。

## 6. 当前环境结论

- Host 编译：已通过（` :app:compileInstrumentationKotlin`，退出码 0）。
- Host Robolectric Compose：已通过（指定共享三套 suite，退出码 0）。
- instrumentation APK：已通过构建；JBR `jar.exe` 检查 target APK 的 `lib/` 无输出。
- API 36 x86_64 emulator Compose UI：通过。已安装官方 `system-images;android-36;default;x86_64`，创建 `CVF_AOSP_API36_X86_64`；`emulator-5554` 报告 `abi=x86_64`、`sdk=36`，共享 Login、ChannelSelection、Smoke instrumentation 为 18/18 通过，耗时 13.427 秒。证据在 `build/reports/emulator-compose/`。
- Vivo install + launch smoke：通过。V2307A、Android 16/API 36、arm64-v8a 上执行 `run-vivo-launch-smoke.ps1 -Serial <device-serial> -SkipBuild` 成功；debug/instrumentation APK 安装成功，`MainActivity` resumed/top，目标包 bounded logcat 与 crash buffer 未发现 Java/native crash。证据在 `build/reports/vivo-launch-smoke/`。

结论：

    Compose门槛 = 通过(路径B)
