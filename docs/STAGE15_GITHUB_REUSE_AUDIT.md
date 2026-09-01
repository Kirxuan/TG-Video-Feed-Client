# 阶段 15：GitHub 复用与拒绝审计

日期：2026-08-22（Asia/Hong_Kong）
状态：已完成

## 审计合同

- 优先使用官方、与本地稳定版本对应的源码或文档。
- 默认只采用概念，不复制第三方代码、资源、图标、布局或 shader。
- 任何候选都必须保持单 ExoPlayer、单 PlayerView、唯一下一条、TDLib 私有单份媒体缓存、可取消 generation 与稳定依赖。
- Media3 main 分支能力不能直接视为本地 1.10.1 可用 API。
- MonoGram 仅作交互/分层参考，禁止复制 GPL-3.0 代码或资源。
- Cloudy 仅作视觉概念参考；不增加 alpha 依赖，不在视频 Surface 上实时模糊。

## 审计结果

| 来源 | 固定版本 | 许可证 | 采用范围 | 结论 |
|---|---|---|---|---|
| [androidx/media](https://github.com/androidx/media/tree/1.10.1) | tag `1.10.1`；peeled commit `5fb306449733dd71595700c1227ad6087578c559` | Apache-2.0 | `DataSpec` position/length、DataSource 取消/异常边界、播放器监听状态机概念 | 仅核对本地 Media3 1.10.1 行为；未复制源码 |
| [android/performance-samples](https://github.com/android/performance-samples/tree/43d879e9f998e8fb854ed027db6aff89880d016c) | `43d879e9f998e8fb854ed027db6aff89880d016c` | Apache-2.0 | 帧/启动指标使用单调时间、测量与功能逻辑分离 | 采用概念；未引入依赖或代码 |
| [android/nowinandroid](https://github.com/android/nowinandroid/tree/7d45eae4f8720a0c77f507712ba2437ff974b6ed) | `7d45eae4f8720a0c77f507712ba2437ff974b6ed` | Apache-2.0 | design token、静态 light/dark scheme、语义化组件边界 | 采用概念；视觉参数为本项目重新设计 |
| [android/compose-samples](https://github.com/android/compose-samples/tree/v2026.03.00) | tag `v2026.03.00`；peeled commit `344a8409e1274d94e5c9c2ceb0f4f3536301633e` | Apache-2.0 | 状态提升、测试 tag、响应式 Compose 测试方式 | 采用概念；未复制布局、资源或图标 |
| [MonoGram](https://github.com/monogram-android/monogram/tree/7da0bb8936b88808b606781da066b56bed296a64) | `7da0bb8936b88808b606781da066b56bed296a64` | GPL-3.0 | Telegram 信息层级和沉浸式媒体交互参考 | 严格只看概念；拒绝复制 GPL 代码/资源 |
| [Cloudy](https://github.com/skydoves/Cloudy/tree/cd34c2dac45b2e990875b6b5c541c5d7c58089d9) | `cd34c2dac45b2e990875b6b5c541c5d7c58089d9` | Apache-2.0 | 雾面/玻璃质感参考 | 拒绝：依赖为 alpha，且实时模糊视频 Surface 风险高 |

## 实际复制与适配

- 复制的第三方代码、shader、图片、图标、布局：**0**。
- 新增外部依赖：**0**；继续只使用仓库现有稳定依赖。
- UI 使用 Compose 原生渐变、透明度、边框和阴影实现；没有实时抓取或模糊 PlayerView Surface。
- Media3 行为以本地锁定的 1.10.1 API 为准，没有把 main 分支 API 当作已安装能力。

## 架构与许可证检查

- 单 ExoPlayer、单 PlayerView、唯一下一条 256 KiB、当前 4 MiB、TDLib 私有单份缓存均保持。
- 没有形成 Media3 + TDLib 双完整缓存，也没有多播放器预热。
- GPL 来源没有进入源码、资源、测试或产物；Apache 来源也只采用抽象概念，因此无需加入复制声明。
