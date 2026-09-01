[CmdletBinding()]
param(
    [string]$Serial = $env:ANDROID_SERIAL,
    [ValidateNotNullOrEmpty()]
    [string]$AvdName = "CVF_AOSP_API36_X86_64",
    [string]$TestClass = "",
    [switch]$SkipBuild,
    [ValidateRange(30, 600)]
    [int]$ProcessTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot "gradlew.bat"
$targetPackage = "com.qixuan.channelvideoflow.instrumentation"
$testPackage = "com.qixuan.channelvideoflow.instrumentation.test"
$runner = "androidx.test.runner.AndroidJUnitRunner"
$targetApk = Join-Path $repoRoot "app\build\outputs\apk\instrumentation\app-instrumentation.apk"
$testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\instrumentation\app-instrumentation-androidTest.apk"
$reportRoot = Join-Path $repoRoot "build\reports\emulator-compose"
$instrumentLog = Join-Path $reportRoot "instrumentation.log"
$deviceLog = Join-Path $reportRoot "device.log"
$logcatLog = Join-Path $reportRoot "target-logcat.log"

function Resolve-FirstExistingPath {
    param([string[]]$Candidates)

    return $Candidates |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path -LiteralPath $_) } |
        Select-Object -First 1
}

function Get-ConnectedDeviceSerial {
    param([string]$AdbPath)

    $devices = @(& $AdbPath devices | Select-String "^(\S+)\s+device(?:\s|$)")
    if ($devices.Count -ne 1) {
        throw "Specify -Serial when exactly one ready emulator is not connected. Expected AVD: $AvdName"
    }
    return $devices[0].Matches[0].Groups[1].Value
}

$sdkAdbCandidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    ForEach-Object { Join-Path $_ "platform-tools\adb.exe" }
$adb = Resolve-FirstExistingPath @(
    $env:ADB,
    "E:\AndroidStudio2.0\platform-tools\adb.exe",
    $sdkAdbCandidates
)
if (-not $adb) {
    throw "adb was not found. Set ADB or install Android platform-tools."
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $Serial = Get-ConnectedDeviceSerial -AdbPath $adb
}
if ($Serial -notlike "emulator-*") {
    throw "Only an Android emulator serial is allowed for this proof. Refusing serial '$Serial'."
}

$deviceState = (& $adb -s $Serial get-state).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne "device") {
    throw "ADB device is not ready: serial=$Serial state=$deviceState"
}

$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim()
$sdk = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($abi -ne "x86_64") {
    throw "Compose UI Proof requires x86_64 AOSP emulator '$AvdName'; connected $Serial reports ABI '$abi'. Do not use the ARM64 AVD."
}
if ($sdk -ne "36") {
    throw "Compose UI Proof requires API 36; connected $Serial reports API '$sdk'."
}

New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
@(
    "serial=$Serial"
    "abi=$abi"
    "sdk=$sdk"
    "expectedAvd=$AvdName"
    "product=$((& $adb -s $Serial shell getprop ro.product.name).Trim())"
    "navigationMode=$((& $adb -s $Serial shell settings get secure navigation_mode).Trim())"
    "displaySize=$((& $adb -s $Serial shell wm size | Select-Object -Last 1).Trim())"
    "displayDensity=$((& $adb -s $Serial shell wm density | Select-Object -Last 1).Trim())"
    "fontScale=$((& $adb -s $Serial shell settings get system font_scale).Trim())"
    "userRotation=$((& $adb -s $Serial shell settings get system user_rotation).Trim())"
) | Set-Content -LiteralPath $deviceLog -Encoding utf8

if (Test-Path -LiteralPath "E:\Android Studio\jbr") {
    $env:JAVA_HOME = "E:\Android Studio\jbr"
}

if (-not $SkipBuild) {
    Push-Location $repoRoot
    try {
        & $gradle :app:assembleInstrumentation :app:assembleInstrumentationAndroidTest --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Instrumentation APK build failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

foreach ($apk in @($targetApk, $testApk)) {
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "Missing APK: $apk"
    }
}

$jar = Join-Path $env:JAVA_HOME "bin\jar.exe"
if (-not (Test-Path -LiteralPath $jar)) {
    throw "JBR jar.exe was not found at '$jar'. Set JAVA_HOME to a JDK before running this script."
}
$nativeEntries = @(& $jar tf $targetApk | Select-String "^lib/")
if ($nativeEntries.Count -gt 0) {
    throw "Instrumentation target APK must not contain native libraries for x86_64 Compose UI tests: $($nativeEntries -join '; ')"
}

& $adb -s $Serial install -r -t $targetApk
if ($LASTEXITCODE -ne 0) {
    throw "Target APK installation failed with exit code $LASTEXITCODE."
}
& $adb -s $Serial install -r -t $testApk
if ($LASTEXITCODE -ne 0) {
    throw "Test APK installation failed with exit code $LASTEXITCODE."
}

& $adb -s $Serial shell am force-stop $targetPackage
& $adb -s $Serial shell am force-stop $testPackage
& $adb -s $Serial logcat -c

$defaultClasses = @(
    "com.qixuan.channelvideoflow.feature.auth.LoginScreenTest"
    "com.qixuan.channelvideoflow.feature.channels.ChannelSelectionScreenTest"
    "com.qixuan.channelvideoflow.feature.tags.TagFilterScreenTest"
    "com.qixuan.channelvideoflow.test.ComposeSmokeTest"
    "com.qixuan.channelvideoflow.feature.video.VideoPlaybackScreenTest"
    "com.qixuan.channelvideoflow.feature.settings.CacheSettingsScreenTest"
    "com.qixuan.channelvideoflow.feature.video.VideoPlaybackActivityRecreationTest"
)
$classFilter = if ([string]::IsNullOrWhiteSpace($TestClass)) {
    $defaultClasses -join ","
}
else {
    $TestClass
}

$instrumentOutput = & $adb -s $Serial shell am instrument -w -r --no-window-animation `
    -e timeout_msec 120000 `
    -e class $classFilter `
    "$testPackage/$runner" 2>&1
$instrumentExitCode = $LASTEXITCODE
$instrumentOutput | Set-Content -LiteralPath $instrumentLog -Encoding utf8

$targetLogcat = @(& $adb -s $Serial logcat -d -v threadtime -t 500 2>&1 |
    Select-String -Pattern ([regex]::Escape($targetPackage)))
$targetLogcatLines = @($targetLogcat | ForEach-Object Line)
Set-Content -LiteralPath $logcatLog -Value $targetLogcatLines -Encoding utf8

$passed = $instrumentExitCode -eq 0 -and
    (($instrumentOutput -join "`n") -match "(?m)^OK \(\d+ tests?\)$") -and
    (($instrumentOutput -join "`n") -notmatch "Process crashed|FAILURES!!!")

Write-Output "DEVICE_LOG=$deviceLog"
Write-Output "INSTRUMENT_LOG=$instrumentLog"
Write-Output "TARGET_LOGCAT=$logcatLog"
Write-Output "EMULATOR_COMPOSE_RESULT=$(if ($passed) { "PASS" } else { "FAIL" })"

if (-not $passed) {
    exit 4
}
