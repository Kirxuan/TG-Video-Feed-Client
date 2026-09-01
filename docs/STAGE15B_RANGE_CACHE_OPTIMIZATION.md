# 阶段 15B：TDLib 区间缓存失效与透明恢复

日期：2026-08-22（Asia/Hong_Kong）
状态：已完成

## 阶段合同

### Outcome

当 TDLib snapshot 声称范围已下载、但对应私有本地文件已经不存在时，当前播放读取能失效旧 snapshot、
重新请求同一区间一次，并继续遵守取消、超时与 owner 边界。

### Scope

- `core/domain/.../TelegramFileGateway.kt`
- `player/.../TelegramMediaDataSource.kt` 及单元测试
- `telegram/.../TelegramFileManager.kt` 及单元测试

### Boundary

- 不改变 4 MiB 当前前读、256 KiB 唯一下一条、TDLib priority、缓存额度或网络策略。
- 不新增 Media3 cache，不执行完整下载，不跨越 telegram 数据边界暴露 TDLib 类型。

### Failure states

- 本地路径缺失/无法打开：仅在路径仍对应当前 snapshot 时失效并重试一次。
- owner close、协程取消、等待超时或下载无进展：保持原有限、可唤醒的失败语义。
- snapshot 已更新为其他路径：旧失败不得清除新路径。

### Proof

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :player:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :telegram:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
```

结果：两项均 `BUILD SUCCESSFUL`。新增测试覆盖 stale path 重试一次、有效路径不重试、路径相等才失效、
新 snapshot 不被旧失败清除。

## 实现决定

- `TelegramFileGateway.invalidateLocalSnapshot` 提供默认空实现，避免破坏既有 Fake；官方实现执行受路径相等保护的失效。
- `TelegramMediaDataSource` 只有在 `RandomAccessFile` 成功打开后才宣告 range ready；打开失败先失效再回到
  原有区间请求循环，最多一次透明恢复。
- 没有主线程文件等待，没有新增日志敏感字段。
