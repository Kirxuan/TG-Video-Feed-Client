# Stage 17：GitHub/API/许可证复用审计

## 审计原则

只采用官方文档、锁定版本源码和本仓库测试支持最终决定。外部项目只参考概念；没有复制、改写或移植 GPL 代码。本阶段没有新增依赖或许可证文件。

| 来源 | 固定版本/commit | 许可证 | 复用方式 | 本项目适配与结论 |
|---|---|---|---|---|
| [TDLib `getFileDownloadedPrefixSize`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_file_downloaded_prefix_size.html)、[result](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1file_downloaded_prefix_size.html)、[`downloadFile`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1download_file.html) | TDLib 1.8.66，commit [`022d60202e446ad1287b9fb68e687c8a0760788b`](https://github.com/tdlib/td/commit/022d60202e446ad1287b9fb68e687c8a0760788b) | [Boost Software License 1.0](https://github.com/tdlib/td/blob/022d60202e446ad1287b9fb68e687c8a0760788b/LICENSE_1_0.txt) | 官方 API 与 offset/limit 语义 | **采用**。client 边界映射为 app-owned `Long`；同 offset single-flight，lease-local 命中，不扩大 range。无外部代码复制。 |
| [Media3 `AdaptiveTrackSelection`](https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/AdaptiveTrackSelection.java) | 1.10.1，tag 对应 commit `5fb306449733dd71595700c1227ad6087578c559` | [Apache-2.0](https://github.com/androidx/media/blob/1.10.1/LICENSE) | 算法思想 | **采用思想**：0.7 安全系数、选择可持续最高码率、快降慢升和滞回。Telegram alternatives 是不同文件，不复制 Media3 track-selection 代码。 |
| [Media3 `DefaultBandwidthMeter`](https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/upstream/DefaultBandwidthMeter.java) | 同上 | Apache-2.0 | API/源码审阅 | **拒绝直接接入**。本项目 DataSource 读取 TDLib 已落地的私有文件，TransferListener 会混入本地磁盘速度，不能证明网络带宽。 |
| [Media3 `DefaultPreloadManager`](https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/preload/DefaultPreloadManager.java)、[官方文档](https://developer.android.com/media/media3/exoplayer/preloading-media/preloadmanager) | 同上 | Apache-2.0 | API/源码审阅 | **拒绝生产候选**。共享 factory 会让 source preload 使用 CURRENT 4 MiB read-ahead；无法保证唯一 NEXT 256 KiB。`specifiedRangeCached` 依赖被禁止的 Media3 Cache。 |
| Telegram Android [`FileStreamLoadOperation`](https://github.com/DrKLO/Telegram/blob/3f03bfc73f1d176e349765c2990e52f490409813/TMessagesProj/src/main/java/org/telegram/messenger/FileStreamLoadOperation.java) | commit `3f03bfc73f1d176e349765c2990e52f490409813` | [GPL-2.0-or-later](https://github.com/DrKLO/Telegram/blob/3f03bfc73f1d176e349765c2990e52f490409813/LICENSE) | 只参考概念 | **禁止代码复用**。只确认“检查当前位置已下载范围、等待新数据、读取同一本地文件”是成熟方向；实现完全使用现有仓库结构和 TDLib 官方 API。 |

## 供应链结论

- Media3 继续锁定 1.10.1；TDLib 继续锁定 1.8.66；没有 alpha/beta/RC/snapshot。
- 没有新增 Maven/GitHub 依赖、native binary、权限或网络端点。
- 生产代码中没有 Telegram Android GPL 源码、结构性移植或派生实现。
- `DefaultPreloadManager` 和 `DefaultBandwidthMeter` 均未接入生产代码。
