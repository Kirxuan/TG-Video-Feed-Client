# 参与 VELORA（曜流）

感谢你愿意改进 VELORA。这个项目首先是一款个人、自行构建的 Telegram 客户端，因此安全边界、可验证性和小步改动比功能数量更重要。

## 开始之前

1. 阅读 [README.md](README.md)、[AGENTS.md](AGENTS.md) 和任务相关的 `docs/` 文档。
2. 对较大的功能先开 Issue，说明用户可观察结果、影响范围、明确不做的内容、失败状态和验证方式。
3. 一个 Pull Request 尽量只解决一个独立问题，不做无关重构或格式化。

## 不可破坏的边界

- 只使用官方 TDLib 个人账号授权；不使用 Bot API、手写 MTProto 或第三方 Telegram 网关。
- 依赖方向保持 `Compose → ViewModel → UseCase/Repository → 实现 → TDLib/Room/Media3`。
- UI 不直接使用 TDLib、Room DAO 或创建 ExoPlayer。
- 任意时刻只允许一个主要 ExoPlayer 发声；预加载只覆盖当前项和唯一下一项。
- 视频字节只能位于应用内部缓存，不得写入公共存储或绕过内容保护。
- Room 只保存元数据；真实凭证、会话、验证码和密码不得进入源码、测试、日志或 Git 历史。
- 第一版 Manifest 只允许 `INTERNET` 与 `ACCESS_NETWORK_STATE`。新增权限必须单独讨论。
- 依赖必须是稳定版本；预发布依赖需要先说明必要性。

## 本地配置

真实 Telegram API ID/API Hash 只能放在被忽略的根目录 `local.properties`。请勿在 Issue、Pull Request、测试夹具或日志中提交真实值。自动化测试必须使用 Fake 或明显无效的合成值。

## 验证

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

若改动涉及 Compose、TDLib native、Media3、生命周期、存储或真实设备行为，请在 PR 中区分主机测试、emulator 和实体机结果。没有执行的项目必须写“尚未验证”。

## Pull Request 检查清单

- [ ] 变更只覆盖声明的范围。
- [ ] 加载、空状态和适用的错误状态已处理。
- [ ] 新业务逻辑依赖接口且有可重复测试。
- [ ] `test`、`lint`、`assembleDebug` 的实际结果已记录。
- [ ] 没有凭证、手机号、验证码、密码、会话、私有媒体、设备序列号或未脱敏的用户目录。
- [ ] 没有 APK、AAB、签名密钥、数据库、构建目录或测试日志。
- [ ] 第三方源码、素材或二进制的来源、版本和许可证已记录。

除非贡献者明确另行说明，提交给本项目的贡献将按 Apache License 2.0 提供。
