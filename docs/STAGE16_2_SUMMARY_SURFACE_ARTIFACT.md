# 阶段 16.2：频道摘要卡片光泽伪影清理

日期：2026-08-22（Asia/Hong_Kong）

## 阶段合同

### Outcome

“已选择频道”和“扫描详情”卡片在折叠、展开及明暗主题下均呈现连续、均匀的圆角表面，不再出现与整体不协调的浅色矩形高光边界。

### Scope

- 既有 `GlossCard` 的内部光泽绘制开关。
- `ChannelSelectionScreen` 中的 `SelectionSummary` 与 `ScanProgressPanel`。
- 阶段 16.2 Android 视觉夹具、拉取脚本、README 和本阶段记录。

### Boundary

- 不修改卡片尺寸、文案、交互、ViewModel、UI state 或扫描业务。
- 不修改其他 `GlossCard` 调用的既有光泽效果，不建立平行设计系统。
- 不修改 TDLib、Room、播放器、Feed、缓存、网络、权限、备份或依赖。
- 不操作实体手机；仅使用 API 36 AOSP x86_64 emulator。

### Failure states

- 浅色折叠、浅色展开和深色展开状态都不得出现矩形高光边界。
- 关闭内部高光后仍须保留圆角裁切、边框、阴影、主题颜色和内容对比度。
- “说明/收起”“详情/收起”和“暂停/继续”行为及至少 48dp 触控语义不得退化。

### Proof

1. `:app:compileInstrumentationKotlin` 与 `ChannelSelectionScreenTest`。
2. `:app:compileInstrumentationAndroidTestKotlin`。
3. 全量 `test --rerun-tasks`、`lint --rerun-tasks`、`assembleDebug --rerun-tasks`。
4. API 36 AOSP x86_64 emulator 上运行 `Stage162VisualSnapshotTest`，逐张人工检查 3 张截图。
5. emulator 默认完整 Compose 套件与权限、备份、边界静态审计。

## 根因与实现决定

- 根因是 `GlossCard` 内容层通过 `drawWithCache` 绘制一个覆盖卡片顶部 34% 高度的矩形渐变。在较矮的紧凑摘要卡上，透明过渡边界会被肉眼感知为卡片中段的浅色长方形。
- 为 `GlossCard` 增加默认值为 `true` 的 `showGlossHighlight` 参数，所有既有调用保持原表现。
- 仅 `SelectionSummary` 与 `ScanProgressPanel` 传入 `false`，跳过矩形高光层；外层 `Surface` 的圆角、边框、阴影、颜色和裁切均保持原实现。
- 没有用额外纯色矩形覆盖伪影，也没有全局删除 OriginOS-inspired 光泽效果。

## 视觉证据

输出目录：`build/reports/stage16-2-visuals/`

| 文件 | SHA-256 | 人工检查结论 |
|---|---|---|
| `01-summary-surfaces-light-collapsed.png` | `317D057EFDFD88F9BE58AF8E269FBAF060DDC5B1C777147F9B086E2F49EABEDD` | 两张折叠摘要卡表面均匀连续，卡片中段无矩形边界；圆角、阴影和一行紧凑布局正常 |
| `02-summary-surfaces-light-expanded.png` | `FF0295F9BD3B2EDC400A0804BAB5CEC88E2433B8DAA8D22EFFAEA6623B6DD99F` | 展开说明、分隔线和完整扫描统计正常；展开区域无白色长方形或突兀色阶 |
| `03-summary-surfaces-dark-expanded.png` | `0ED34826FE48EEC6731D0B467ABC47AAC71076C0F79C6CA068792B9B0581FDB5` | 深色主题表面与描边连续，文字和控制对比度清晰，无浅色矩形伪影 |

三张图片均由测试夹具渲染真实生产 `ChannelSelectionScreen`，未向生产 APK 增加 Fake/Demo 路由。

## 验证记录

### 定向 Proof

- `:app:compileInstrumentationKotlin` + `ChannelSelectionScreenTest`：PASS，128 个任务，11 个实际执行。
- `:app:compileInstrumentationAndroidTestKotlin`：PASS，117 个任务，3 个实际执行。
- `scripts/run-stage162-visual-qa.ps1 -Serial emulator-5554`：PASS；3 张 PNG 已拉取并逐张人工检查。

### 全量主机 Proof

使用 `JAVA_HOME=E:\Android Studio\jbr`：

| 命令 | 结果 |
|---|---|
| `test --rerun-tasks` | PASS；345/345 个任务实际执行 |
| `lint --rerun-tasks` | PASS；220/220 个任务实际执行 |
| `assembleDebug --rerun-tasks` | PASS；190/190 个任务实际执行 |

最终 APK：`app/build/outputs/apk/debug/app-debug.apk`，44,703,008 bytes，SHA-256 `83BC65B325D12EC0781233D85EFAC6E38EE087A753D9A51D1B2A6FECEDAF284E`。

### Emulator 与真机 Proof

- `scripts/run-emulator-compose-tests.ps1 -Serial emulator-5554`：PASS；API 36 AOSP x86_64，`OK (95 tests)`，117.109 秒。
- 未运行 TDLib native smoke；全部验证完成后已关闭 emulator。
- 实体手机安装、启动和交互：**尚未验证，按用户要求未操作 iQOO 12。**

### 安全与边界 Proof

- `aapt2 dump permissions` 显示最终 APK 仍只有 `INTERNET` 与 `ACCESS_NETWORK_STATE`。
- `allowBackup=false`、`dataExtractionRules`、`fullBackupContent` 保持不变。
- 两个生产改动文件静态扫描未出现 TDLib、Room、Media3/ExoPlayer、公共存储、网络资源或 Fake/Demo 引用。
- 没有修改 Gradle 依赖、ViewModel、UI state、Room、Telegram、播放器或缓存代码；仅确认 `local.properties` 被 `.gitignore` 忽略，未读取其内容。
- 未 commit、未 push；仓库仍为 `main`、无提交基线，现有未跟踪文件全部保留。

## 已知提示

- Android SDK XML version 4 解析提示、既有缓存策略废弃 API、KAPT 选项和 native strip 提示仍存在，但没有造成 test、lint、assemble 或 emulator 失败。
