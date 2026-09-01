[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Serial
)

$ErrorActionPreference = "Stop"

if ($Serial -notlike "emulator-*") {
    throw "Stage 19 system UI QA only permits emulator-* serials. Refusing '$Serial'."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $PSScriptRoot "run-emulator-compose-tests.ps1"
$genericReport = Join-Path $repoRoot "build\reports\emulator-compose"
$reportRoot = Join-Path $repoRoot "build\reports\stage19-system-ui"
$targetPackage = "com.qixuan.channelvideoflow.instrumentation"
$mainComponent = "$targetPackage/com.qixuan.channelvideoflow.MainActivity"
$systemUiTest = "com.qixuan.channelvideoflow.visual.Stage19SystemUiTest"
$displayCutoutTest = "com.qixuan.channelvideoflow.visual.Stage19DisplayCutoutTest"
$cutoutOverlay = "com.android.internal.display.cutout.emulation.hole"

function Resolve-FirstExistingPath {
    param([string[]]$Candidates)

    return $Candidates |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path -LiteralPath $_) } |
        Select-Object -First 1
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

$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim()
$sdk = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($abi -ne "x86_64" -or $sdk -ne "36") {
    throw "Stage 19 requires API 36 x86_64 AOSP; serial=$Serial abi=$abi sdk=$sdk"
}

$originalNavigationMode = (& $adb -s $Serial shell settings get secure navigation_mode).Trim()
$originalFontScale = (& $adb -s $Serial shell settings get system font_scale).Trim()
$originalRotation = (& $adb -s $Serial shell settings get system user_rotation).Trim()
$originalAccelerometerRotation = (& $adb -s $Serial shell settings get system accelerometer_rotation).Trim()
$originalCutoutOverlay = (& $adb -s $Serial shell cmd overlay list |
    Select-String '^\[x\]\s+com\.android\.internal\.display\.cutout\.emulation\.' |
    ForEach-Object { ($_ -replace '^\[x\]\s+', '').Trim() } |
    Select-Object -First 1)

if ((& $adb -s $Serial shell cmd overlay list | Select-String ([regex]::Escape($cutoutOverlay))).Count -eq 0) {
    throw "Required AOSP display cutout overlay '$cutoutOverlay' is unavailable."
}

function Set-NavigationMode {
    param(
        [ValidateSet("threebutton", "gestural")]
        [string]$Mode,
        [string]$ExpectedValue
    )

    $overlay = "com.android.internal.systemui.navbar.$Mode"
    & $adb -s $Serial shell cmd overlay enable-exclusive --user 0 --category $overlay
    if ($LASTEXITCODE -ne 0) {
        throw "Could not enable navigation overlay '$overlay'."
    }
    Start-Sleep -Seconds 2
    $actual = (& $adb -s $Serial shell settings get secure navigation_mode).Trim()
    if ($actual -ne $ExpectedValue) {
        throw "Navigation mode '$Mode' expected '$ExpectedValue', observed '$actual'."
    }
}

function Assert-ColdLaunchAndRootBack {
    param(
        [ValidateSet("threebutton", "gestural")]
        [string]$Mode,
        [string]$OutputDirectory
    )

    & $adb -s $Serial shell am force-stop $targetPackage
    $startOutput = @(& $adb -s $Serial shell am start -W -S -n $mainComponent 2>&1)
    $startOutput | Set-Content -LiteralPath (Join-Path $OutputDirectory "cold-launch.log") -Encoding utf8
    if ($LASTEXITCODE -ne 0 -or ($startOutput -join "`n") -notmatch "Status:\s+ok") {
        throw "Cold launch failed in '$Mode' mode."
    }
    Start-Sleep -Seconds 1
    $beforeBack = @(& $adb -s $Serial shell dumpsys activity activities |
        Select-String "topResumedActivity|mResumedActivity")
    $beforeBack | Set-Content -LiteralPath (Join-Path $OutputDirectory "before-back.log") -Encoding utf8
    if (($beforeBack -join "`n") -notmatch [regex]::Escape($targetPackage)) {
        throw "MainActivity was not resumed before '$Mode' root back."
    }

    if ($Mode -eq "threebutton") {
        $sizeLine = (& $adb -s $Serial shell wm size | Select-Object -Last 1).Trim()
        $densityLine = (& $adb -s $Serial shell wm density | Select-Object -Last 1).Trim()
        $sizeMatch = [regex]::Match($sizeLine, '(\d+)x(\d+)')
        $densityMatch = [regex]::Match($densityLine, '(\d+)$')
        if (-not $sizeMatch.Success -or -not $densityMatch.Success) {
            throw "Could not resolve emulator size/density for three-button back."
        }
        $displayWidth = [int]$sizeMatch.Groups[1].Value
        $displayHeight = [int]$sizeMatch.Groups[2].Value
        $densityDpi = [int]$densityMatch.Groups[1].Value
        $backX = [int][math]::Round($displayWidth / 6.0)
        $backY = $displayHeight - [int][math]::Round(24.0 * $densityDpi / 160.0)
        & $adb -s $Serial shell input tap $backX $backY
    }
    else {
        & $adb -s $Serial shell input swipe 1 1200 620 1200 500
    }
    Start-Sleep -Seconds 2
    $afterBack = @(& $adb -s $Serial shell dumpsys activity activities |
        Select-String "topResumedActivity|mResumedActivity")
    $afterBack | Set-Content -LiteralPath (Join-Path $OutputDirectory "after-back.log") -Encoding utf8
    if (($afterBack -join "`n") -match [regex]::Escape($targetPackage)) {
        throw "Root back did not return home in '$Mode' mode."
    }
}

function Restore-OriginalEnvironment {
    if ($originalNavigationMode -eq "2") {
        Set-NavigationMode -Mode gestural -ExpectedValue "2"
    }
    else {
        Set-NavigationMode -Mode threebutton -ExpectedValue "0"
    }
    & $adb -s $Serial shell settings put system font_scale $originalFontScale
    if ($originalAccelerometerRotation -eq "1") {
        & $adb -s $Serial shell wm user-rotation free | Out-Null
    }
    else {
        & $adb -s $Serial shell wm user-rotation lock $originalRotation | Out-Null
    }
    & $adb -s $Serial shell cmd overlay disable --user 0 $cutoutOverlay | Out-Null
    if (-not [string]::IsNullOrWhiteSpace($originalCutoutOverlay)) {
        & $adb -s $Serial shell cmd overlay enable-exclusive --user 0 --category $originalCutoutOverlay | Out-Null
    }
}

New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null

try {
    $modes = @(
        @{ Name = "threebutton"; Value = "0" },
        @{ Name = "gestural"; Value = "2" }
    )
    foreach ($mode in $modes) {
        $modeDirectory = Join-Path $reportRoot $mode.Name
        New-Item -ItemType Directory -Force -Path $modeDirectory | Out-Null
        Set-NavigationMode -Mode $mode.Name -ExpectedValue $mode.Value
        & $runner -Serial $Serial -TestClass $systemUiTest -SkipBuild
        if ($LASTEXITCODE -ne 0) {
            throw "Stage 19 Insets/IME instrumentation failed in '$($mode.Name)' mode."
        }
        Copy-Item -LiteralPath (Join-Path $genericReport "device.log") -Destination $modeDirectory -Force
        Copy-Item -LiteralPath (Join-Path $genericReport "instrumentation.log") -Destination $modeDirectory -Force
        Copy-Item -LiteralPath (Join-Path $genericReport "target-logcat.log") -Destination $modeDirectory -Force
        Assert-ColdLaunchAndRootBack -Mode $mode.Name -OutputDirectory $modeDirectory
    }

    $cutoutDirectory = Join-Path $reportRoot "display-cutout"
    New-Item -ItemType Directory -Force -Path $cutoutDirectory | Out-Null
    Set-NavigationMode -Mode gestural -ExpectedValue "2"
    & $adb -s $Serial shell cmd overlay enable-exclusive --user 0 --category $cutoutOverlay
    if ($LASTEXITCODE -ne 0) {
        throw "Could not enable AOSP display cutout overlay '$cutoutOverlay'."
    }
    Start-Sleep -Seconds 2
    $enabledCutout = @(& $adb -s $Serial shell cmd overlay list |
        Select-String "^\[x\]\s+$([regex]::Escape($cutoutOverlay))$")
    if ($enabledCutout.Count -eq 0) {
        throw "Display cutout overlay '$cutoutOverlay' was not enabled."
    }
    & $runner -Serial $Serial -TestClass $displayCutoutTest -SkipBuild
    if ($LASTEXITCODE -ne 0) {
        throw "Stage 19 non-zero display cutout instrumentation failed."
    }
    Copy-Item -LiteralPath (Join-Path $genericReport "device.log") -Destination $cutoutDirectory -Force
    Copy-Item -LiteralPath (Join-Path $genericReport "instrumentation.log") -Destination $cutoutDirectory -Force
    Copy-Item -LiteralPath (Join-Path $genericReport "target-logcat.log") -Destination $cutoutDirectory -Force
    & $adb -s $Serial shell am start -W -S -n $mainComponent |
        Set-Content -LiteralPath (Join-Path $cutoutDirectory "cutout-launch.log") -Encoding utf8
    Start-Sleep -Seconds 1
    & $adb -s $Serial shell screencap -p /sdcard/stage19-display-cutout.png
    & $adb -s $Serial pull /sdcard/stage19-display-cutout.png (Join-Path $cutoutDirectory "display-cutout.png") | Out-Null
    & $adb -s $Serial shell cmd overlay disable --user 0 $cutoutOverlay | Out-Null
    Start-Sleep -Seconds 2

    Set-NavigationMode -Mode gestural -ExpectedValue "2"
    & $adb -s $Serial shell am start -W -S -n $mainComponent |
        Set-Content -LiteralPath (Join-Path $reportRoot "landscape-launch.log") -Encoding utf8
    & $adb -s $Serial shell wm user-rotation lock 1 | Out-Null
    $landscapeObserved = $false
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        Start-Sleep -Seconds 1
        $rotationState = @(& $adb -s $Serial shell dumpsys window displays |
            Select-String "mRotation=ROTATION_90|mDisplayRotation=ROTATION_90")
        if ($rotationState.Count -gt 0) {
            $landscapeObserved = $true
            break
        }
    }
    if (-not $landscapeObserved) {
        throw "Landscape rotation 90 was not observed within 10 seconds."
    }
    & $adb -s $Serial shell screencap -p /sdcard/stage19-landscape.png
    & $adb -s $Serial pull /sdcard/stage19-landscape.png (Join-Path $reportRoot "landscape.png") | Out-Null
    $landscapeConfig = @(
        & $adb -s $Serial shell dumpsys activity activities |
            Select-String "topResumedActivity|mResumedActivity"
        & $adb -s $Serial shell dumpsys window displays |
            Select-String "overrideConfig=.*land|mRotation=ROTATION_90|mDisplayRotation=ROTATION_90"
    )
    $landscapeConfig | Set-Content -LiteralPath (Join-Path $reportRoot "landscape-state.log") -Encoding utf8
    if (($landscapeConfig -join "`n") -notmatch "land") {
        throw "Landscape configuration was not observed."
    }

    & $adb -s $Serial shell wm user-rotation lock 0 | Out-Null
    & $adb -s $Serial shell settings put system font_scale 1.3
    Start-Sleep -Seconds 2
    & $adb -s $Serial shell am start -W -S -n $mainComponent |
        Set-Content -LiteralPath (Join-Path $reportRoot "large-font-launch.log") -Encoding utf8
    & $adb -s $Serial shell screencap -p /sdcard/stage19-font130.png
    & $adb -s $Serial pull /sdcard/stage19-font130.png (Join-Path $reportRoot "font130.png") | Out-Null
}
finally {
    Restore-OriginalEnvironment
}

Write-Output "STAGE19_SYSTEM_UI_REPORT=$reportRoot"
Write-Output "STAGE19_SYSTEM_UI_QA_RESULT=PASS"
