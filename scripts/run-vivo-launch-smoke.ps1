[CmdletBinding()]
param(
    [string]$Serial = $env:ANDROID_SERIAL,
    [switch]$SkipBuild,
    [ValidateRange(5, 60)]
    [int]$ActivityTimeoutSeconds = 20
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot "gradlew.bat"
$targetPackage = "com.qixuan.channelvideoflow"
$instrumentationPackage = "com.qixuan.channelvideoflow.instrumentation"
$instrumentationTestPackage = "com.qixuan.channelvideoflow.instrumentation.test"
$debugApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$instrumentationApk = Join-Path $repoRoot "app\build\outputs\apk\instrumentation\app-instrumentation.apk"
$instrumentationTestApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\instrumentation\app-instrumentation-androidTest.apk"
$reportRoot = Join-Path $repoRoot "build\reports\vivo-launch-smoke"
$prepLog = Join-Path $reportRoot "prep.log"
$startLog = Join-Path $reportRoot "start.log"
$activityLog = Join-Path $reportRoot "activity.log"
$logcatLog = Join-Path $reportRoot "target-logcat.log"
$crashLog = Join-Path $reportRoot "crash-buffer.log"

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
        throw "Specify -Serial when exactly one ready physical device is not connected."
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
$bash = Resolve-FirstExistingPath @(
    $env:GIT_BASH,
    "C:\Program Files\Git\bin\bash.exe"
)
if (-not $adb) {
    throw "adb was not found. Set ADB or install Android platform-tools."
}
if (-not $bash) {
    throw "Git Bash was not found. Set GIT_BASH; vivo-test-prep.sh requires a POSIX shell."
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $Serial = Get-ConnectedDeviceSerial -AdbPath $adb
}

$deviceState = (& $adb -s $Serial get-state).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne "device") {
    throw "ADB device is not ready: serial=$Serial state=$deviceState"
}

$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim()
if ($abi -notmatch "arm64-v8a") {
    throw "Vivo launch smoke requires an ARM64 physical device; connected $Serial reports ABI '$abi'."
}

New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null

if (-not $SkipBuild) {
    if (Test-Path -LiteralPath "E:\Android Studio\jbr") {
        $env:JAVA_HOME = "E:\Android Studio\jbr"
    }
    Push-Location $repoRoot
    try {
        & $gradle :app:assembleDebug :app:assembleInstrumentation :app:assembleInstrumentationAndroidTest --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "APK build failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

foreach ($apk in @($debugApk, $instrumentationApk, $instrumentationTestApk)) {
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "Missing APK: $apk"
    }
}

$env:ADB = ($adb -replace "\\", "/")
$env:ANDROID_SERIAL = $Serial
$env:PKG = $instrumentationPackage
$env:TEST_PKG = $instrumentationTestPackage
$env:APK = ($instrumentationApk -replace "\\", "/")
$env:TEST_APK = ($instrumentationTestApk -replace "\\", "/")

Push-Location $repoRoot
try {
    & $bash "scripts/vivo-test-prep.sh" 2>&1 | Tee-Object -FilePath $prepLog
    if ($LASTEXITCODE -ne 0) {
        throw "Vivo test preparation failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

& $adb -s $Serial install -r -t $debugApk
if ($LASTEXITCODE -ne 0) {
    throw "Debug APK installation failed with exit code $LASTEXITCODE."
}

& $adb -s $Serial shell am force-stop $targetPackage
& $adb -s $Serial logcat -c
$startOutput = & $adb -s $Serial shell am start -W -n "$targetPackage/.MainActivity" 2>&1
$startExitCode = $LASTEXITCODE
$startOutput | Set-Content -LiteralPath $startLog -Encoding utf8

$componentPattern = "com\.qixuan\.channelvideoflow/(?:\.MainActivity|com\.qixuan\.channelvideoflow\.MainActivity)"
$activityOutput = ""
$isResumed = $false
for ($attempt = 1; $attempt -le $ActivityTimeoutSeconds; $attempt += 1) {
    $activityOutput = (& $adb -s $Serial shell dumpsys activity activities) -join "`n"
    if ($activityOutput -match "(?m)(mResumedActivity|topResumedActivity).*$componentPattern") {
        $isResumed = $true
        break
    }
    Start-Sleep -Seconds 1
}
$activityOutput | Set-Content -LiteralPath $activityLog -Encoding utf8

$fullLogcat = @(& $adb -s $Serial logcat -d -v threadtime -t 500 2>&1)
$targetLogcat = @($fullLogcat | Select-String -Pattern ([regex]::Escape($targetPackage)))
$targetLogcatLines = @($targetLogcat | ForEach-Object Line)
Set-Content -LiteralPath $logcatLog -Value $targetLogcatLines -Encoding utf8
$fullCrashBuffer = @(& $adb -s $Serial logcat -d -b crash -v threadtime -t 200 2>&1)
$crashBuffer = @($fullCrashBuffer | Select-String -Pattern ([regex]::Escape($targetPackage)))
$crashBufferLines = @($crashBuffer | ForEach-Object Line)
Set-Content -LiteralPath $crashLog -Value $crashBufferLines -Encoding utf8

$combinedLogcat = ($fullLogcat + $fullCrashBuffer) -join "`n"
$escapedPackage = [regex]::Escape($targetPackage)
$crashPatterns = @(
    "(?im)am_crash.*$escapedPackage",
    "(?is)FATAL EXCEPTION.*?Process:\s*$escapedPackage",
    "(?im)(Fatal signal|native crash).*${escapedPackage}",
    "(?im)$escapedPackage.*(FATAL EXCEPTION|Fatal signal|native crash)"
)
$hasCrash = $false
foreach ($pattern in $crashPatterns) {
    if ($combinedLogcat -match $pattern) {
        $hasCrash = $true
        break
    }
}

$startSucceeded = $startExitCode -eq 0 -and (($startOutput -join "`n") -notmatch "(?im)^Error:")
$passed = $startSucceeded -and $isResumed -and -not $hasCrash

Write-Output "PREP_LOG=$prepLog"
Write-Output "START_LOG=$startLog"
Write-Output "ACTIVITY_LOG=$activityLog"
Write-Output "TARGET_LOGCAT=$logcatLog"
Write-Output "CRASH_BUFFER=$crashLog"
Write-Output "VIVO_LAUNCH_SMOKE_RESULT=$(if ($passed) { "PASS" } else { "FAIL" })"

if (-not $passed) {
    exit 4
}
