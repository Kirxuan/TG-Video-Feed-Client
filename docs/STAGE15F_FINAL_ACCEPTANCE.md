# 阶段 15F：最终性能、视觉与安全验收

日期：2026-08-22（Asia/Hong_Kong）
状态：通过（真机除外）

## 阶段合同

### Outcome

在不操作实体手机的前提下，以主机测试、Robolectric、API 36 x86_64 emulator、人工截图复核、lint、
APK 内容与安全静态审计，证明阶段 15A–15F 的可交付 Debug 构建。

### Scope

阶段 15 的生产代码、测试、脚本、审计文档、18 张视觉证据和最终 Debug APK。

### Boundary

- 不连接、安装、启动、截图或测试任何实体手机。
- 不执行真实 Telegram 登录、真实视频 benchmark 或 emulator TDLib native smoke。
- 不 commit、不 push，不读取或输出 `local.properties` 值。

### Failure states

任何 Proof 失败均停止扩展并修复首个根因。实际 emulator 全套首次因测试固定 1000px 坐标失败 1 例；
修正为百分比坐标后目标测试与 90 例全套均通过。

### Proof

| Proof | 结果 |
|---|---|
| `:player:testDebugUnitTest --rerun-tasks` | PASS；30 tasks |
| `:telegram:testDebugUnitTest --rerun-tasks` | PASS；56 tasks |
| `:app:testDebugUnitTest --rerun-tasks` | PASS；128 tasks |
| `:app:compileInstrumentationKotlin` | PASS |
| `:app:testInstrumentationUnitTest --rerun-tasks` | PASS；128 tasks |
| `test --rerun-tasks` | PASS；345 tasks |
| `lint --rerun-tasks` | PASS；220 tasks |
| `assembleDebug --rerun-tasks` | PASS；190 tasks |
| API 36 x86_64 emulator Compose UI | PASS；90/90 |
| Stage 15 visual QA | PASS；4 visual tests / 18 PNG |

## APK 与清单审计

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：44,649,829 bytes。
- SHA-256：`2328C85FF96320A82D734A56D119DCA152C2043D8B0C5157B76A1F04F9FFD2B7`。
- 生产 APK native：arm64-v8a 下 `libtdjni.so`、`libandroidx.graphics.path.so`、
  `libdatastore_shared_counter.so`。
- instrumentation target native：0；未在 emulator 执行 TDLib native。
- `aapt2 dump permissions`：仅 INTERNET 与 ACCESS_NETWORK_STATE。

## 安全验收

- `android:allowBackup=false`；`dataExtractionRules` 与 `fullBackupContent` 对云备份和设备迁移的 root、file、
  database、sharedpref 及 device_* 域全部排除。
- 没有公共媒体目录、MediaStore、广泛存储权限、第二份 Media3 完整缓存或生产 Fake/Demo route。
- 凭证仍只从被忽略的 `local.properties` 读取；本阶段未读取真实值，测试未使用真实账号/验证码/密码。
- Debug 日志只保留状态、请求类型、键、范围、缓存/播放器统计和脱敏错误码；Release 由 BuildConfig gate 关闭详细日志。

## 视觉证据

目录：`build/reports/stage15-visuals/`。18 张截图均由 test-only visual fixture 渲染生产 composable，
已逐张人工检查；详情图直接捕获可见的详情内容节点，避免系统 Surface/Popup 合成落后一帧。

## 已知限制

- Android 编译器提示部分旧的 memory/storage pressure 常量已弃用；兼容分支仍通过 test/lint，不影响本阶段。
- 本地 SDK command-line tools 解析到较新的 SDK XML version 4 时有版本提示；构建、lint 与测试均成功。
- TDLib/部分依赖 `.so` 无法由当前工具链剥离符号，按原样打包。
- 真实 Telegram 网络、arm64 TDLib native、真实滑动首帧指标和 iQOO 12 视觉表现：尚未验证。
- 仓库当前 `main` 分支几乎全部文件仍为用户未跟踪内容；未自动提交。
