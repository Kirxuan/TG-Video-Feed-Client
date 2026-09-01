# 阶段 15E：全局视觉系统、响应式与无障碍

日期：2026-08-22（Asia/Hong_Kong）
状态：已完成

## 阶段合同

### Outcome

登录、频道、标签、视频和设置形成统一的年轻化 OriginOS-inspired 视觉语言，同时保持 Material 3 语义、
可点击尺寸、暗色可读性、窄屏与大字体可用性。

### Scope

- 静态 light/dark theme、DesignTokens、GlossComponents。
- Login、ChannelSelection、TagFilter、VideoPlayback、CacheSettings。
- Channel → Tags → Feed 与 Settings 导航；Tag ViewModel/UI/测试。

### Boundary

- “OriginOS-inspired” 只指柔和背景、轻玻璃层级、圆角和节奏，不复制 vivo 资源或私有组件。
- 不新增不稳定依赖、网络图片、权限、Room schema、DataStore 字段或业务后端。

### Failure states

- loading/empty/error/disabled/submitting/unsupported 均有固定中文反馈。
- 320dp 宽度、超长中英文标签和 1.35x fontScale 不得裁切关键操作。
- dark theme 内容颜色必须由主题和 Card contentColor 显式提供，不能退化为黑字黑底。

### Proof

- Login、Channel、Tag、Video、Settings、Compose smoke 与 Activity recreation 的 emulator UI 套件：90/90。
- 首次全套发现 1 个测试使用固定 1000px 拖动坐标；改为控件 5%→90% 后，目标测试与完整 90 例均通过。
- Robolectric `:app:testInstrumentationUnitTest --rerun-tasks` 通过。
- 18 张视觉证据全部实际查看，包含 320dp 和 1.35x 字体样本。

## 视觉决定

- 静态渐变、透明填充、细边框和有限阴影构成 gloss 层级，保证低开销和确定性。
- `PremiumBackdrop` 提供 `LocalContentColor`，`GlossCard` 显式设置 `contentColor`，修复 dark theme 继承错误。
- Feed 保持黑色沉浸基底、顶部轻量切换、右侧动作、底部元数据/进度；控制不遮挡主要画面中心。

## Token 与组件说明

| 类别 | 集中定义 | 用途 |
|---|---|---|
| Spacing | 4/8/12/16/24/32 dp | 页面、Card、列表和 hero 节奏 |
| Shapes | 12/16/22/26/30 dp + pill | 输入、控制、Card、主要表面与胶囊 |
| Elevation | 1 dp border、5 dp card、9 dp floating | 有限层级；不依赖大面积 blur |
| Motion | 150 ms feedback、220 ms surface、420 ms loading disclosure、0.98 pressed scale | 只做交互反馈和有限过渡 |
| Feed | obsidian/graphite/elevatedGraphite/iceText/electricBlue | 沉浸视频专用暗色层级 |
| GlossColors | light/dark backdrop、surface、border、highlight、accent/danger | 主题一致的静态光泽配色 |

复用组件：`PremiumBackdrop`（缓存静态线性/径向 Brush）、`GlossCard`、`SettingsGroup`、
`PremiumTopBar`、`StatusPill`、`GlossActionPill`、`SegmentedControl`。所有可点击 pill 至少 48 dp，
选择状态同时使用勾选/边框/文字而非只靠颜色；没有 infinite decorative animation 或 RenderEffect。
