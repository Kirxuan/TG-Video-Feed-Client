# 阶段 7 预加载、有界缓存和资源保护交接

日期：2026-07-28
状态：实现、完整主机 Proof、Compose Path B、Room 设备迁移测试和真机安装/启动已通过；真实账号媒体观察等待验收

## Outcome

稳定当前页继续使用唯一 `VideoPlayerManager`/ExoPlayer；只对逻辑下一条以 TDLib 优先级 8 请求 256KiB 首段。当前播放以优先级 32 读取，当前与下一条分别持有运行时 pin/owner。TDLib 私有视频缓存默认限制为 500MB，并可选择 200MB、500MB、1GB、2GB、5GB、10GB、15GB、20GB。

## 实现边界

- `VideoPreloadManager` 只保存一个下一条目标和一个范围 lease；页面不稳定、目标变化、离线、移动数据默认策略、省电、低存储或 MODERATE 以上热状态都会释放旧 lease。
- 当前视频在播放器绑定前同步取得 `CURRENT_PLAYBACK` pin；下一条在预加载目标建立时同步取得 `NEXT_PRELOAD` 范围 owner。清理候选在删除前由 `TelegramFileManager` 再次检查。
- 缓存占用以官方 `GetStorageStatistics` 中 `FileTypeVideo` 的精确字节数为事实来源。TDLib 尚未 Ready 时，界面只把私有 `cacheDir/tdlib/files` 的 `stat.st_blocks × 512` 结果标记为启动估算。
- 自动清理先按 Room v4 `media_cache_entries` 的最近访问时间选择未 pin `fileId` 并调用官方 `DeleteFile`。只有当前不存在任何 pin 时，才允许 `OptimizeStorage(FileTypeVideo)` 清理由阶段 7 之前产生、尚无 LRU 元数据的遗留文件。
- 没有 `SimpleCache`、`CacheDataSource` 或第二份 Media3 文件缓存；没有公共目录、MediaStore、FileProvider 或新增存储权限。
- 手动清空先停止预加载并释放播放器绑定，只删除 TDLib 视频缓存和 LRU 元数据；登录状态、频道索引、视频元数据、标签索引与 DataStore 设置不在删除范围。
- 缓存管理在 Application 启动时注册并在 TDLib Ready 后执行精确检查；持续下载期间最多每 2 秒检查一次。实现不依赖应用退出回调。

## 自动化覆盖

- `MediaCachePolicyTest`：500MB 默认值、八档换算、LRU 次序、当前/下一条保护与释放后可清理。
- `VideoPreloadPolicyTest`：Wi‑Fi、移动数据默认关闭/显式开启、离线、省电、低存储、UNKNOWN/MODERATE/SEVERE 热状态。
- `TelegramFileManagerTest`：CURRENT/NEXT 删除保护、最后 owner 取消、排队取消、当前 4MiB 有界前读。
- `VideoPreloadManagerTest`：只保留最后下一条、256KiB、优先级 8、快速目标变化释放及策略变化暂停/恢复。
- `CacheSettingsScreenTest`：精确占用、500MB 默认值、八档选项和手动清空确认。

## 完整验证记录

### 主机

- `.\gradlew.bat test --rerun-tasks --no-daemon --console=plain`：通过，345 个 task 全部重新执行。
- `.\gradlew.bat lint --rerun-tasks --no-daemon --console=plain`：通过，220 个 task 全部重新执行；修改迁移夹具后再次增量执行 `lint` 也通过。
- `.\gradlew.bat assembleDebug --rerun-tasks --no-daemon --console=plain`：通过，190 个 task 全部重新执行。
- 阶段 7 领域、Telegram 文件管理、TDLib 存储统计、预加载管理、播放器绑定和缓存设置的针对性测试均通过。

### Compose Path B 与 Room

- `:app:compileInstrumentationKotlin`：通过。
- `:app:testInstrumentationUnitTest`：登录、频道选择、Compose smoke、视频信息流和缓存设置共享测试通过。
- `scripts/run-emulator-compose-tests.ps1 -Serial emulator-5554`：API 36 AOSP x86_64 AVD 上 29/29 通过，目标包无 crash/ANR。
- `:core:database:connectedDebugAndroidTest`：同一 AVD 上 12/12 通过，包括 Room 1→2、2→3、3→4 迁移。
- 初次 Room 设备运行在 UTP 启动时曾报告 0 tests/process crashed；手动复现显示测试可启动，并定位到 v2/v3 迁移夹具遗漏既有 `NOT NULL` 扫描字段。修正夹具后使用标准 Gradle 命令重跑通过；不是生产迁移失败。

### 真机

- 设备：Xiaomi 21091116UC，Android 13/API 33，arm64-v8a；验证时为已验证 Wi‑Fi、非省电模式、Thermal Status 0。
- `:app:installDebug`：通过。
- `scripts/run-vivo-launch-smoke.ps1 -Serial <device-serial> -SkipBuild`：通过；`MainActivity` 冷启动并保持 top/resumed，目标包无 Java/native crash。脚本名沿用 Path B，实际设备不是 Vivo。
- TDLib 媒体根目录为 app-private `cache/tdlib/files`；Room 位于 app-private `databases/`，TDLib 会话位于 app-private `no_backup/tdlib/database`。设备上不存在本应用的 `/sdcard/Android/data` 或 `/sdcard/Android/media` 目录。
- MediaStore 视频表针对内部路径以及本应用 external data/media 路径的查询返回 `No result found`。系统相册中以包名命名的三项是系统截图功能写入 `DCIM/Screenshots` 的 JPEG，不是应用媒体缓存。
- 该真机安装前没有目标应用本体，安装后停在 Telegram 授权页；没有可复用的真实登录、频道索引或非空视频缓存。因此“非空 TDLib 视频缓存不出现在相册”的动态样本、超过阈值后的真机增长上界、手动清空后真实账号数据保留，以及真实播放/快速滑动/发热/耗电观察均为**尚未验证**。没有用 Fake 或伪登录代替。
