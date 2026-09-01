# 阶段 16：频道选择与标签筛选 UI 优化

日期：2026-08-22（Asia/Hong_Kong）

## 阶段合同

### Outcome

频道选择页以等宽、等高、单行标签的三入口快捷组和覆盖主体的单一滚动列表呈现；标签页具有可发现、可清除、可测试且不破坏已选状态的本地搜索。

### Scope

- `ChannelSelectionScreen`、`TagFilterScreen`、`TagFilterUiState`、`TagFilterViewModel` 和纯搜索归一化函数。
- 复用并扩展 `GlossComponents`、`DesignTokens`、字符串、官方本地图标。
- 对应 ViewModel、Robolectric Compose、语义、窄屏/大字体、light/dark 和阶段 16 视觉测试。
- 本阶段复用审计、截图脚本和文档。

### Boundary

- 不修改 TDLib、Room schema、播放器、Feed、媒体/缓存策略、权限、备份规则、导航业务语义或依赖版本。
- 不新增网络查询；标签搜索只过滤已经加载的数据。
- 不覆盖阶段 15 视觉证据，不向生产 APK 加 Fake/Demo 路由。
- 不操作 iQOO 12 或其他实体手机。

### Failure states

- 频道：loading、empty、error、刷新、保存禁用、无索引视频禁用、扫描中、暂停、FLOOD_WAIT/重试。
- 标签：loading、无频道、空标签、无搜索结果、继续按钮禁用，以及清除查询/清除选择的独立恢复。
- 布局：320dp、1.35 字体、暗色、键盘/导航栏 Insets、长标题和滚动到底仍须可用。

### Proof

1. `:app:compileInstrumentationKotlin`。
2. `ChannelSelectionScreenTest`、`TagFilterScreenTest`、`ComposeSmokeTest` 的 Robolectric-Compose。
3. ViewModel/纯函数定向测试与全量 `test`。
4. `lint`、`assembleDebug`。
5. 若存在明确 API 36 AOSP x86_64 `emulator-*`：emulator Compose UI 与 `Stage16VisualSnapshotTest`；逐张人工查看截图。

## 实现摘要

- 频道页只有一个主 `LazyColumn`。快捷入口、搜索、选择/置顶摘要、扫描摘要和频道卡片共同滚动，底部保存固定并处理导航栏/IME Insets。
- 三个快捷入口始终三等分，使用同系列本地图标和单行短标签；无索引视频时“浏览视频”保留但禁用并暴露无障碍说明。
- 选择和扫描详情默认收敛为单行摘要，可展开；暂停/继续为紧凑尾部操作。频道行压缩空白，父项与 Checkbox 都暴露勾选状态，长标题最多两行。
- 标签查询进入 `TagFilterUiState`/`TagFilterViewModel`；`normalizeTagSearchQuery` 先 trim、移除可选 `#`、NFKC，再以 `Locale.ROOT` 小写。预计算 `SearchableTag`，同时匹配 display/normalized 名称。
- 搜索只改变可见标签；选择集合、选中计数和最终 `VideoFilter` 基于完整标签集合。“清除搜索”与“清除选择”完全分离。
- 返回使用统一图标按钮；模式为等宽“任一标签/全部标签” Tab 语义；标签使用 Checkbox，不再使用 Unicode 空圆圈。

## 视觉证据合同

- 生成器：`Stage16VisualSnapshotTest`，真实生产 Composable + androidTest 本地夹具。
- 拉取脚本：`scripts/run-stage16-visual-qa.ps1`，拒绝非 `emulator-*` serial。
- 输出目录：`build/reports/stage16-visuals/`。
- 计划截图：频道扫描态、无视频禁用态、320dp/1.35 字体、标签默认、搜索结果、无结果、暗色，共 7 张。

## 验证记录

### 主机 Proof

使用 `JAVA_HOME=E:\Android Studio\jbr`：

| 验证 | 结果 |
|---|---|
| `:app:compileInstrumentationKotlin` | PASS；90 个任务，最终执行为 90 up-to-date |
| `ChannelSelectionScreenTest` + `TagFilterScreenTest` + `ComposeSmokeTest` | PASS；128 个任务，最终执行为 128 up-to-date |
| `test --rerun-tasks` | PASS；345/345 个任务实际执行 |
| `lint --rerun-tasks` | PASS；220/220 个任务实际执行 |
| `assembleDebug --rerun-tasks` | PASS；190/190 个任务实际执行；产物 `app/build/outputs/apk/debug/app-debug.apk` |

开发过程中首先修复了扩展测试本身的滚动/语义定位问题；窄屏摘要改为 `BoxWithConstraints` 时曾因接收者不明确出现一次编译错误，改为显式 `this@BoxWithConstraints.maxWidth` 后，以上最终 Proof 全部通过。剩余输出只有仓库既有缓存策略的 Android 废弃 API 警告、KAPT 未识别选项警告和 native strip 提示，不是阶段 16 失败。

### Emulator Compose Proof

- 目标：`emulator-5554`，API 36、AOSP、ABI `x86_64`；脚本拒绝非 `emulator-*` serial。
- `scripts/run-emulator-compose-tests.ps1 -Serial emulator-5554`：PASS，`OK (95 tests)`。
- 未运行 TDLib native smoke；验证完成后已关闭该 emulator。
- iQOO 12 / 其他实体手机：**尚未验证，按用户要求未操作 iQOO 12**。

### 阶段 16 视觉证据与人工复核

`scripts/run-stage16-visual-qa.ps1 -Serial emulator-5554`：PASS。7 张图片均由真实生产 Composable 和 androidTest 本地夹具生成，逐张通过图像查看工具人工复核：

| 截图 | SHA-256 | 人工检查结论 |
|---|---|---|
| `01-channels-scanning.png` | `7DE3A3D91C2F11A3E4C057F1F35318D38D65696551E8C5CF8AA0CD1EBF0F5F8A` | 三入口等宽等高；标签单行；选择/扫描摘要紧凑；频道列表覆盖主体；保存按钮固定 |
| `02-channels-no-videos-disabled.png` | `2B57ADF7131C7635635775B86CEDE63E8CF2DDB59E23A36102D178F19E24743A` | “浏览视频”仍占相同宽度并明确禁用；其余入口不跳宽 |
| `03-channels-320dp-font135.png` | `138A079D9F5005DFC71F9256E4390CC1AE2044CA705F135430371273E639EED9` | 320dp / 1.35 字体下三个短标签仍单行；长标题最多两行；主按钮无裁切或重叠 |
| `04-tags-default.png` | `B6096D52D514AD64838F9DF8F834DC7017651C47E231D0DCE288646A0CED152D` | 搜索入口始终可见；模式严格二等分；Checkbox、基线、圆角一致；底部按钮固定 |
| `05-tags-search-result.png` | `AB7EDA50B60F1781AFB83F45EAAA39ECC1BE2F9C8799812C6BF7ABC42971F67F` | `#CITY` 结果正确；显示 1/6；隐藏项未清除“已选择 2 个标签”；清除搜索/选择文案区分明确 |
| `06-tags-no-results.png` | `EE0C20418780E185179E8A3823EEB398FB833074FB1CE7D61006180A5F141171` | 无匹配提示和恢复操作清楚；现有选择和继续按钮保持可用 |
| `07-tags-dark.png` | `B67DA57BCF7509ED00858944FA4E9FD8CA6187D16A4D88792764FB179A478CB5` | 暗色搜索边框、选中/未选中卡片、文字与固定按钮对比清楚；选中态不只靠颜色 |

阶段 15 的 `build/reports/stage15-visuals/` 18 张证据未被覆盖，时间戳均保持为阶段 16 开始前的 `2026-08-22 17:11:29`。

### 安全与边界 Proof

- 最终 debug APK 的 `aapt2 dump permissions` 只有 `android.permission.INTERNET` 和 `android.permission.ACCESS_NETWORK_STATE`。
- `allowBackup=false`、`dataExtractionRules`、`fullBackupContent` 及全域排除规则保持不变。
- 阶段 16 生产修改范围静态扫描未发现公共存储路径、网络资源、真实凭证、生产 Fake/Demo、ExoPlayer 或 `SimpleCache` 引用。
- 未新增依赖，未修改 TDLib、Room schema、播放器、Feed、媒体/缓存策略或权限；未读取 `local.properties` 内容。
- 未 commit、未 push。
