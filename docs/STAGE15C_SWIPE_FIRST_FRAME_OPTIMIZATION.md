# 阶段 15C：翻页计划与首帧状态协调

日期：2026-08-22（Asia/Hong_Kong）
状态：已完成

## 阶段合同

### Outcome

同一 `chatId + messageId` 的 Room 元数据更新不会清空仍兼容的 PlaybackPlan；翻页 settle 后可以复用已经
准备的消息引用和计划，减少重复 refresh/rebuild，同时旧 generation 仍不能污染当前项。

### Scope

- `app/.../VideoPlaybackViewModel.kt`
- `app/.../VideoPlaybackViewModelTest.kt`

### Boundary

- 不修改 Pager 手势、snap duration、ExoPlayer 数量、PlayerView attach/detach 或区间大小。
- 不启用阶段 13B 被否决的 active-request reuse；不改变随机轮次语义。

### Failure states

- key、quality、network、account、queue 或 random generation 不兼容：仍必须丢弃旧计划。
- 同 key 仅元数据变化：保留可播放计划，同时以最新领域对象更新 UI。
- 删除、权限丢失、unsupported 或真正的 plan failure：终态仍优先于空 feed。

### Proof

```powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
.\gradlew.bat test --rerun-tasks --no-daemon --console=plain
```

结果：均通过；新增 same-key compatible metadata 写回的 plan single-flight 回归测试。

## 实现决定

- 兼容性以完整 `VideoKey(chatId, messageId)` 和既有 plan context 判定，绝不假设 messageId 全局唯一。
- ViewModel 只协调 Repository/UseCase 与播放器接口，没有把 TDLib、DAO 或 ExoPlayer 引入 UI 层。
