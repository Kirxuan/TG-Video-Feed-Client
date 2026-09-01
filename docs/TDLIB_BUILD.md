# TDLib 1.8.66 可复现构建记录

本文记录 VELORA（曜流）当前入库的官方 TDLib 授权绑定。它不是登录或设备验收记录；真实登录、重启保持、退出和安装均由 Task 11 单独验证。

## 固定来源与目标

| 项目 | 固定值 |
|---|---|
| TDLib 官方 Git remote | `https://github.com/tdlib/td.git` |
| TDLib commit / HEAD | `022d60202e446ad1287b9fb68e687c8a0760788b` |
| TDLib 版本 | `1.8.66` |
| OpenSSL 官方 release | `https://github.com/openssl/openssl/releases/download/openssl-3.5.7/openssl-3.5.7.tar.gz` |
| OpenSSL | `3.5.7 LTS` |
| OpenSSL archive SHA-256 | `a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8` |
| ABI | `arm64-v8a`（仅此一个） |
| Android API | `26` |
| STL | `c++_static` |
| 外部构建根 | `E:\tdlib-build\channel-video-flow` |

只接受该官方 remote、完整 commit 和已校验 OpenSSL archive。不得以“最新”“当前”替代上表中的固定版本号或 commit。

## 受控工具链

Task 1 已记录以下实际工具版本；详细逐行命令输出在 [TDLIB_PROVENANCE.md](../telegram/tdlib/TDLIB_PROVENANCE.md) 和 `.superpowers/sdd/task-1-report.md`。

| 工具 | 固定版本 |
|---|---|
| Android NDK | `23.2.8568313`（Clang/LLVM `12.0.9`） |
| Android CMake / Ninja | `3.22.1` / `1.10.2` |
| Android Studio JBR | OpenJDK `21.0.10` |
| MSYS2 runtime | `3.6.10-1` |
| UCRT64 GCC / G++ | `16.1.0` |
| UCRT64 CMake / Ninja | `4.4.0` / `1.13.2` |
| PHP | `8.5.8` |
| Git for Windows | `2.54.0.windows.1` |

## 构建入口、命令和退出证据

入库入口为：

- `tools/tdlib/build-android.ps1`
- `tools/tdlib/build-android-arm64.sh`

PowerShell 入口以实际 Task 3 使用的根目录调用方式如下（主机工具的绝对临时目录见 provenance，不应改写为未验证路径）：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\tdlib\build-android.ps1 `
  -HostGit 'C:\Program Files\Git\cmd\git.exe' `
  -MsysRoot "$env:LOCALAPPDATA\Temp\channel-video-flow-tdlib-toolchain\msys64-portable" `
  -MsysBash "$env:LOCALAPPDATA\Temp\channel-video-flow-tdlib-toolchain\msys64-portable\usr\bin\bash.exe" `
  -AndroidSdkRoot 'E:\AndroidStudio2.0' `
  -AndroidNdkRoot 'E:\AndroidStudio2.0\ndk\23.2.8568313' `
  -AndroidCMakeRoot 'E:\AndroidStudio2.0\cmake\3.22.1' `
  -PhpExe "$env:LOCALAPPDATA\Temp\channel-video-flow-tdlib-toolchain\php-8.5.8\php.exe" `
  -BuildRoot 'E:\tdlib-build\channel-video-flow'
```

已记录的 Task 3 证据：上面 native 构建命令 exit `0`、stderr 为空；`powershell` AST parse、`bash -n tools/tdlib/build-android-arm64.sh`、`llvm-readelf` 和 `gradlew.bat --offline :telegram:tdlib:assembleDebug` 也均 exit `0`。该历史证据不等同于本次主机或真机授权验证。

与上游 TDLib 的最小必要差异仅是受控编排脚本：先运行上游 `example/android/check-environment.sh`，以固定 OpenSSL 静态构建 host/Android 代码生成，调用上游 `tl_generate_java` 和 `AddIntDef.php`，再只构建 `tdjni`。脚本会验证 remote、HEAD、OpenSSL SHA、ABI/API/STL、CMake cache、源码清洁性及唯一 JNI 输出；不修改官方 TDLib 的 Client/Java API 语义。

### 上游生成源码的异常输出边界

固定提交生成的 `Client.java:20` 与 `TdApi.java:20` 在各自静态初始化块捕获 `UnsatisfiedLinkError` 后调用上游原样的 `e.printStackTrace()`。这两处不是本项目自定义日志，也未为了隐藏差异而修改，因此上表哈希仍能对应官方固定源码；不能宣称整个入库生成源码不存在 `printStackTrace()`。

生产接入通过 `OfficialTdLibBridge.load()` 在首次使用 `Client` 前显式执行 `System.loadLibrary("tdjni")`。`TelegramClientManager.start()` 捕获该预加载失败，只向仓库自定义 logger 和授权状态边界发送固定类别 `NATIVE_LIBRARY` / `NativeLibraryLoadFailed`，不转发异常消息、堆栈、路径或 TDLib 对象；失败后也不继续创建 Client。项目自定义 Kotlin/Java 代码不得直接调用上述生成类的异常输出，也不得把异常 message 写入 UI 或 `CVF/Auth`。若未来修改官方生成文件来移除此上游行为，必须记录补丁、重新计算哈希并重新执行供应链验证。

## 入库文件和哈希

| 文件 | 字节数 | SHA-256 |
|---|---:|---|
| `telegram/tdlib/src/main/java/org/drinkless/tdlib/Client.java` | 11,274 | `5b30cb91dc25eb26b5dd93622974cec7024a0d87d81714965ae0416054347b26` |
| `telegram/tdlib/src/main/java/org/drinkless/tdlib/TdApi.java` | 5,225,990 | `9a462ae179b8d8ff90bda85d56f6ac526d63ff56670f492bf8e86783fd5edc55` |
| `telegram/tdlib/src/main/jniLibs/arm64-v8a/libtdjni.so` | 26,610,816 | `7e07cd3b069639bb0c5db094d4cf081526d3c06db81b3ebe9f9bda65ddee84e3` |

`libtdjni.so` 的 ELF 形态为 AArch64 `DYN`，动态依赖仅为 `liblog.so`、`libdl.so`、`libz.so`、`libm.so` 和 `libc.so`；没有 `libc++_shared.so`。debug APK 的 `lib/` 只允许 `arm64-v8a` 目录，且 native 文件必须逐一来自已知来源：本项目固定构建的 `libtdjni.so`、Compose `ui-graphics 1.11.4` 传递解析的官方 AndroidX `androidx.graphics:graphics-path:1.0.1` 所提供的 `libandroidx.graphics.path.so`，以及稳定版 `androidx.datastore:datastore-core-android:1.2.1` 所提供的 `libdatastore_shared_counter.so`。阶段 8 已比对最后一项在 debug APK 与 Gradle 缓存官方 AAR 中的 ARM64 SHA-256，二者均为 `deed4546c8dafad0e68ea2c25e4c0a62ca97343614ae386b7ed2af6abb7fa999`。不允许其他 ABI、未知 native 文件或 `libc++_shared.so`。

可独立复算：

```powershell
Get-FileHash -Algorithm SHA256 `
  telegram/tdlib/src/main/java/org/drinkless/tdlib/Client.java, `
  telegram/tdlib/src/main/java/org/drinkless/tdlib/TdApi.java, `
  telegram/tdlib/src/main/jniLibs/arm64-v8a/libtdjni.so
& 'E:\AndroidStudio2.0\ndk\23.2.8568313\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe' `
  -h telegram/tdlib/src/main/jniLibs/arm64-v8a/libtdjni.so
```

## Gradle 打包与许可证

`telegram:tdlib` 只为 `arm64-v8a` 设置 ABI filter；应用 debug APK 的 ABI 列表须由 `aapt.exe list` 验证。TDLib 为 Boost Software License 1.0，副本为 `telegram/tdlib/licenses/LICENSE_1_0.txt`；OpenSSL 为 Apache License 2.0，副本为 `telegram/tdlib/licenses/LICENSE_OPENSSL.txt`。

详细机器生成来源、工具输出和构建命令以 [TDLIB_PROVENANCE.md](../telegram/tdlib/TDLIB_PROVENANCE.md) 为准。任何变更均须同时更新此文档、provenance、哈希和验证证据；不得下载或使用来源不明的 `.so`、`.jar` 或 `.aar`。
