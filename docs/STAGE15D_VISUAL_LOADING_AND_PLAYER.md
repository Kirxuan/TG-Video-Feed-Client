# 阶段 15D：加载反馈与播放器视觉证据

日期：2026-08-22（Asia/Hong_Kong）
状态：已完成

## 阶段合同

### Outcome

竖向视频从目标切换到真实首帧期间有稳定、不闪烁的沉浸式占位；短加载不立即显示转圈，较长加载明确
反馈，成功首帧平滑交接，失败/不支持状态立即替换占位。

### Scope

- `VideoPlaybackScreen.kt` 与 `VideoPlaybackScreenTest.kt`
- test-only `Stage15VisualSnapshotTest.kt`
- `scripts/run-stage15-visual-qa.ps1`
- `scripts/run-emulator-compose-tests.ps1`

### Boundary

- 不伪造 Telegram 登录或媒体来源；视觉测试只向生产 composable 注入惰性测试状态。
- 不向生产 APK 加 demo route、截图依赖或 synthetic feed。
- 不在视频 Surface 上做实时 blur；不改变播放器/缓存架构。

### Failure states

- 0–420 ms：显示稳定 poster，不用 spinner 制造闪烁。
- 超过 420 ms：显示明确进度反馈。
- 首帧：190 ms 交接淡出；新 key 立即恢复全不透明 poster。
- network/decoder/removed/unsupported：直接显示对应终态，不等待成功淡出。

### Proof

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\scripts\run-stage15-visual-qa.ps1 -Serial emulator-5554
.\scripts\run-emulator-compose-tests.ps1 -Serial emulator-5554
```

结果：API 36 AOSP x86_64 emulator 通过；18 张 PNG 已拉取到
`build/reports/stage15-visuals/` 并逐张人工查看。instrumentation target APK 的 `.so` 数量为 0。

## 截图覆盖

登录 light/dark、320dp 频道、频道 light、长标签 dark、即时/延迟加载、首帧、暂停、2x、失败、
不支持流式、详情、设置 light/dark+1.35x 字体、搜索、空态、dark error，共 18 张。
