# 阶段 2 后续 AI 测试与修复提示词

把下面整段原样交给下一位 AI。它是验证/修复任务，不授权进入频道、媒体、标签、信息流或缓存阶段。

```text
你正在接手 E:\Telegram Android Developer 的 Channel Video Flow 阶段 2 验收与必要修复。

先完整阅读根目录 AGENTS.md、README.md、所有 docs、.superpowers/sdd/task-10-report.md、task-11-brief.md、task-11-report.md，以及现有代码。先执行只读 git status；所有文件目前可能未跟踪，不得 reset、clean、覆盖、提交或 push。

目标仅限：验证官方 TDLib 集成、客户端初始化、手机号/验证码/两步密码授权状态机、登录保存、退出、授权错误处理。禁止实现频道列表、扫描、播放、标签、信息流、媒体缓存。

已知固定供应链：TDLib 1.8.66，commit 022d60202e446ad1287b9fb68e687c8a0760788b；OpenSSL 3.5.7 LTS，SHA-256 a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8；目标 arm64-v8a/API 26/c++_static。不要替换为第三方预编译库。先复核 docs/TDLIB_BUILD.md 和 telegram/tdlib/TDLIB_PROVENANCE.md。

当前已知状态：
1. 最近一次完整 host proof 曾有 145 tests、0 failure，lint/assembleDebug 通过，但最终工作树在停止真机诊断后尚未 fresh 重跑。
2. vivo V2307A、Android 16/API 36、arm64-v8a 可被 adb 识别。
3. :telegram:connectedDebugAndroidTest 的官方 JNI smoke 已通过 1/1。
4. :app:connectedDebugAndroidTest 在该 vivo 上会在 Compose 测试规则启动阶段挂起；runner 能发现测试，业务测试方法未完成。不要无限等待。优先在干净的 AOSP arm64 模拟器或另一台 arm64 设备复现；若只在 vivo 挂起，记录厂商测试框架兼容问题，不为此污染生产代码。若多设备复现，最小化为单个 Compose test，检查 Compose 1.11.4 v2 rule、ActivityScenario、test-manifest 宿主和 Android Test Runner 依赖。任何 workaround 必须放 androidTest/debug，且先证明失败再修复，不能删除或禁用测试。
5. local.properties 的 Telegram 凭证形态此前无效。只输出“是否有效”的布尔结果；不得打印、复制或修改值。让所有者自己在本机 local.properties 配置 TELEGRAM_API_ID 和 TELEGRAM_API_HASH，绝不让其发到聊天。
6. 官方生成 Client.java:20 和 TdApi.java:20 各保留一处上游 e.printStackTrace()。不要误报为自定义日志；先审查 OfficialTdLibBridge 预加载和 TelegramClientManager 的脱敏失败映射。若修改生成源码，必须记录补丁并更新哈希/provenance；默认保持官方生成文件不变。

执行顺序：
A. 先静态审查授权状态映射、manager 单客户端生命周期、repository 不可变 Flow、ViewModel、UI、Fake、退出、FLOOD_WAIT、敏感输入清除、私有目录和备份规则。TDLib 类型不得越过 telegram 边界，回调不得直接操作 Compose。
B. 设置 JAVA_HOME=E:\Android Studio\jbr，串行 fresh 运行：
   .\gradlew.bat test --rerun-tasks --no-daemon --console=plain
   .\gradlew.bat lint --rerun-tasks --no-daemon --console=plain
   .\gradlew.bat assembleDebug --rerun-tasks --no-daemon --console=plain
   不要并发 Gradle；Windows 曾因并发 UTP/Gradle产生 classes.jar 文件锁。
C. 复核 debug APK 仅有 arm64-v8a/libtdjni.so 和 arm64-v8a/libandroidx.graphics.path.so；manifest 有效权限恰为 INTERNET、ACCESS_NETWORK_STATE，allowBackup=false 且两套备份规则存在。
D. 连接设备后先跑 :telegram:connectedDebugAndroidTest，再处理 :app:connectedDebugAndroidTest。每个 connected 命令设置合理超时；挂起时保存 UTP 日志和仅含 runner/异常的 logcat，然后终止精确的 Gradle wrapper、UTP 和 app 进程，不杀系统级 broad targets。
E. 凭证有效后运行 installDebug。清理并只读取过滤后的 CVF/Auth 状态日志。让所有者在手机界面亲自输入手机号、验证码、两步密码；禁止 adb input、剪贴板、uiautomator dump、截图、录屏、全量 logcat，且不要要求把任何敏感值发给你。
F. 所有者只回复结果，例如“已登录”。必须看到真实 TDLib updateAuthorizationState -> READY 才能记录登录通过。随后 force-stop 并正常启动，由所有者确认无需再次输入且仍显示已登录；再由所有者在手机点退出，验证 LOGGING_OUT -> CLOSING -> CLOSED，重新初始化后回到 WAIT_PHONE_NUMBER/登录页。
G. 检查日志不含手机号、验证码、密码、api_hash、异常 raw message 或完整 TDLib 对象。FLOOD_WAIT 必须遵守秒数并禁用重试；验证码/密码成功、取消或终止后清空内存引用；输入不得进入 Room/DataStore/SavedStateHandle/SharedPreferences/rememberSaveable。

遇到失败时遵循：复现 -> 最小化 -> 假设 -> 定向取证 -> 最小修复 -> 回归测试。不要顺手扩展范围。未经真实 READY、重启保持和退出回登录的证据，结论必须写“尚未验证”，不得声称阶段 2 全部通过。

完成后更新 .superpowers/sdd/task-11-report.md，并按 AGENTS.md 规定的十个中文标题汇报。列出所有命令、退出码、测试数量、设备结果、已知问题和安全检查；不要自动提交。
```
