# 阶段 16.1：频道选择摘要卡片紧凑化

日期：2026-08-22（Asia/Hong_Kong）

## 阶段合同

### Outcome

“已选择”与“扫描”摘要在折叠、展开状态下都明显降低高度和空白，同时保留置顶说明、完整扫描统计及暂停/继续能力。

### Scope

- `ChannelSelectionScreen` 中的 `SelectionSummary` 与 `ScanProgressPanel`。
- 频道 Compose 高度/行为测试、阶段 16.1 Android 视觉夹具和拉取脚本。
- 对应字符串、README 和本阶段记录。

### Boundary

- 不修改 ViewModel、UI state、扫描业务、TDLib、Room、播放器、Feed、缓存、权限、备份或依赖。
- 不操作实体手机；用户提供的三张真机截图仅作为问题证据。
- 不覆盖阶段 15、阶段 16 的既有视觉证据。

### Failure states

- 扫描中、暂停、不可控制和失败摘要继续可见。
- 折叠态必须保持一行；展开态不得重新生成独立的空操作行。
- 320dp / 1.35 字体下，紧凑摘要允许省略，但详情、控制和完整统计不得不可达。

### Proof

1. `:app:compileInstrumentationKotlin` 与 `ChannelSelectionScreenTest`。
2. `:app:compileInstrumentationAndroidTestKotlin`。
3. 全量 `test --rerun-tasks`、`lint --rerun-tasks`、`assembleDebug --rerun-tasks`。
4. API 36 AOSP x86_64 emulator 上运行 `Stage161VisualSnapshotTest`，逐张人工检查 3 张截图。

## 实现决定

- 根因是 `GlossCard` 默认 12dp 子项间距叠加两个独立的 48dp 操作行。扫描卡原先把“暂停扫描”单独放在第二行，展开后又加入分隔线和详情，形成明显空白。
- 两张卡均改为只有一个直接子 `Column`，内部使用 4dp 节奏，避免继承全局卡片的大间距。
- 扫描摘要、详情切换与暂停/继续操作合并到同一行；控制继续保持至少 48dp 触控区域。
- 折叠扫描文案缩为“扫描 N 条 · 视频 N”，页数与频道完成进度保留在展开详情中。
- 新增根节点 test tag 和高度回归断言：折叠扫描卡与选择卡同级；展开卡不得超过折叠高度的 2.2 倍，扫描详情不得再比选择详情多出独立操作行。

## 视觉证据

输出目录：`build/reports/stage16-1-visuals/`

| 文件 | SHA-256 | 人工检查结论 |
|---|---|---|
| `01-channel-summaries-collapsed.png` | `2FC59D70ABDC34985F3FFFA13FF7F6DEB136A6CC3B71F48852C6F20BCF83310B` | 两张摘要均为单行紧凑卡；“详情/暂停”同排，频道列表明显上移 |
| `02-channel-summaries-expanded.png` | `CBEA7DE56F6D405F14A7A3AE157C5B692E936D9E3A16BD6FC09CC60EE162E3F1` | 两块展开内容紧贴标题行；无独立空操作行；完整页数和频道进度可见 |
| `03-channel-summaries-expanded-320dp-font135.png` | `9551291438EDC9D4F976E356394EABC7EB480D22D6C914126668BCA953EDC569` | 320dp / 1.35 字体下控制仍单行可用；完整说明与统计正常换行；底部按钮保持可见 |

## 验证记录

### 定向 Proof

- `:app:compileInstrumentationKotlin` + `ChannelSelectionScreenTest`：PASS，128 个任务。
- `:app:compileInstrumentationAndroidTestKotlin`：PASS，117 个任务。
- 首次定向编译因误删文件后半段重试状态仍需要的 `OutlinedButton` import 而失败；恢复该 import 后原命令通过。没有用跳过测试、禁用 lint 或 suppress 绕过失败。

### 全量主机 Proof

使用 `JAVA_HOME=E:\Android Studio\jbr`：

| 命令 | 结果 |
|---|---|
| `test --rerun-tasks` | PASS；345/345 个任务实际执行 |
| `lint --rerun-tasks` | PASS；220/220 个任务实际执行 |
| `assembleDebug --rerun-tasks` | PASS；190/190 个任务实际执行 |

最终 APK：`app/build/outputs/apk/debug/app-debug.apk`，44,703,008 bytes，SHA-256 `22A0EF14A6C5D6DABCB65CB5599CC49C413DC667FDE742ED5A7F5EDD94E1A58C`。

### Emulator 与视觉 Proof

- `scripts/run-stage161-visual-qa.ps1 -Serial emulator-5554`：PASS；2 个测试方法，3 张 PNG，均已逐张人工检查。
- `scripts/run-emulator-compose-tests.ps1 -Serial emulator-5554`：PASS；API 36 AOSP x86_64，`OK (95 tests)`。
- 未运行 TDLib native smoke；验证完成后已关闭 emulator。
- 实体手机安装、启动和交互：**尚未验证；本阶段未连接或操作实体手机，真机判断仅依据用户提供的三张问题截图。**

### 安全与边界 Proof

- `aapt2 dump permissions` 仍只有 `INTERNET` 与 `ACCESS_NETWORK_STATE`。
- `allowBackup=false`、`dataExtractionRules`、`fullBackupContent` 保持不变。
- 修改范围静态扫描未出现 TDLib 类型、播放器、第二缓存、公共存储、网络资源或生产 Fake/Demo。
- 没有修改 Gradle 依赖、ViewModel、UI state、Room、Telegram、播放器或缓存代码；未读取 `local.properties` 内容。
- 未 commit、未 push；仓库仍为 `main`、无提交基线，现有未跟踪文件全部保留。

### 已知提示

- Android SDK XML version 4 解析提示、既有缓存策略废弃 API、KAPT 选项和 native strip 提示仍存在，但没有造成 test、lint、assemble 或 emulator 失败。
