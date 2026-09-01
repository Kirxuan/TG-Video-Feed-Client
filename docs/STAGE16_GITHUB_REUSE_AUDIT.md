# 阶段 16：GitHub 复用与许可证审计

日期：2026-08-22（Asia/Hong_Kong）
状态：实现完成，主机 Proof 与视觉 Proof 结果见阶段文档

## 审计合同

- 只核对官方、稳定、许可证明确的来源；本地 Compose BOM `2026.06.01` 和实际解析到的 Material 3 `1.4.0` 才是可用 API 边界。
- 默认采用状态管理、信息层级、可访问性和截图测试方法，不复制第三方布局。
- 只有官方 Material Design Icons 的六个 SVG path 被适配为本地 VectorDrawable；没有新增依赖、网络图片、字体或运行时资源。
- GPL 及许可证不明素材一律不复制。

## 精确来源与结论

| 来源 | 固定版本 | 许可证 | 采用或复制范围 | 拒绝范围 |
|---|---|---|---|---|
| [Jetsnack Search.kt](https://github.com/android/compose-samples/blob/344a8409e1274d94e5c9c2ceb0f4f3536301633e/Jetsnack/app/src/main/java/com/example/jetsnack/ui/home/search/Search.kt) | tag `v2026.03.00`；peeled commit `344a8409e1274d94e5c9c2ceb0f4f3536301633e` | Apache-2.0 | 采用查询状态提升、输入即筛选、清除查询和无结果恢复的概念 | 未复制布局或代码；没有照搬示例的产品视觉 |
| [JetNews InterestsScreen](https://github.com/android/compose-samples/blob/344a8409e1274d94e5c9c2ceb0f4f3536301633e/JetNews/app/src/main/java/com/example/jetnews/ui/interests/InterestsScreen.kt) | tag `v2026.03.00`；同上 commit | Apache-2.0 | 采用可选择列表、状态不只靠颜色、窄屏和大字体可用的概念 | 未复制列表实现或资源 |
| [Now in Android screenshot tests](https://github.com/android/nowinandroid/tree/7d45eae4f8720a0c77f507712ba2437ff974b6ed/app/src/testDemo/kotlin/com/google/samples/apps/nowinandroid/uitesthilt) | commit `7d45eae4f8720a0c77f507712ba2437ff974b6ed` | Apache-2.0 | 采用集中组件、生产 Composable + 测试夹具、light/dark 截图矩阵的方法 | 未复制其 design system、截图基线或产品资产 |
| [AndroidX Material 3 samples](https://github.com/androidx/androidx/blob/ff9a7111302243197384c499d5e3461c1804cd6e/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/CheckboxSamples.kt) | `androidx-main` commit `ff9a7111302243197384c499d5e3461c1804cd6e`（仅审阅日固定） | Apache-2.0 | 核对 Checkbox/可选择语义的稳定用法；实际实现只使用本地已解析 Material 3 `1.4.0` API | 拒绝直接使用 main 分支实验 API；不增加 alpha/beta/RC 依赖 |
| [Google Material Design Icons](https://github.com/google/material-design-icons/tree/e083cc60a0828fdd3b404cea0cb8a5b900e9c23e/src) | master commit `e083cc60a0828fdd3b404cea0cb8a5b900e9c23e` | Apache-2.0 | 复制并适配 `logout`、`settings`、`play_circle_outline`、`search`、`clear`、`arrow_back` 六个 24px outlined SVG path 为 VectorDrawable | 未复制图片、字体、整套图标包或构建代码；没有 Unicode/手绘图标混用 |

## 实际进入仓库的外部内容

- 六个图标文件：`ic_logout_outlined.xml`、`ic_settings_outlined.xml`、`ic_play_circle_outlined.xml`、`ic_search_outlined.xml`、`ic_clear_outlined.xml`、`ic_arrow_back_outlined.xml`。
- 每个文件头都保留精确固定 commit 的原始 SVG URL、图标名和 Apache-2.0 声明；统一许可证说明位于 `app/src/main/res/raw/material_design_icons_license.txt`。
- 复制的第三方 Kotlin/Java 代码、布局、图片、shader：**0**。
- 新增外部依赖：**0**。

## 拒绝理由

- GitHub `main`/`androidx-main` 中本地 BOM 没有的 API：版本不稳定或兼容性未经本地证明，拒绝。
- GPL 代码/资源、许可证不明素材、网络图片、Unicode 字符图标、额外图标库：许可证、离线性或视觉一致性不满足合同，拒绝。
- 为单个页面引入新的设计系统、搜索库或截图库：与现有 `GlossComponents`、Material 3 和测试基础设施重复，拒绝。
