# 阶段 2：官方 TDLib 与真实 Telegram 授权实施计划

> 历史说明：本文记录 Stage 2 的原始实施计划。仓库所有者于 2026-09-01 明确批准 Stage 24 用户自行配置版；公开 release 的凭证来源、Keystore 存储和客户端重建以 `docs/STAGE24_USER_CONFIGURED_CREDENTIALS.md` 为准。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax (- [ ]) for tracking.

**Goal:** 在 arm64-v8a 真机上集成由官方源码可复现构建的 TDLib，并完成手机号、验证码、两步验证密码、会话恢复、退出和安全错误处理。

**Architecture:** Compose 只调用 AuthViewModel；AuthViewModel 只依赖 TelegramAuthRepository；生产 Repository 通过 TelegramAuthClient 调用唯一 TelegramClientManager；manager 才能接触官方 TdApi 和 Client。TDLib 回调串行转换成应用自有事件和不可变 StateFlow，不直接修改 UI。

**Tech Stack:** Kotlin 2.3.0、Jetpack Compose/Material 3、Coroutines/Flow 1.11.0、Hilt 2.58、Gradle Kotlin DSL、官方 TDLib 1.8.66 Java/JNI、Android NDK 23.2.8568313、CMake 3.22.1、OpenSSL 3.5.7 LTS、JUnit 4、AndroidX Compose Test。

## Global Constraints

- 仓库根目录固定为 E:\Telegram Android Developer，分支 main，当前没有提交；所有既有未跟踪文件均视为用户成果。
- 包名和 namespace 保持 com.qixuan.channelvideoflow；minSdk=26、compileSdk=36.1、targetSdk=36。
- 目标真机为 vivo V2307A、Android 16、SDK 36；唯一 ABI 和 abiFilters 都是 arm64-v8a。
- TDLib 只从 https://github.com/tdlib/td.git 获取并固定到 022d60202e446ad1287b9fb68e687c8a0760788b。
- OpenSSL 固定 3.5.7 LTS，源码包 SHA-256 为 a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8。
- TDLib Java/JNI、OpenSSL、NDK、CMake、MSYS2 包版本、构建命令和产物 SHA-256 必须记录。
- 只构建 arm64-v8a、android-26、c++_static；APK 不得包含其他 ABI。
- 真实 TELEGRAM_API_ID 和 TELEGRAM_API_HASH 只能来自被 Git 忽略的根 local.properties。
- 不记录或持久化手机号、验证码、密码、API Hash、数据库 key、完整 TDLib 对象或原始 TDLib error.message。
- 验证码和密码由仓库所有者只在手机 UI 输入；不得通过聊天、ADB 输入、截图、日志或测试获取。
- TDLib 数据库位于 noBackupFilesDir，TDLib 文件位于内部 cacheDir；不得使用公共或外部存储。
- Manifest 继续只允许 INTERNET 与 ACCESS_NETWORK_STATE，且完全禁用/排除备份。
- 本阶段不创建频道、消息扫描、Room 业务库、标签、播放器、信息流、预加载或媒体缓存。
- 行为代码执行严格 RED → GREEN → REFACTOR；生成代码、Gradle 配置和 native 构建使用来源/哈希/构建 Proof。
- Proof 失败立即停止扩展，记录首个根因；禁止删测试、全局 suppress、跳过 lint 或假登录。
- 不自动 git add、commit、push。每个任务结束只展示 status/diff/checkpoint，提交需用户另行明确授权。

---

## 文件结构锁定

新增模块和职责：

- core/model：TelegramAuthState、TelegramAuthFailure、TelegramUnsupportedAuthStep。
- core/domain：TelegramAuthRepository。
- telegram/tdlib：官方生成 Client.java、TdApi.java、arm64-v8a/libtdjni.so 和许可证。
- telegram：TelegramAuthClient、TelegramClientManager、official bridge、状态/错误映射、Repository、目录、日志和 Hilt。
- app：BuildConfig 凭证适配、AuthViewModel、LoginScreen 和导航。

生产依赖：

    app -> core:model
    app -> core:domain
    app -> telegram
    core:domain -> core:model
    telegram -> core:model
    telegram -> core:domain
    telegram -> telegram:tdlib

telegram:tdlib 必须是 telegram 的 implementation 依赖，不能成为 app 的 api 依赖。

---

### Task 1: 保护基线并安装固定 native 工具链

**Files:**

- Inspect: AGENTS.md
- Inspect: README.md
- Inspect: docs/superpowers/specs/2026-07-23-tdlib-authentication-design.md
- Inspect: .gitignore
- Inspect: local.properties（只输出布尔状态，不输出值）
- No repository source change

**Interfaces:**

- Consumes: 已批准的方案 A、真机 arm64-v8a、Android SDK 根 E:\AndroidStudio2.0。
- Produces: 可调用的 MSYS2 UCRT64、NDK 23.2.8568313、CMake 3.22.1、Command-line Tools 和版本证据。

- [ ] **Step 1: 重读规则并捕获 Git/设备基线**

Run:

~~~powershell
git rev-parse --show-toplevel
git branch --show-current
git status --short --branch
E:\AndroidStudio2.0\platform-tools\adb.exe devices -l
E:\AndroidStudio2.0\platform-tools\adb.exe shell getprop ro.product.cpu.abi
E:\AndroidStudio2.0\platform-tools\adb.exe shell getprop ro.product.cpu.abilist
~~~

Expected:

- 根目录为 E:/Telegram Android Developer。
- 分支 main。
- 设备状态 device。
- 两个 ABI 命令都只返回 arm64-v8a。

- [ ] **Step 2: 只验证凭证边界**

Run:

~~~powershell
git check-ignore -v -- local.properties
git ls-files --error-unmatch -- local.properties
~~~

Expected:

- check-ignore 命中 .gitignore。
- ls-files 以非零退出，证明 local.properties 不在 index。
- 不运行 Get-Content 输出真实值。

- [ ] **Step 3: 安装 MSYS2**

Run:

~~~powershell
winget install --exact --id MSYS2.MSYS2 --accept-package-agreements --accept-source-agreements
~~~

Expected: winget exit 0，C:\msys64\usr\bin\bash.exe 存在。若安装器返回需要重启，停止任务并报告，不继续 native 安装。

- [ ] **Step 4: 更新 MSYS2 并安装 host 构建包**

Run first update:

~~~powershell
& C:\msys64\usr\bin\bash.exe -lc 'pacman -Syu --noconfirm'
~~~

重新打开 shell后运行：

~~~powershell
& C:\msys64\usr\bin\bash.exe -lc 'pacman -S --needed --noconfirm base-devel git make perl wget unzip mingw-w64-ucrt-x86_64-toolchain mingw-w64-ucrt-x86_64-cmake mingw-w64-ucrt-x86_64-ninja mingw-w64-ucrt-x86_64-gperf mingw-w64-ucrt-x86_64-php'
~~~

Expected: 所有包成功安装；若包名在当前 MSYS2 仓库变化，先用 pacman -Ss 精确查找同一官方包，不替换为第三方二进制。

- [ ] **Step 5: 安装 Android 官方组件**

在 Android Studio 执行：

1. Tools → SDK Manager → SDK Tools。
2. 勾选 Show Package Details。
3. 安装 Android SDK Command-line Tools (latest)。
4. 安装 NDK (Side by side) 23.2.8568313。
5. 安装 CMake 3.22.1。
6. 在 SDK Platforms 确保 Android 14 / android-34 存在，供官方 Java 文档生成脚本使用。

Expected directories:

~~~text
E:\AndroidStudio2.0\cmdline-tools\latest
E:\AndroidStudio2.0\ndk\23.2.8568313
E:\AndroidStudio2.0\cmake\3.22.1
E:\AndroidStudio2.0\platforms\android-34
~~~

- [ ] **Step 6: 记录工具版本**

Run:

~~~powershell
& E:\AndroidStudio2.0\cmdline-tools\latest\bin\sdkmanager.bat --list_installed
& E:\AndroidStudio2.0\cmake\3.22.1\bin\cmake.exe --version
& E:\AndroidStudio2.0\cmake\3.22.1\bin\ninja.exe --version
& E:\Android Studio\jbr\bin\java.exe -version
& C:\msys64\usr\bin\bash.exe -lc 'export PATH=/ucrt64/bin:/usr/bin:$PATH; gcc --version; g++ --version; gperf --version; php --version; perl --version; pacman -Q'
~~~

Expected: 命令全部 exit 0。只在后续 docs/TDLIB_BUILD.md 中记录直接相关包，不复制整个包清单。

- [ ] **Step 7: 基线 checkpoint**

Run:

~~~powershell
git status --short --branch
~~~

Expected: 除已批准设计/计划文档外没有新的仓库文件。不要提交。

---

### Task 2: 建立 core 授权合同和最小模块图

**Files:**

- Modify: settings.gradle.kts
- Modify: build.gradle.kts
- Modify: gradle/libs.versions.toml
- Create: core/model/build.gradle.kts
- Create: core/model/src/main/java/com/qixuan/channelvideoflow/model/auth/TelegramAuthFailure.kt
- Create: core/model/src/main/java/com/qixuan/channelvideoflow/model/auth/TelegramAuthState.kt
- Create: core/model/src/test/java/com/qixuan/channelvideoflow/model/auth/TelegramAuthStateTest.kt
- Create: core/domain/build.gradle.kts
- Create: core/domain/src/main/java/com/qixuan/channelvideoflow/domain/auth/TelegramAuthRepository.kt

**Interfaces:**

- Consumes: Kotlin 2.3.0、Coroutines 1.11.0。
- Produces: TelegramAuthRepository 和完全不含 TdApi/Android 类型的授权模型。

- [ ] **Step 1: 添加最小 Gradle 模块配置**

Add plugin aliases:

~~~toml
[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
~~~

Add root plugin declarations:

~~~kotlin
alias(libs.plugins.android.library) apply false
alias(libs.plugins.kotlin.jvm) apply false
~~~

Add settings entries:

~~~kotlin
include(":core:model")
include(":core:domain")
include(":telegram")
include(":telegram:tdlib")
~~~

Create core/model/build.gradle.kts:

~~~kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
~~~

Create core/domain/build.gradle.kts:

~~~kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
}
~~~

- [ ] **Step 2: 写授权模型失败测试**

Create TelegramAuthStateTest.kt:

~~~kotlin
package com.qixuan.channelvideoflow.model.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramAuthStateTest {
    @Test
    fun waitingCodeCanCarrySanitizedFailure() {
        val state = TelegramAuthState.WaitingCode(
            failure = TelegramAuthFailure.InvalidCode,
        )

        assertEquals(TelegramAuthFailure.InvalidCode, state.failure)
    }

    @Test
    fun freshWaitingPasswordHasNoFailure() {
        assertNull(TelegramAuthState.WaitingPassword().failure)
    }

    @Test
    fun floodWaitStoresOnlyRelativeSeconds() {
        assertEquals(
            42,
            TelegramAuthFailure.FloodWait(retryAfterSeconds = 42).retryAfterSeconds,
        )
    }
}
~~~

- [ ] **Step 3: 运行 RED**

Run:

~~~powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat :core:model:test
~~~

Expected: FAIL because TelegramAuthState and TelegramAuthFailure do not exist.

- [ ] **Step 4: 实现最小授权错误模型**

Create TelegramAuthFailure.kt:

~~~kotlin
package com.qixuan.channelvideoflow.model.auth

sealed interface TelegramAuthFailure {
    data object InvalidPhoneNumber : TelegramAuthFailure
    data object InvalidCode : TelegramAuthFailure
    data object InvalidPassword : TelegramAuthFailure
    data class FloodWait(val retryAfterSeconds: Int) : TelegramAuthFailure
    data object NetworkUnavailable : TelegramAuthFailure
    data object NativeLibraryLoadFailed : TelegramAuthFailure
    data object TdLibInitializationFailed : TelegramAuthFailure
    data object DatabaseFailed : TelegramAuthFailure
    data class RequestRejected(val code: Int) : TelegramAuthFailure
    data object Unknown : TelegramAuthFailure
}
~~~

- [ ] **Step 5: 实现完整授权状态模型**

Create TelegramAuthState.kt:

~~~kotlin
package com.qixuan.channelvideoflow.model.auth

enum class TelegramUnsupportedAuthStep {
    PREMIUM_PURCHASE,
    EMAIL_ADDRESS,
    EMAIL_CODE,
    OTHER_DEVICE_CONFIRMATION,
    REGISTRATION,
    UNKNOWN,
}

sealed interface TelegramAuthState {
    data class UnconfiguredCredentials(
        val invalidKeys: Set<String>,
    ) : TelegramAuthState

    data object Initializing : TelegramAuthState
    data class WaitingPhoneNumber(
        val failure: TelegramAuthFailure? = null,
    ) : TelegramAuthState

    data class WaitingCode(
        val failure: TelegramAuthFailure? = null,
    ) : TelegramAuthState

    data class WaitingPassword(
        val failure: TelegramAuthFailure? = null,
    ) : TelegramAuthState

    data class Authorized(
        val failure: TelegramAuthFailure? = null,
    ) : TelegramAuthState

    data object LoggingOut : TelegramAuthState
    data object Closing : TelegramAuthState
    data object Closed : TelegramAuthState

    data class Unsupported(
        val step: TelegramUnsupportedAuthStep,
    ) : TelegramAuthState

    data class FatalError(
        val failure: TelegramAuthFailure,
    ) : TelegramAuthState
}
~~~

- [ ] **Step 6: 创建 Repository 接口**

Create TelegramAuthRepository.kt:

~~~kotlin
package com.qixuan.channelvideoflow.domain.auth

import com.qixuan.channelvideoflow.model.auth.TelegramAuthState
import kotlinx.coroutines.flow.StateFlow

interface TelegramAuthRepository {
    val authState: StateFlow<TelegramAuthState>

    suspend fun start()
    suspend fun submitPhoneNumber(phoneNumber: String)
    suspend fun submitCode(code: String)
    suspend fun submitPassword(password: String)
    suspend fun logout()
}
~~~

- [ ] **Step 7: 运行 GREEN**

Run:

~~~powershell
.\gradlew.bat :core:model:test :core:domain:compileKotlin
~~~

Expected: BUILD SUCCESSFUL。

- [ ] **Step 8: 检查依赖边界**

Run:

~~~powershell
rg -n "org\.drinkless|androidx\.|android\." core
~~~

Expected: 无输出。然后运行 git status 和 git diff --check；不要提交。

---

### Task 3: 从固定官方源码构建并封装 TDLib

**Files:**

- Modify: .gitignore
- Create: tools/tdlib/build-android.ps1
- Create: tools/tdlib/build-android-arm64.sh
- Create: telegram/tdlib/build.gradle.kts
- Create: telegram/tdlib/src/main/AndroidManifest.xml
- Generate: telegram/tdlib/src/main/java/org/drinkless/tdlib/Client.java
- Generate: telegram/tdlib/src/main/java/org/drinkless/tdlib/TdApi.java
- Generate: telegram/tdlib/src/main/jniLibs/arm64-v8a/libtdjni.so
- Create: telegram/tdlib/TDLIB_PROVENANCE.md
- Copy: telegram/tdlib/licenses/LICENSE_1_0.txt
- Copy: telegram/tdlib/licenses/LICENSE_OPENSSL.txt

**Interfaces:**

- Consumes: Task 1 工具链、固定 TDLib/OpenSSL 版本和 arm64-v8a。
- Produces: implementation-only Android library :telegram:tdlib。

此任务属于官方生成代码和构建配置，不伪造 RED。Proof 是源码 SHA、下载 SHA、构建退出码、ELF/ABI、产物哈希和 Gradle 打包结果。

- [ ] **Step 1: 扩展忽略规则**

Append:

~~~gitignore
.tdlib-build/
tdlib-build/
*.tdlib-build.log
~~~

固定外部构建根 E:\tdlib-build 不属于仓库；不得误删其他目录。

- [ ] **Step 2: 编写 PowerShell orchestrator**

build-android.ps1 必须定义并验证这些常量：

~~~powershell
$TdLibRepository = 'https://github.com/tdlib/td.git'
$TdLibCommit = '022d60202e446ad1287b9fb68e687c8a0760788b'
$TdLibVersion = '1.8.66'
$OpenSslVersion = '3.5.7'
$OpenSslSha256 = 'a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8'
$AndroidSdkRoot = 'E:\AndroidStudio2.0'
$AndroidNdkVersion = '23.2.8568313'
$CMakeVersion = '3.22.1'
$TargetAbi = 'arm64-v8a'
$AndroidApi = '26'
$BuildRoot = 'E:\tdlib-build\channel-video-flow'
$MsysBash = 'C:\msys64\usr\bin\bash.exe'
~~~

The script must:

1. Resolve every tool/file path and fail before cloning if one is missing.
2. Clone with git clone --filter=blob:none --no-checkout from the exact official remote.
3. Fetch and checkout only the fixed commit.
4. verify git remote get-url origin and git rev-parse HEAD exactly.
5. Download the official OpenSSL release asset.
6. Verify Get-FileHash -Algorithm SHA256 before extraction.
7. Invoke build-android-arm64.sh through MSYS2 UCRT64.
8. Copy only Client.java, TdApi.java and arm64-v8a/libtdjni.so.
9. Fail if any other ABI directory exists under telegram/tdlib/src/main/jniLibs.
10. Print only paths, versions and hashes; never print local.properties.

- [ ] **Step 3: 编写单 ABI Bash 构建脚本**

The checked-in script must use:

~~~bash
set -euo pipefail
TARGET_ABI=arm64-v8a
ANDROID_API=26
ANDROID_NDK_VERSION=23.2.8568313
CMAKE_VERSION=3.22.1
TDLIB_INTERFACE=Java
ANDROID_STL=c++_static
~~~

It must run the fixed source copy of official example/android/check-environment.sh, build OpenSSL 3.5.7 only for android-arm64, generate Java through tl_generate_java and AddIntDef.php, then configure TDLib with:

~~~bash
cmake \
  -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
  -DOPENSSL_ROOT_DIR="$OPENSSL_INSTALL_DIR/$TARGET_ABI" \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DANDROID_ABI="$TARGET_ABI" \
  -DANDROID_STL="$ANDROID_STL" \
  -DANDROID_PLATFORM="android-$ANDROID_API" \
  "$TDLIB_SOURCE"

cmake --build . --target tdjni
~~~

No loop over x86, x86_64 or armeabi-v7a is permitted.

- [ ] **Step 4: 创建 :telegram:tdlib module**

Create build.gradle.kts:

~~~kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.drinkless.tdlib.android"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
}
~~~

Create manifest:

~~~xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
~~~

- [ ] **Step 5: 执行官方源码构建**

Run:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\tools\tdlib\build-android.ps1
~~~

Expected:

- exact TDLib commit verified。
- OpenSSL SHA verified。
- only arm64-v8a built。
- Client.java、TdApi.java、libtdjni.so copied。
- command exit 0。

若失败，保存首个 host generator、OpenSSL、CMake、linker 或 copy 错误并停止 Task 3。

- [ ] **Step 6: 验证产物**

Run:

~~~powershell
Get-FileHash -Algorithm SHA256 .\telegram\tdlib\src\main\java\org\drinkless\tdlib\Client.java
Get-FileHash -Algorithm SHA256 .\telegram\tdlib\src\main\java\org\drinkless\tdlib\TdApi.java
Get-FileHash -Algorithm SHA256 .\telegram\tdlib\src\main\jniLibs\arm64-v8a\libtdjni.so
Get-ChildItem .\telegram\tdlib\src\main\jniLibs -Directory
.\gradlew.bat :telegram:tdlib:assembleDebug
~~~

Expected: 一个 ABI 目录、三个哈希、Gradle BUILD SUCCESSFUL。

- [ ] **Step 7: 来源与许可证 checkpoint**

TDLIB_PROVENANCE.md must record exact source URL, commit, TDLib version, OpenSSL URL/version/SHA, NDK/CMake/JDK/MSYS2 direct package versions, command, target ABI/API, output hashes and licenses. Run git diff --check；不要提交。

---

### Task 4: TDD 授权状态和错误映射

**Files:**

- Create: telegram/build.gradle.kts
- Create: telegram/src/main/AndroidManifest.xml
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/client/TelegramClientModels.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/auth/TdLibAuthorizationStateMapper.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/auth/TdLibAuthErrorMapper.kt
- Test: telegram/src/test/java/com/qixuan/channelvideoflow/telegram/auth/TdLibAuthorizationStateMapperTest.kt
- Test: telegram/src/test/java/com/qixuan/channelvideoflow/telegram/auth/TdLibAuthErrorMapperTest.kt

**Interfaces:**

- Consumes: generated TdApi、TelegramAuthFailure、TelegramUnsupportedAuthStep。
- Produces: TelegramClientAuthorizationState、TelegramAuthRequest、sanitized mapper。

- [ ] **Step 1: 创建 telegram module 配置**

Add library:

~~~toml
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
~~~

Create telegram/build.gradle.kts:

~~~kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.qixuan.channelvideoflow.telegram"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    implementation(project(":telegram:tdlib"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
~~~

Create empty manifest without permissions.

- [ ] **Step 2: 写 13 状态 RED 测试**

The state mapper test must instantiate all of:

~~~kotlin
TdApi.AuthorizationStateWaitTdlibParameters()
TdApi.AuthorizationStateWaitPhoneNumber()
TdApi.AuthorizationStateWaitCode()
TdApi.AuthorizationStateWaitPassword()
TdApi.AuthorizationStateReady()
TdApi.AuthorizationStateLoggingOut()
TdApi.AuthorizationStateClosing()
TdApi.AuthorizationStateClosed()
TdApi.AuthorizationStateWaitPremiumPurchase()
TdApi.AuthorizationStateWaitEmailAddress()
TdApi.AuthorizationStateWaitEmailCode()
TdApi.AuthorizationStateWaitOtherDeviceConfirmation()
TdApi.AuthorizationStateWaitRegistration()
~~~

Assert the first eight map to their exact client state and the last five map to exact TelegramUnsupportedAuthStep enum values.

- [ ] **Step 3: 写错误 RED 测试**

Required assertions:

~~~kotlin
assertEquals(
    TelegramAuthFailure.FloodWait(42),
    TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 429, "FLOOD_WAIT_42"),
)
assertEquals(
    TelegramAuthFailure.InvalidPhoneNumber,
    TdLibAuthErrorMapper.map(TelegramAuthRequest.PHONE_NUMBER, 400, "PHONE_NUMBER_INVALID"),
)
assertEquals(
    TelegramAuthFailure.InvalidCode,
    TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 400, "PHONE_CODE_INVALID"),
)
assertEquals(
    TelegramAuthFailure.InvalidPassword,
    TdLibAuthErrorMapper.map(TelegramAuthRequest.PASSWORD, 400, "PASSWORD_HASH_INVALID"),
)
assertEquals(
    TelegramAuthFailure.RequestRejected(418),
    TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 418, "synthetic sensitive detail"),
)
~~~

- [ ] **Step 4: 运行 RED**

Run:

~~~powershell
.\gradlew.bat :telegram:testDebugUnitTest
~~~

Expected: FAIL because client models and mappers do not exist.

- [ ] **Step 5: 实现 client-owned models**

TelegramClientModels.kt:

~~~kotlin
package com.qixuan.channelvideoflow.telegram.client

import com.qixuan.channelvideoflow.model.auth.TelegramUnsupportedAuthStep

enum class TelegramAuthRequest {
    PARAMETERS,
    PHONE_NUMBER,
    CODE,
    PASSWORD,
    LOG_OUT,
    CLOSE,
}

sealed interface TelegramClientAuthorizationState {
    data object WaitTdlibParameters : TelegramClientAuthorizationState
    data object WaitPhoneNumber : TelegramClientAuthorizationState
    data object WaitCode : TelegramClientAuthorizationState
    data object WaitPassword : TelegramClientAuthorizationState
    data object Ready : TelegramClientAuthorizationState
    data object LoggingOut : TelegramClientAuthorizationState
    data object Closing : TelegramClientAuthorizationState
    data object Closed : TelegramClientAuthorizationState
    data class Unsupported(
        val step: TelegramUnsupportedAuthStep,
    ) : TelegramClientAuthorizationState
}

sealed interface TelegramClientEvent {
    data class CredentialsUnavailable(
        val invalidKeys: Set<String>,
    ) : TelegramClientEvent

    data class AuthorizationStateChanged(
        val state: TelegramClientAuthorizationState,
    ) : TelegramClientEvent

    data class RequestFailed(
        val request: TelegramAuthRequest,
        val code: Int,
        internal val rawMessage: String,
    ) : TelegramClientEvent

    data class FatalFailure(
        val category: FatalCategory,
    ) : TelegramClientEvent
}

enum class FatalCategory {
    NATIVE_LIBRARY,
    INITIALIZATION,
    DATABASE,
}
~~~

- [ ] **Step 6: 实现状态 mapper**

Use exhaustive when over concrete TdApi classes. Unsupported mapping must be:

~~~kotlin
TdApi.AuthorizationStateWaitPremiumPurchase::class -> PREMIUM_PURCHASE
TdApi.AuthorizationStateWaitEmailAddress::class -> EMAIL_ADDRESS
TdApi.AuthorizationStateWaitEmailCode::class -> EMAIL_CODE
TdApi.AuthorizationStateWaitOtherDeviceConfirmation::class -> OTHER_DEVICE_CONFIRMATION
TdApi.AuthorizationStateWaitRegistration::class -> REGISTRATION
~~~

The mapper must throw no raw object and expose no TdApi type outside telegram.

- [ ] **Step 7: 实现 error mapper**

Implementation rules:

~~~kotlin
private val floodWaitPattern = Regex("^FLOOD_WAIT_([0-9]+)$")
~~~

- Parse only positive Int seconds and clamp to Int.MAX_VALUE.
- Map exact symbolic codes listed in tests.
- Map code 0 and strings NETWORK_ERROR/REQUEST_ABORTED to NetworkUnavailable.
- Return RequestRejected(code) for every other input.
- Never retain or return rawMessage.

- [ ] **Step 8: 运行 GREEN**

Run:

~~~powershell
.\gradlew.bat :telegram:testDebugUnitTest
~~~

Expected: all mapper tests pass. Then search:

~~~powershell
rg -n "rawMessage|TdApi" core app
~~~

Expected: no TdApi in core/app；rawMessage only inside telegram infrastructure/tests。

---

### Task 5: TDD Repository 授权状态机

**Files:**

- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/client/TelegramAuthClient.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/auth/TdLibTelegramAuthRepository.kt
- Test: telegram/src/test/java/com/qixuan/channelvideoflow/telegram/auth/FakeTelegramAuthClient.kt
- Test: telegram/src/test/java/com/qixuan/channelvideoflow/telegram/auth/TdLibTelegramAuthRepositoryTest.kt

**Interfaces:**

- Consumes: TelegramClientEvent and TelegramAuthRepository。
- Produces: StateFlow-driven Repository with logout restart and no TdApi types。

- [ ] **Step 1: 定义 wished-for client interface in test**

Tests assume:

~~~kotlin
interface TelegramAuthClient {
    val events: Flow<TelegramClientEvent>
    suspend fun start()
    suspend fun submitPhoneNumber(phoneNumber: String)
    suspend fun submitCode(code: String)
    suspend fun submitPassword(password: String)
    suspend fun logout()
}
~~~

Fake implementation uses MutableSharedFlow with extraBufferCapacity 16 and only synthetic inputs.

- [ ] **Step 2: 写 Repository RED 状态序列**

Use runTest and backgroundScope. Required tests:

1. initial state is Initializing。
2. CredentialsUnavailable maps exact invalid key names。
3. WaitPhoneNumber/WaitCode/WaitPassword/Ready map exactly。
4. wrong code produces WaitingCode(InvalidCode)。
5. wrong password produces WaitingPassword(InvalidPassword)。
6. FLOOD_WAIT remains on the current input state。
7. unsupported states never become Authorized。
8. LoggingOut → Closing → Closed are observable in order。
9. Closed after requested logout calls client.start exactly once and a later WaitPhoneNumber returns login state。
10. submit methods delegate once to the fake client。

- [ ] **Step 3: 运行 RED**

Run:

~~~powershell
.\gradlew.bat :telegram:testDebugUnitTest --tests "*TdLibTelegramAuthRepositoryTest"
~~~

Expected: FAIL because interface/repository do not exist.

- [ ] **Step 4: 实现 TelegramAuthClient**

Create the exact interface from Step 1 in production source. It remains in telegram module and does not expose TdApi.

- [ ] **Step 5: 实现 Repository**

Constructor:

~~~kotlin
class TdLibTelegramAuthRepository(
    private val client: TelegramAuthClient,
    private val scope: CoroutineScope,
) : TelegramAuthRepository
~~~

Production/Hilt secondary construction can be provided by a module later. Required fields:

~~~kotlin
private val mutableAuthState =
    MutableStateFlow<TelegramAuthState>(TelegramAuthState.Initializing)
override val authState: StateFlow<TelegramAuthState> = mutableAuthState.asStateFlow()
private var collectionJob: Job? = null
private var logoutRequested = false
~~~

start must install the event collector before calling client.start and be idempotent. RequestFailed must call TdLibAuthErrorMapper and attach the failure only to the matching current input state. LOG_OUT failure returns Authorized(failure) instead of claiming logout. Closed after logout sets Closed, clears logoutRequested, then calls client.start exactly once.

- [ ] **Step 6: 运行 GREEN and full telegram tests**

Run:

~~~powershell
.\gradlew.bat :telegram:testDebugUnitTest
~~~

Expected: all state mapper, error mapper and repository tests pass.

- [ ] **Step 7: Repository boundary checkpoint**

Run:

~~~powershell
rg -n "TdApi|org\.drinkless" core app
git diff --check
git status --short
~~~

Expected: no TDLib types outside telegram；不要提交。

---

### Task 6: TDD TelegramClientManager、官方 bridge 和私有目录

**Files:**

- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/config/TelegramCredentials.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/storage/TdLibDirectories.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/client/TdLibBridge.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/client/OfficialTdLibBridge.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/client/TelegramClientManager.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/logging/AuthEventLogger.kt
- Test: telegram/src/test/java/com/qixuan/channelvideoflow/telegram/client/FakeTdLibBridge.kt
- Test: telegram/src/test/java/com/qixuan/channelvideoflow/telegram/client/TelegramClientManagerTest.kt
- Test: telegram/src/androidTest/java/com/qixuan/channelvideoflow/telegram/TdLibNativeSmokeTest.kt

**Interfaces:**

- Consumes: TelegramAuthClient、generated Client/TdApi、Android private directories。
- Produces: unique lifecycle owner, serialized callbacks, real TDLib requests and sanitized logs。

- [ ] **Step 1: 写 manager RED tests with fake bridge**

FakeTdLibBridge must never load JNI. It records:

- load call count。
- create call count。
- all sent TdApi.Function subclasses。
- update callback。
- result callbacks by request。

Required tests:

1. missing credentials emits CredentialsUnavailable and never loads native。
2. two start calls create exactly one Client。
3. WaitTdlibParameters sends exactly one SetTdlibParameters for one state generation。
4. duplicate callback for the same state object does not send parameters twice。
5. submitPhoneNumber sends SetAuthenticationPhoneNumber。
6. submitCode sends CheckAuthenticationCode。
7. submitPassword sends CheckAuthenticationPassword。
8. logout sends LogOut。
9. TdApi.Error becomes RequestFailed without logging raw message。
10. AuthorizationStateClosed clears the current session so next start creates a new one。

- [ ] **Step 2: 运行 RED**

Run:

~~~powershell
.\gradlew.bat :telegram:testDebugUnitTest --tests "*TelegramClientManagerTest"
~~~

Expected: FAIL because bridge, credentials, directories and manager do not exist.

- [ ] **Step 3: 实现 credential result with redacted toString**

TelegramCredentials.kt:

~~~kotlin
package com.qixuan.channelvideoflow.telegram.config

class TelegramCredentials(
    val apiId: Int,
    val apiHash: String,
) {
    override fun toString(): String = "TelegramCredentials(REDACTED)"
}

sealed interface TelegramCredentialsResult {
    data class Available(
        val credentials: TelegramCredentials,
    ) : TelegramCredentialsResult

    data class Unavailable(
        val invalidKeys: Set<String>,
    ) : TelegramCredentialsResult
}

fun interface TelegramCredentialsProvider {
    fun get(): TelegramCredentialsResult
}
~~~

- [ ] **Step 4: 实现私有目录**

TdLibDirectories must use @ApplicationContext and expose only:

~~~kotlin
val databaseDirectory: File =
    File(context.noBackupFilesDir, "tdlib/database")
val filesDirectory: File =
    File(context.cacheDir, "tdlib/files")
~~~

ensureCreated calls mkdirs and verifies canonical paths remain under their expected private roots. Failure throws a custom internal exception without embedding the absolute path in its message.

- [ ] **Step 5: 实现 bridge**

TdLibBridge:

~~~kotlin
internal interface TdLibSession {
    fun send(
        function: TdApi.Function,
        result: (TdApi.Object) -> Unit,
    )
}

internal interface TdLibBridge {
    fun load()
    fun configureLogHandler(logger: AuthEventLogger)
    fun create(
        onUpdate: (TdApi.Object) -> Unit,
        onException: () -> Unit,
    ): TdLibSession
}
~~~

OfficialTdLibBridge:

- load explicitly calls System.loadLibrary("tdjni") before referencing Client。
- configureLogHandler calls Client.setLogMessageHandler(1) but discards message text。
- create wraps Client.create。
- exceptions are reduced to onException() without exception.message。

- [ ] **Step 6: 实现 safe logger**

AuthEventLogger interface:

~~~kotlin
interface AuthEventLogger {
    fun state(name: String)
    fun request(name: String)
    fun failure(category: String, code: Int)
    fun nativeLevel(level: Int)
}
~~~

Android implementation logs only fixed names/numbers under tag CVF/Auth when telegram BuildConfig.DEBUG is true. It accepts no user input or raw error string.

- [ ] **Step 7: 实现 manager**

TelegramClientManager must:

- be @Singleton and implement TelegramAuthClient。
- own one single-thread ExecutorCoroutineDispatcher named cvf-tdlib-events。
- expose events with SharedFlow, no TdApi。
- verify credentials before bridge.load。
- create directories before SetTdlibParameters。
- set useTestDc=false, useFileDatabase=true, useChatInfoDatabase=true, useMessageDatabase=true, useSecretChats=false。
- use empty ByteArray databaseEncryptionKey。
- get system language via Locale.getDefault().toLanguageTag()。
- use Build.MODEL, Build.VERSION.RELEASE and PackageManager versionName。
- emit state names and request names only。
- never log phoneNumber/code/password。
- set current session null only on Closed。

- [ ] **Step 8: 运行 GREEN**

Run:

~~~powershell
.\gradlew.bat :telegram:testDebugUnitTest
~~~

Expected: all manager and previous telegram tests pass.

- [ ] **Step 9: 写并运行 JNI smoke test**

Test body:

~~~kotlin
@Test
fun officialTdLibLoadsAndReportsPinnedVersion() {
    System.loadLibrary("tdjni")
    val value = Client.execute<TdApi.OptionValue>(
        TdApi.GetOption("version"),
    )
    assertTrue(value is TdApi.OptionValueString)
    assertEquals("1.8.66", (value as TdApi.OptionValueString).value)
}
~~~

Run:

~~~powershell
.\gradlew.bat :telegram:connectedDebugAndroidTest
~~~

Expected: physical arm64-v8a device test passes. If reported version differs, fail and stop; do not edit expected value to match an unpinned artifact.

---

### Task 7: TDD BuildConfig 凭证、Hilt 和 Repository wiring

**Files:**

- Modify: app/src/main/java/com/qixuan/channelvideoflow/config/BuildConfigTelegramCredentialStatusProvider.kt
- Modify: app/src/main/java/com/qixuan/channelvideoflow/di/ConfigurationModule.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/di/TelegramModule.kt
- Create: telegram/src/main/java/com/qixuan/channelvideoflow/telegram/di/TelegramCoroutineModule.kt
- Modify: app/build.gradle.kts
- Test: app/src/test/java/com/qixuan/channelvideoflow/config/TelegramCredentialsProviderTest.kt

**Interfaces:**

- Consumes: existing TelegramCredentialEvaluator and telegram TelegramCredentialsProvider。
- Produces: Hilt can create one manager and inject TelegramAuthRepository while UI cannot access credentials。

- [ ] **Step 1: 写 credential adapter RED test**

Extract a pure function:

~~~kotlin
fun buildTelegramCredentialsResult(
    apiId: String,
    apiHash: String,
): TelegramCredentialsResult
~~~

Tests:

- blanks return Unavailable with both key names。
- invalid ID returns only TELEGRAM_API_ID。
- invalid hash returns only TELEGRAM_API_HASH。
- synthetic valid values return Available。
- result.toString does not contain the synthetic hash。

- [ ] **Step 2: 运行 RED**

Run:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TelegramCredentialsProviderTest"
~~~

Expected: FAIL because adapter function/provider interface implementation is missing.

- [ ] **Step 3: 实现 adapter**

BuildConfigTelegramCredentialStatusProvider continues serving the existing safe status interface and also implements TelegramCredentialsProvider. get() calls the pure function using BuildConfig fields. No logging or exception contains values.

- [ ] **Step 4: 添加 app module dependencies**

Add:

~~~kotlin
implementation(project(":core:model"))
implementation(project(":core:domain"))
implementation(project(":telegram"))
testImplementation(libs.kotlinx.coroutines.test)
~~~

Do not add project(":telegram:tdlib") directly.

- [ ] **Step 5: 实现 Hilt bindings**

ConfigurationModule binds BuildConfigTelegramCredentialStatusProvider to both TelegramCredentialStatusProvider and TelegramCredentialsProvider。

TelegramModule binds:

~~~kotlin
TelegramClientManager -> TelegramAuthClient
TdLibTelegramAuthRepository -> TelegramAuthRepository
OfficialTdLibBridge -> TdLibBridge
AndroidAuthEventLogger -> AuthEventLogger
~~~

TelegramCoroutineModule provides:

~~~kotlin
@Singleton
@TelegramApplicationScope
fun provideTelegramApplicationScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
~~~

Repository production constructor uses this qualified scope.

- [ ] **Step 6: 运行 GREEN and Hilt compile**

Run:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin
~~~

Expected: tests pass and Hilt graph compiles.

- [ ] **Step 7: DI boundary scan**

Run:

~~~powershell
rg -n "TdApi|org\.drinkless" app core
~~~

Expected: no output。

---

### Task 8: TDD AuthViewModel 与 FakeTelegramAuthRepository

**Files:**

- Modify: app/src/main/java/com/qixuan/channelvideoflow/feature/auth/LoginUiState.kt
- Create: app/src/main/java/com/qixuan/channelvideoflow/feature/auth/AuthViewModel.kt
- Create: app/src/test/java/com/qixuan/channelvideoflow/feature/auth/FakeTelegramAuthRepository.kt
- Replace: app/src/test/java/com/qixuan/channelvideoflow/feature/auth/LoginUiStateTest.kt
- Create: app/src/test/java/com/qixuan/channelvideoflow/feature/auth/AuthViewModelTest.kt

**Interfaces:**

- Consumes: TelegramAuthRepository StateFlow。
- Produces: immutable LoginUiState and typed UI intents with no persistence。

- [ ] **Step 1: 定义 wished-for UI state in tests**

~~~kotlin
enum class LoginStep {
    UNCONFIGURED,
    INITIALIZING,
    PHONE_NUMBER,
    CODE,
    PASSWORD,
    AUTHORIZED,
    LOGGING_OUT,
    CLOSING,
    UNSUPPORTED,
    FATAL_ERROR,
}

data class LoginUiState(
    val step: LoginStep,
    val input: String = "",
    val invalidKeys: Set<String> = emptySet(),
    val failure: TelegramAuthFailure? = null,
    val unsupportedStep: TelegramUnsupportedAuthStep? = null,
    val retrySecondsRemaining: Int = 0,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = input.isNotBlank() &&
            retrySecondsRemaining == 0 &&
            !isSubmitting &&
            step in setOf(LoginStep.PHONE_NUMBER, LoginStep.CODE, LoginStep.PASSWORD)

    val canLogout: Boolean
        get() = step == LoginStep.AUTHORIZED && !isSubmitting
}
~~~

- [ ] **Step 2: 写 ViewModel RED tests**

Fake repository uses MutableStateFlow and records only synthetic test values in memory. Required tests:

1. init calls repository.start once。
2. each domain state maps to exact LoginStep。
3. changing TDLib step clears previous input。
4. submit code clears input immediately after delegation。
5. submit password clears input immediately after delegation。
6. Authorized clears every input reference。
7. wrong phone may retain editable phone input, but wrong code/password input remains cleared。
8. FloodWait(2) disables submit, reports 2→1→0 under runTest virtual time, then re-enables。
9. logout delegates once and disables duplicate click。
10. no SavedStateHandle constructor dependency exists。

- [ ] **Step 3: 运行 RED**

Run:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AuthViewModelTest" --tests "*LoginUiStateTest"
~~~

Expected: FAIL because new state/ViewModel do not exist.

- [ ] **Step 4: 实现 LoginUiState**

Use the exact model in Step 1. Do not add phone/code/password-specific persisted fields.

- [ ] **Step 5: 实现 AuthViewModel**

Constructor:

~~~kotlin
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: TelegramAuthRepository,
) : ViewModel()
~~~

Public surface:

~~~kotlin
val uiState: StateFlow<LoginUiState>
fun onInputChanged(value: String)
fun submit()
fun retryStart()
fun logout()
~~~

Implementation requirements:

- init starts collection and calls repository.start in viewModelScope。
- submit snapshots the current String only for the matching repository call。
- CODE/PASSWORD inputs are replaced with empty String before suspension。
- every new auth step clears incompatible input。
- catch blocks rethrow CancellationException。
- no log calls。
- FloodWait countdown uses delay(1000), so kotlinx-coroutines-test controls it。

- [ ] **Step 6: 运行 GREEN**

Run:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest
~~~

Expected: all existing credential tests and new ViewModel tests pass.

- [ ] **Step 7: persistence scan**

Run:

~~~powershell
rg -n "SavedStateHandle|DataStore|Room|SharedPreferences|rememberSaveable" app\src\main\java\com\qixuan\channelvideoflow\feature\auth
~~~

Expected: no output。

---

### Task 9: TDD 真实 Compose 登录 UI 与导航

**Files:**

- Modify: app/src/main/java/com/qixuan/channelvideoflow/feature/auth/LoginScreen.kt
- Modify: app/src/main/java/com/qixuan/channelvideoflow/navigation/ChannelVideoFlowNavHost.kt
- Modify: app/src/main/res/values/strings.xml
- Delete: app/src/main/java/com/qixuan/channelvideoflow/feature/startup/StartupScreen.kt
- Delete: app/src/main/java/com/qixuan/channelvideoflow/feature/startup/StartupUiState.kt
- Delete: app/src/main/java/com/qixuan/channelvideoflow/feature/startup/StartupViewModel.kt
- Delete: app/src/test/java/com/qixuan/channelvideoflow/feature/startup/StartupUiStateFactoryTest.kt
- Create: app/src/androidTest/java/com/qixuan/channelvideoflow/feature/auth/LoginScreenTest.kt

**Interfaces:**

- Consumes: LoginUiState and AuthViewModel intents。
- Produces: real phone/code/password UI, authorized/logout UI and explicit failures。

- [ ] **Step 1: 写 Compose RED tests**

Call pure LoginScreen directly with state/callbacks; do not mock TDLib or Hilt. Required tests:

1. PHONE_NUMBER shows only phone field and submit。
2. CODE shows only code field。
3. PASSWORD uses PasswordVisualTransformation。
4. INITIALIZING and LOGGING_OUT show progress and no editable field。
5. AUTHORIZED shows logout and no channel/feed content。
6. UNCONFIGURED shows only invalid configuration key names。
7. unsupported states show the fixed unsupported message。
8. InvalidCode/InvalidPassword/FloodWait show sanitized Chinese text。
9. submit callback receives UI intent but test never uses real secrets。

- [ ] **Step 2: 运行 RED**

Run:

~~~powershell
.\gradlew.bat :app:connectedDebugAndroidTest
~~~

Expected: FAIL because current LoginScreen is the stage 1 placeholder.

- [ ] **Step 3: 实现 LoginRoute**

~~~kotlin
@Composable
fun LoginRoute(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        uiState = uiState,
        onInputChanged = viewModel::onInputChanged,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retryStart,
        onLogout = viewModel::logout,
    )
}
~~~

- [ ] **Step 4: 实现 LoginScreen**

Use Scaffold and one centered Column. Input rules:

- phone: KeyboardType.Phone, visible text, no auto-fill persistence API。
- code: KeyboardType.NumberPassword。
- password: KeyboardType.Password and PasswordVisualTransformation。
- buttons use uiState.canSubmit/canLogout。
- CircularProgressIndicator for initialization/submission/logout/closing。
- Text resolves failure enum to fixed Chinese resources；never render raw exception。

- [ ] **Step 5: 替换导航并清理 placeholder**

ChannelVideoFlowNavHost must render only LoginRoute as current stage destination. Delete the old startup placeholder files only after LoginScreen tests compile and the new route is wired. Do not create channel/feed destinations.

- [ ] **Step 6: 添加固定中文 resources**

Add strings for:

- 未配置开发凭证、初始化中、手机号、验证码、两步验证密码、提交、重试、已登录、退出登录、退出中、关闭中。
- 手机号错误、验证码错误、密码错误、网络错误、FLOOD_WAIT 剩余秒数、native/初始化/数据库错误、不支持当前授权步骤、新账号注册不支持。

No resource may contain a real credential or sample that looks usable.

- [ ] **Step 7: 运行 GREEN**

Run:

~~~powershell
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:testDebugUnitTest
~~~

Expected: Compose auth tests and JVM tests pass.

- [ ] **Step 8: UI boundary scan**

Run:

~~~powershell
rg -n "TdApi|Client\.create|Room|ExoPlayer|Media3" app\src\main\java\com\qixuan\channelvideoflow\feature\auth
~~~

Expected: no output。

---

### Task 10: 文档、供应链、权限、备份和日志安全 Proof

**Files:**

- Modify: README.md
- Modify: docs/ARCHITECTURE.md
- Modify: docs/DEVELOPMENT_PLAN.md
- Modify: docs/ACCEPTANCE_TESTS.md
- Create: docs/TDLIB_BUILD.md
- Verify: app/src/main/AndroidManifest.xml
- Verify: app/src/main/res/xml/data_extraction_rules.xml
- Verify: app/src/main/res/xml/backup_rules.xml

**Interfaces:**

- Consumes: actual Task 1/3 tool and artifact output。
- Produces: exact reproducibility/security record, no claimed manual success。

- [ ] **Step 1: 更新版本和阶段记录**

README and docs must state:

- current phase 2 merges old phases 2-4 by explicit owner instruction。
- TDLib 1.8.66 and exact 40-char commit。
- OpenSSL 3.5.7 LTS and SHA。
- NDK/CMake/ABI/build command。
- only auth scope implemented。
- manual login/restart/logout remain 尚未验证 until Task 11。

- [ ] **Step 2: 写 docs/TDLIB_BUILD.md from actual evidence**

Record exact:

- official URLs。
- full commit and git remote。
- tool versions captured in Task 1。
- command lines and exit codes。
- Java/SO hashes captured in Task 3。
- file locations and APK ABI check。
- TDLib Boost Software License 1.0 and OpenSSL Apache License 2.0。
- any minimal build-script delta from upstream。

Do not use prose like latest/current without the fixed number next to it.

- [ ] **Step 3: merged manifest check**

Run:

~~~powershell
.\gradlew.bat :app:processDebugMainManifest
$manifest = Get-ChildItem -Recurse -Filter AndroidManifest.xml .\app\build\intermediates\merged_manifest | Select-Object -First 1
Select-String -LiteralPath $manifest.FullName -Pattern 'uses-permission|allowBackup|dataExtractionRules|fullBackupContent'
~~~

Expected:

- only INTERNET and ACCESS_NETWORK_STATE uses-permission。
- allowBackup=false。
- both backup rule resources referenced。

- [ ] **Step 4: APK ABI check**

Run:

~~~powershell
.\gradlew.bat :app:assembleDebug
& E:\AndroidStudio2.0\build-tools\36.0.0\aapt.exe list .\app\build\outputs\apk\debug\app-debug.apk | Select-String '^lib/'
~~~

Expected: only `arm64-v8a` under `lib/`; enumerate every native file. The fixed TDLib `libtdjni.so` and Compose `ui-graphics 1.11.4` transitive official AndroidX `androidx.graphics:graphics-path:1.0.1` `libandroidx.graphics.path.so` are permitted. No other ABI, unknown native file, or `libc++_shared.so` is permitted.

- [ ] **Step 5: sensitive-source scan**

Run searches excluding local.properties and build directories:

~~~powershell
rg -n -uu -g '!local.properties' -g '!**/build/**' -g '!**/.gradle/**' -g '!**/.git/**' 'Log\.(v|d|i|w|e)\([^\\n]*(phone|code|password|apiHash|api_hash)|printStackTrace|exception\.message|rawMessage' .
rg -n -uu -g '!local.properties' -g '!**/build/**' -g '!**/.gradle/**' -g '!**/.git/**' '[0-9a-fA-F]{32}' .
~~~

Review every match. Allowed matches are documented synthetic test constants or the published OpenSSL SHA; no real API Hash may appear.

- [ ] **Step 6: full host verification**

Run fresh:

~~~powershell
$env:JAVA_HOME = 'E:\Android Studio\jbr'
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
~~~

Expected: all three commands exit 0. On failure stop, isolate the first causal task and rerun the full set after the fix.

- [ ] **Step 7: checkpoint**

Run:

~~~powershell
git diff --check
git status --short --branch
~~~

Because the repository has no initial commit, also list every untracked path explicitly. Do not stage or commit.

---

### Task 11: 真机安装、过滤 logcat 和用户人工授权验收

**Files:**

- Modify after evidence: docs/ACCEPTANCE_TESTS.md
- Modify after evidence: docs/TDLIB_BUILD.md
- No credential/code/password file

**Interfaces:**

- Consumes: passing Task 10, configured local.properties, connected arm64-v8a phone。
- Produces: physical-device and manual Telegram evidence without capturing secrets。

- [ ] **Step 1: 再确认设备和本地配置布尔状态**

Run ADB device/ABI commands again. Validate local.properties by parsing only whether ID is positive integer and hash is 32 hex; output only true/false. If either is false, stop and ask the owner to edit local.properties locally without sharing values.

- [ ] **Step 2: 真机自动化和安装**

Run:

~~~powershell
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat installDebug
~~~

Expected: physical device tests and install both exit 0.

- [ ] **Step 3: 启动过滤后的 Auth logcat**

Clear only the current buffer, launch the app, determine PID, then read only CVF/Auth lines:

~~~powershell
E:\AndroidStudio2.0\platform-tools\adb.exe logcat -c
E:\AndroidStudio2.0\platform-tools\adb.exe shell am force-stop com.qixuan.channelvideoflow
E:\AndroidStudio2.0\platform-tools\adb.exe shell monkey -p com.qixuan.channelvideoflow -c android.intent.category.LAUNCHER 1
E:\AndroidStudio2.0\platform-tools\adb.exe shell pidof com.qixuan.channelvideoflow
~~~

Do not stream all logcat and do not take a screenshot while an auth input is visible.

- [ ] **Step 4: 人工登录暂停点**

Tell the owner:

“应用已安装并停在真实 Telegram 登录流程。请只在手机界面输入手机号、验证码和两步验证密码；不要把任何值发到聊天。看到‘已登录’后回复‘已登录’。”

Wait. Do not use adb input text, clipboard, UI dump or screenshot to inspect the fields.

- [ ] **Step 5: 验证 Authorized log state**

After owner confirmation, read only CVF/Auth for the current PID and verify a fixed state=Ready/Authorized entry without user input. If no evidence, mark 尚未验证 rather than infer.

- [ ] **Step 6: 验证重启保持登录**

使用 force-stop 杀死应用进程，再次启动且不清除应用数据。请仓库所有者确认应用直接返回“已登录”，且不要求重新输入验证码或密码。过滤后的 logcat 必须显示 Ready/Authorized。

- [ ] **Step 7: 验证退出**

请仓库所有者在手机上点击“退出登录”，完成明确的退出账号操作。只观察脱敏后的 LoggingOut → Closing → Closed → initialization/WaitPhoneNumber 状态名，再请仓库所有者确认手机号登录页已经可见。

- [ ] **Step 8: 更新验收证据**

Record MANUAL-01/02/03 as passed only for steps directly confirmed by the owner and supported by sanitized state evidence. Record all channel/media acceptance items as 尚未验证 and out of stage scope.

- [ ] **Step 9: final fresh verification**

Run again:

~~~powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat installDebug
git diff --check
git status --short --branch
~~~

Expected: commands exit 0 and only intended stage files differ. Do not commit.

- [ ] **Step 10: 阶段完成报告**

Report in the repository-required order:

1. 一、本阶段目标
2. 二、实际完成内容
3. 三、修改文件
4. 四、关键架构决定
5. 五、执行的命令
6. 六、测试结果
7. 七、真机验证结果
8. 八、已知问题
9. 九、安全检查结果
10. 十、建议的下一阶段

Every unexecuted or user-unconfirmed behavior must say 尚未验证. Show the change summary and suggest a commit; do not create it without explicit authorization.

---

## Plan self-review checklist

- Spec coverage: Tasks 1-11 cover all 27 user requirements and every scope/boundary item。
- Generated/config exception: native and Gradle work use provenance/hash/build Proof；all behavior code uses failing tests first。
- Type consistency: TelegramAuthRepository signatures, TelegramAuthState variants, TelegramAuthClient methods and LoginUiState fields are identical across tasks。
- Security consistency: no task reads or emits real local.properties values, inputs, raw error strings or full TDLib objects。
- Device consistency: every native and APK step is arm64-v8a only。
- Scope consistency: no channel, Room business schema, video, Media3, tag, feed, preload or cache production code is introduced。
- Git consistency: every task explicitly avoids automatic add/commit/push。
