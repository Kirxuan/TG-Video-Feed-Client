# Stage 19 — UI 精修、动效与 Android 16 全屏手势适配

日期：2026-08-28
状态：主机 Proof、API 36 AOSP x86_64 Compose/UI/系统栏验证和视觉核查通过；iQOO 12 / OriginOS 6 / Android 16 真机尚未验证

## 1. 阶段合同

### Outcome

登录、频道选择、标签筛选、缓存设置和视频播放五个页面共享一套克制的表面、状态与动效语言；Android 16 edge-to-edge 下交互内容避开显示挖孔、系统栏、IME 和强制手势区，根页面不拦截系统返回。

### Scope

- `DesignTokens.kt` 与 `GlossComponents.kt`：Motion Tokens、静态氛围背景、统一卡片/按钮/搜索/分段选择/状态组件。
- `LoginScreen.kt`、`ChannelSelectionScreen.kt`、`TagFilterScreen.kt`、`CacheSettingsScreen.kt`、`VideoPlaybackScreen.kt`：逐屏状态反馈、Insets 和有限动画。
- `ChannelVideoFlowNavHost.kt`、`AndroidManifest.xml`：非根导航返回和 `adjustResize`。
- 共享 Compose 测试、API 36 system UI/视觉 instrumentation 测试与 emulator-only 脚本。
- 本文档、`README.md`、架构、安全、验收和开发计划状态。

### Boundary

- 不修改 TDLib、Room、Media3、播放器单例、Pager 物理参数、预加载、缓存所有权或业务筛选语义。
- 不新增依赖、权限、WebView、网页运行时、Shader、Lottie、粒子、全屏 blur、付费内容或许可不明源码。
- 不使用 `systemGestureExclusion`；没有 API 36 可复现冲突，不扩大系统边缘屏蔽范围。
- 不连接、安装、启动、占用或测试 iQOO 12；不运行 Vivo 真机脚本。

### Failure states

- 登录：idle、loading、success、error、disabled，敏感输入不做逐字动画或日志。
- 频道：loading、empty、partial/content、error、retry、选中/取消选中。
- 标签：focused、clear、empty、error、selected、disabled、OR/AND 切换。
- 缓存：八个离散容量、保存中/成功/失败、清理中/成功/失败。
- 视频：loading、paused、error、streaming unsupported；不让 UI 动画重建播放器。

### Proof

1. Android Studio JBR 下完成 Compose 编译、指定 Robolectric-Compose、全量 `test`、`lint`、`assembleDebug`。
2. API 36 AOSP `x86_64` emulator 运行完整 Compose UI suite。
3. 同一 emulator 分别在 gestural 与 three-button 模式验证真实窗口 Insets、冷启动、根返回；补充横屏、大字体和视觉截图。
4. 物理 iQOO 12 本阶段不使用，结果必须保留为“尚未验证”。

## 2. 实现结果

### 2.1 共享视觉与 Motion Tokens

- `PremiumBackdrop` 使用 `drawWithCache` 的静态、低透明度渐变光斑；没有无限动画、每帧 Brush 分配或视频页背景装饰。
- Motion Tokens 固定按压 90/120 ms、状态 200 ms、表面 220 ms、内容 280 ms，按压缩放为 0.985；所有状态动画都是有限动画。
- `GlossCard` 统一 enabled/selected/error/clickable 状态、语义和短暂按压反馈。`StatefulPrimaryButton` 保持 54dp 尺寸稳定，以文字、图标和语义同时表达 idle/loading/success/error/disabled，loading 时阻止重复提交。
- `SegmentedControl` 复用单一共享选中指示器；搜索框、状态面板、底部主操作和数量 pill 共享同一套状态层级。

### 2.2 五个页面

- 登录与 Telegram 授权：加入轻量步骤指示和有限内容切换，根布局组合 safe drawing 与 IME，错误/加载/禁用语义完整。
- 频道选择：只对发生插入/状态变化的行使用有限 item 动画，选中项有描边、表面与图标反馈；保存与错误状态复用共享组件。
- 标签筛选：搜索聚焦/清除/错误状态统一，OR/AND 继续复用现有 `SegmentedControl`；为避免长列表重组和 CPU 放大，不给整个结果区包裹 `AnimatedContent`。
- 缓存设置：保留 200MB、500MB、1GB、2GB、5GB、10GB、15GB、20GB 和原默认值，用离散 Slider 与既有容量选项同步过渡；缓存业务和清理规则未变。
- 视频播放：保留 `VerticalPager`、唯一主要 ExoPlayer、流式播放与手势；仅调整控制层、暂停、加载、错误和 unsupported 面板的有限淡入/位移，并把控制与进度触控目标保护到安全区域和至少 48dp。

### 2.3 Android 16 edge-to-edge 与返回

- Activity 继续只在既有入口调用 `enableEdgeToEdge()`；页面 `Scaffold` 不再重复消费默认 Insets，顶部、内容、底部操作和 IME 按职责各消费一次。
- 登录/搜索/底部操作使用 `safeDrawing` 与 `imePadding`；视频内容可以延伸至全屏，但返回、右侧控制、进度和底部元数据避开 safe content、cutout 与 gesture 区。
- 视频沉浸式栏控制在前台重新应用，在离开页面、进入后台和 Activity 销毁时恢复系统栏、图标外观与方向；保留边缘滑动临时显示系统栏。
- `BackHandler` 只处理应用内非根层级：Feed 返回 Tags，Tags/Settings 返回 Channels。登录和 Channels 根页面不拦截返回，保留 back-to-home 预测返回路径；没有旧 `onBackPressed`、`KEYCODE_BACK` 或禁用 OnBackInvokedCallback。

## 3. 自动化与视觉证据

### 主机

- `:app:compileInstrumentationKotlin`：PASS。
- 固定 Login/ChannelSelection/ComposeSmoke Robolectric 组：PASS。
- 固定 CacheSettings Robolectric 组：PASS。
- 追加 TagFilter/VideoPlayback/GlossComponents 组：PASS。
- `test --no-daemon --console=plain`：PASS，345 个 Gradle task。
- `lint --no-daemon --console=plain`：PASS，220 个 Gradle task。
- `assembleDebug --no-daemon --console=plain`：PASS，190 个 Gradle task。

首次单独运行 `assembleDebug` 时新 shell 未设置 `JAVA_HOME`，命令在 Gradle 启动前退出；按仓库指定 `E:\Android Studio\jbr` 重跑后通过，不属于源码或构建失败。

补充 cutout Proof 时还定位了三项测试基础设施问题：`-SkipBuild` 路径未设置 JBR、横屏命令在 `NOSENSOR` Launcher 前台执行、instrumentation 结束后截图落到 Launcher。分别通过 runner 提前解析 JBR、先启动目标 Activity 再有界轮询 rotation 90、cutout overlay 仍启用时重启目标 Activity 修复。最终脚本使用真实三键按钮 tap 而非 legacy back key，整套重跑通过且恢复 emulator 原状态。

### API 36 AOSP x86_64 emulator

- `run-emulator-compose-tests.ps1 -Serial emulator-5580`：PASS，95/95。
- `run-stage19-system-ui-qa.ps1 -Serial emulator-5580`：PASS。
  - three-button：2/2 system UI tests、冷启动、实际点击 AOSP 三键返回按钮后回到 Launcher。
  - gestural：2/2 system UI tests、冷启动、边缘返回到 Launcher。
  - display cutout：启用 AOSP 官方非零 hole-cutout overlay 后 1/1，登录输入位于 cutout inset 之外；截图确认状态栏和内容未重叠。
  - landscape：2400×1080、rotation 90、Activity resumed。
  - large font：font scale 1.3，页面可滚动且底部操作可达。
- `run-stage19-visual-qa.ps1 -Serial emulator-5580`：PASS，6 张生产 Compose 截图哈希均不同。
- 截图人工核查：登录、频道、标签、缓存、视频 unsupported 和大字体缓存页未发现裁切、重叠、闪烁、不可读文字或过强背景。
- 修改前后核查：将 Stage 15/16 留存的登录、频道、标签、缓存与 unsupported 视频截图逐项对照 Stage 19 结果；新步骤层级、共享选中态、离散容量控件和安全区控制均可见，原文案与业务信息仍在。历史截图的尺寸/主题不完全一致，因此只作定性视觉回归，不冒充同条件像素 A/B。

报告目录：

- `build/reports/emulator-compose`
- `build/reports/stage19-system-ui`
- `build/reports/stage19-visuals`

## 4. 安全与性能结论

- 没有新增权限、依赖、远程素材、公共存储写入、WebView/JS、付费资源或上游源码复制；通用视觉机制为独立 Compose 实现，因此不新增第三方许可证声明。
- 没有全屏实时 blur、Shader、粒子或无限背景动画。视频页没有氛围背景，播放器数量、Pager、预加载与缓存所有权未修改。
- 加载动画只在真实 loading 状态存在；测试用暂停时钟推进动画，证明动画中与完成后的语义和点击门控。
- TalkBack 状态不只依赖颜色；selected/error/loading/disabled 有语义或文字/图标，关键触控目标至少 48dp。

## 5. 尚未验证与后续边界

- iQOO 12 / OriginOS 6 / Android 16 真机验证：尚未验证（用户设备本阶段不可用）。
- 因真机门槛未执行，不能声称完整 Vivo Compose Path B Proof 通过；本阶段只有主机和 API 36 x86_64 emulator 证据通过。
- 真实 Telegram 登录会话下的 OEM 键盘、挖孔、系统栏暂显和高刷新率功耗：尚未验证。
- 下一阶段必须重新取得用户明确批准；本阶段不进入后续业务功能。
