[CmdletBinding()]
param(
    [string]$Serial = $env:ANDROID_SERIAL,
    [string]$TestClass = "",
    [ValidateRange(30, 600)]
    [int]$ProcessTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$adb = "E:\AndroidStudio2.0\platform-tools\adb.exe"
$bash = "C:\Program Files\Git\bin\bash.exe"
$gradle = Join-Path $repoRoot "gradlew.bat"
$targetPackage = "com.qixuan.channelvideoflow.instrumentation"
$testPackage = "com.qixuan.channelvideoflow.instrumentation.test"
$runner = "androidx.test.runner.AndroidJUnitRunner"
$targetApk = Join-Path $repoRoot "app\build\outputs\apk\instrumentation\app-instrumentation.apk"
$testApk = Join-Path $repoRoot (
    "app\build\outputs\apk\androidTest\instrumentation\app-instrumentation-androidTest.apk"
)
$reportRoot = Join-Path $repoRoot "build\reports\vivo-compose"
$prepLog = Join-Path $reportRoot "prep.log"
$instrumentLog = Join-Path $reportRoot "instrumentation.log"
$diagnosticLog = Join-Path $reportRoot "diagnostics.log"

if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb not found: $adb"
}
if (-not (Test-Path -LiteralPath $bash)) {
    throw "Git Bash not found: $bash"
}

New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $deviceLines = @(& $adb devices | Select-String "^\S+\s+device$")
    if ($deviceLines.Count -ne 1) {
        throw "Specify -Serial when exactly one authorized device is not connected."
    }
    $Serial = (($deviceLines[0].Line -split "\s+")[0])
}

$env:JAVA_HOME = "E:\Android Studio\jbr"
Push-Location $repoRoot
try {
    & $gradle `
        :app:assembleInstrumentation `
        :app:assembleInstrumentationAndroidTest `
        --no-daemon `
        --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Instrumentation APK build failed with exit code $LASTEXITCODE."
    }

    $env:ADB = "E:/AndroidStudio2.0/platform-tools/adb.exe"
    $env:ANDROID_SERIAL = $Serial
    $env:PKG = $targetPackage
    $env:TEST_PKG = $testPackage
    $env:APK = ($targetApk -replace "\\", "/")
    $env:TEST_APK = ($testApk -replace "\\", "/")

    & $bash "scripts/vivo-test-prep.sh" 2>&1 |
        Tee-Object -FilePath $prepLog
    if ($LASTEXITCODE -ne 0) {
        throw "Vivo test preparation failed with exit code $LASTEXITCODE."
    }

    & $adb -s $Serial logcat -c
    & $adb -s $Serial shell input keyevent KEYCODE_WAKEUP
    & $adb -s $Serial shell wm dismiss-keyguard 2>$null

    $arguments = [System.Collections.Generic.List[string]]::new()
    foreach ($argument in @(
            "-s", $Serial,
            "shell", "am", "instrument",
            "-w", "-r", "--no-window-animation",
            "-e", "timeout_msec", "120000",
            "-e", "numShards", "1",
            "-e", "shardIndex", "0"
        )) {
        $arguments.Add($argument)
    }
    if (-not [string]::IsNullOrWhiteSpace($TestClass)) {
        foreach ($argument in @("-e", "class", $TestClass)) {
            $arguments.Add($argument)
        }
    }
    $arguments.Add("$testPackage/$runner")

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $adb
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $arguments) {
        $startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $completed = $process.WaitForExit($ProcessTimeoutSeconds * 1000)
    if (-not $completed) {
        $process.Kill($true)
        $process.WaitForExit()
    }
    $instrumentOutput = $process.StandardOutput.ReadToEnd()
    $instrumentError = $process.StandardError.ReadToEnd()
    @(
        "completed=$completed"
        "exitCode=$(if ($completed) { $process.ExitCode } else { "TIMEOUT" })"
        $instrumentOutput
        $instrumentError
    ) | Set-Content -LiteralPath $instrumentLog -Encoding utf8

    $diagnostics = [System.Collections.Generic.List[string]]::new()
    $diagnostics.Add("ENVIRONMENT")
    foreach ($property in @(
            "ro.product.manufacturer",
            "ro.product.model",
            "ro.build.version.release",
            "ro.build.version.sdk",
            "ro.vivo.os.version",
            "ro.build.display.id",
            "ro.product.cpu.abi"
        )) {
        $value = (& $adb -s $Serial shell getprop $property).Trim()
        $diagnostics.Add("$property=$value")
    }
    $diagnostics.Add("PROCESS")
    & $adb -s $Serial shell dumpsys activity processes |
        Select-String -Pattern (
            "$([regex]::Escape($targetPackage))|" +
            "$([regex]::Escape($testPackage))"
        ) -Context 3, 12 |
        Select-Object -First 40 |
        ForEach-Object { $diagnostics.Add($_.ToString()) }
    $diagnostics.Add("DEVICE_IDLE")
    & $adb -s $Serial shell dumpsys deviceidle |
        Select-String -Pattern "mState=|mLightState=|mForceIdle=|mScreenOn=|mCharging=" |
        Select-Object -First 30 |
        ForEach-Object { $diagnostics.Add($_.Line) }
    $diagnostics.Add("BATTERY")
    & $adb -s $Serial shell dumpsys battery |
        Select-String -Pattern "powered:|status:|level:|temperature:" |
        Select-Object -First 30 |
        ForEach-Object { $diagnostics.Add($_.Line) }
    $diagnostics.Add("CRASH_BUFFER")
    & $adb -s $Serial logcat -d -b crash -v threadtime |
        Select-String -Pattern "$([regex]::Escape($targetPackage))|signal|tombstone" |
        Select-Object -Last 80 |
        ForEach-Object { $diagnostics.Add($_.Line) }
    $diagnostics.Add("SYSTEM_CONTROL")
    & $adb -s $Serial logcat -d -v threadtime |
        Select-String -Pattern (
            "$([regex]::Escape($targetPackage)).*" +
            "(fast_freezer|ASDP|bgkill|freeze|kill|Force stopping|am_proc_died|am_crash)|" +
            "(fast_freezer|ASDP|bgkill|freeze|kill|Force stopping|am_proc_died|am_crash).*" +
            "$([regex]::Escape($targetPackage))"
        ) |
        Select-Object -Last 120 |
        ForEach-Object { $diagnostics.Add($_.Line) }
    $diagnostics.Add("SYSTEM_CONTROL_EVENTS")
    & $adb -s $Serial logcat -d -b events -v threadtime |
        Select-String -Pattern (
            "$([regex]::Escape($targetPackage)).*" +
            "(fast_freezer|ASDP|bgkill|freeze|kill|Force stopping|am_proc_died|am_crash)|" +
            "(fast_freezer|ASDP|bgkill|freeze|kill|Force stopping|am_proc_died|am_crash).*" +
            "$([regex]::Escape($targetPackage))"
        ) |
        Select-Object -Last 120 |
        ForEach-Object { $diagnostics.Add($_.Line) }
    $diagnostics | Set-Content -LiteralPath $diagnosticLog -Encoding utf8

    & $adb -s $Serial shell am force-stop $targetPackage
    & $adb -s $Serial shell am force-stop $testPackage

    $passed = $completed -and
        $process.ExitCode -eq 0 -and
        $instrumentOutput -match "(?m)^OK \(\d+ tests?\)$" -and
        $instrumentOutput -notmatch "Process crashed|FAILURES!!!"

    Write-Output "PREP_LOG=$prepLog"
    Write-Output "INSTRUMENT_LOG=$instrumentLog"
    Write-Output "DIAGNOSTIC_LOG=$diagnosticLog"
    Write-Output "VIVO_COMPOSE_RESULT=$(if ($passed) { "PASS" } else { "BLOCKED" })"

    if (-not $passed) {
        exit 4
    }
}
finally {
    Pop-Location
}
