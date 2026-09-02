# Stage 24：用户自行配置 Telegram API 参数

日期：2026-09-02

状态：实现、主机 Proof 与正式签名静态 Proof 完成；仓库所有者报告当前版本 ARM64 真机安装和正常使用通过，并确认已取得 Telegram 对本次无 sponsored messages/广告发行的书面例外许可

## 1. Outcome

同一份正式 release APK 不包含维护者或构建者的 Telegram API ID/API Hash。下载者首次启动后在应用内填写自己的参数，安全保存成功后进入既有官方 TDLib 手机号授权流程；格式错误、静态存储故障或 TDLib 拒绝参数时均可在配置页修正。

## 2. Scope

- 非 debug 构建凭证隔离与版本升级到 1.1.0。
- API ID/API Hash 输入、校验、密码遮罩、提交和固定错误状态。
- Android Keystore AES-GCM 加密、noBackupFilesDir 密文文件、原子写入和损坏恢复。
- `TelegramAuthRepository.configureCredentials`、Hilt 绑定和 Fake 测试接缝。
- 凭证改变后的 TDLib `Close → AuthorizationStateClosed → 新 generation` 单客户端重建。
- 定向 JVM/Robolectric/Compose 测试、完整主机构建和 APK 敏感值静态扫描。

## 3. Boundary

- 不修改手机号、验证码、两步验证密码的 TDLib 状态来源或持久化规则。
- 不修改频道、视频索引、Room、Media3、播放器、缓存、预加载和内容保护。
- 不新增权限、网络服务、遥测、服务器、SharedPreferences、DataStore 或 Room 凭证表。
- 不在本阶段生成长期 release 签名密钥或上传 GitHub Release APK。
- Telegram 公共第三方客户端 sponsored messages 合规能力不在本阶段实现；完成独立评审前不把本阶段主机构建等同于可公开发布。

## 4. Failure states

| 状态 | 行为 |
|---|---|
| API ID/API Hash 为空或格式错误 | 不写文件；只显示固定格式说明，不回显 Hash |
| Keystore 或密文不可读 | 失败关闭，显示固定存储错误；显式重新输入时重建密钥与密文 |
| 原子写入失败 | 保留不可用状态，不把内存值写入日志或其他存储 |
| TDLib 拒绝格式正确的参数 | 返回配置页；允许覆盖设备端参数 |
| 旧 TDLib Client 仍存在 | 发送 Close，等待 Closed 后再创建新 generation；不并行使用两个 Client |
| native/数据库/网络错误 | 保留既有脱敏错误映射和重试边界 |

## 5. Security design

构建来源分层：

1. debug 在设备端密文不存在时，可回退被 Git 忽略的 `local.properties`，兼容维护者开发流程。
2. release、instrumentation 和未来任何非 debug 构建的 `TELEGRAM_API_ID`/`TELEGRAM_API_HASH` BuildConfig 字段强制为空。
3. 设备端配置优先于 debug 回退值，因此开发者也能复现公开路径。

静态存储：

- KeyStore alias：`com.qixuan.channelvideoflow.telegram.credentials.v1`。
- 算法：AES-256/GCM/NoPadding，随机 12-byte IV，128-bit tag。
- AAD：固定版本化应用语义，不包含用户数据。
- 文件：`noBackupFilesDir/credentials/telegram-api.v1`，最大读取 4KiB。
- 写入：同目录临时文件、fsync、原子替换；异常路径清理临时文件。
- 明文：定长版本结构；加解密使用的 ByteArray 在完成后覆盖。Kotlin String 和 TDLib 参数对象仍会在必要的进程内生命周期存在，不能承诺抵御 root/恶意调试设备。

## 6. Dependency flow

```text
LoginScreen
  → AuthViewModel
    → TelegramAuthRepository.configureCredentials
      → TelegramCredentialsStore
        → Android Keystore + noBackupFilesDir
      → TelegramAuthClient.restartAfterCredentialsChanged
        → old Client Close/Closed
        → TelegramCredentialsProvider.get
        → new official TDLib Client
```

UI 不引用 Android Keystore、文件或 TDLib 类型；Repository 不向 UI 暴露原始 TDLib 错误。

## 7. Proof

定向 Proof：

```powershell
.\gradlew.bat :telegram:testDebugUnitTest --tests "*SecureTelegramCredentialsProviderTest" --tests "*TelegramClientManagerTest" --tests "*TdLibTelegramAuthRepositoryTest" :app:testDebugUnitTest --tests "*TelegramCredentialsProviderTest" --tests "*AuthViewModelTest" :app:testInstrumentationUnitTest --tests "com.qixuan.channelvideoflow.feature.auth.LoginScreenTest" --no-daemon --console=plain
```

完整主机 Proof：

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat lint --no-daemon --console=plain
.\gradlew.bat assembleDebug :app:assembleRelease --no-daemon --console=plain
```

静态 Proof：

- release BuildConfig 字段为空。
- release APK 解包扫描不含本机 API ID/API Hash。
- merged manifest 只含 INTERNET 与 ACCESS_NETWORK_STATE，备份保持禁用。
- `local.properties` 继续被 Git 忽略且不在 index。
- 源码、资源、diff 和日志调用不含真实凭证。

设备 Proof（有授权 ARM64 设备时）：

```powershell
adb devices -l
.\gradlew.bat :telegram:connectedDebugAndroidTest
.\gradlew.bat installRelease
```

由仓库所有者亲自在手机 UI 完成首次参数配置、错误参数修正、手机号/验证码/两步验证、重启恢复和退出。Codex 不读取、代填或请求这些真实值。

## 8. Current evidence

- 定向凭证、Repository、Client、ViewModel 与 Login Compose 测试：通过。
- 完整 `test`：1106/1106 通过，failures=0、errors=0、skipped=0。
- 完整 `lint`、`assembleDebug` 与 `:app:assembleRelease`：退出码 0。
- `:telegram:compileDebugAndroidTestKotlin` 与 `:app:compileInstrumentationKotlin`：退出码 0。
- release BuildConfig 两项凭证字段为空；unsigned release APK 与所有 Git 跟踪文件对本机真实 API ID/API Hash 反向扫描均为零命中。
- release merged manifest 只有 `INTERNET`、`ACCESS_NETWORK_STATE`，`allowBackup=false` 且两套备份规则均存在；native ABI 只有 `arm64-v8a`。
- 正式签名 release APK 为 40,738,734 bytes，SHA-256 `ACFD472C3EAC18E63C1B746B2ABD0602D3C7D3CC3BFA611A3CE75A27B6723061`；`zipalign` 与 APK Signature Scheme v2/v3 验证通过。
- 发布证书为 RSA-4096 / SHA256withRSA，证书 SHA-256 指纹为 `1A:A9:57:CA:E4:A6:9A:5B:A2:E7:53:AB:C4:02:E4:73:FA:BD:A9:D4:0A:A5:2C:E1:0F:21:16:65:43:85:9E:7A`；密钥和口令位于仓库外，仅当前 Windows 用户可读。
- 正式签名 APK、所有 Git 跟踪文件和工作区 diff 对本机真实 API ID/API Hash 反向扫描均为零命中。
- 仓库所有者报告当前 1.1.0 在 ARM64 真机安装与正常使用通过；本次正式签名 APK 未由 Codex 连接设备重复安装。
- Android Keystore instrumentation 与真实账号逐项清单：尚未验证。
- `adb devices -l` 没有列出设备，Codex 本次未执行设备安装或 instrumentation。
- 仓库所有者确认已取得 Telegram 对本次无 sponsored messages/广告发行的书面例外许可；许可文件和账户信息不进入仓库，Codex 未独立审阅许可原文。

## 9. Next stage gate

Stage 24 主机 Proof、安全扫描和正式签名静态 Proof 已全部通过。仓库所有者明确批准发布 1.1.0，并以其确认的 Telegram 书面例外许可解除本次 sponsored messages 门槛。发布资产只允许使用上述 SHA-256 对应的正式签名 APK；不得上传 unsigned/aligned-unsigned 产物、签名密钥、口令文件、`local.properties` 或许可文件。
