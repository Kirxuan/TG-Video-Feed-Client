[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Serial,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

if ($Serial -notlike "emulator-*") {
    throw "Stage 16.1 visual QA only permits emulator-* serials. Refusing '$Serial'."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $PSScriptRoot "run-emulator-compose-tests.ps1"
$targetPackage = "com.qixuan.channelvideoflow.instrumentation"
$remoteDirectory = "/sdcard/Android/data/$targetPackage/files/stage16-1-visuals"
$outputDirectory = Join-Path $repoRoot "build\reports\stage16-1-visuals"

& $runner `
    -Serial $Serial `
    -TestClass "com.qixuan.channelvideoflow.visual.Stage161VisualSnapshotTest" `
    -SkipBuild:$SkipBuild
if ($LASTEXITCODE -ne 0) {
    throw "Stage 16.1 visual instrumentation failed with exit code $LASTEXITCODE."
}

$sdkAdbCandidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    ForEach-Object { Join-Path $_ "platform-tools\adb.exe" }
$adb = @(
    $env:ADB
    "E:\AndroidStudio2.0\platform-tools\adb.exe"
    $sdkAdbCandidates
) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path -LiteralPath $_) } |
    Select-Object -First 1
if (-not $adb) {
    throw "adb was not found after the visual test completed."
}

New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& $adb -s $Serial pull "$remoteDirectory/." $outputDirectory
if ($LASTEXITCODE -ne 0) {
    throw "Could not pull Stage 16.1 screenshots from '$remoteDirectory'."
}

$screenshots = @(Get-ChildItem -LiteralPath $outputDirectory -Filter "*.png" -File)
if ($screenshots.Count -ne 3) {
    throw "Expected exactly 3 Stage 16.1 screenshots, found $($screenshots.Count)."
}

$screenshots |
    Sort-Object Name |
    ForEach-Object {
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        Write-Output "SCREENSHOT=$($_.FullName) SHA256=$hash"
    }
Write-Output "STAGE16_1_VISUAL_QA_RESULT=PASS"
