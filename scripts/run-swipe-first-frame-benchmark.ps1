[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Serial,
    [ValidateRange(1, 100)][int]$SwipeCount = 12,
    [ValidateRange(1, 120)][int]$PerSwipeTimeoutSeconds = 12,
    [ValidateSet('Normal', 'Fast')][string]$Mode = 'Normal',
    [ValidateSet('Forward', 'Reverse')][string]$Direction = 'Forward',
    [ValidateRange(5, 120)][int]$PlaybackReadyTimeoutSeconds = 30,
    [ValidateRange(0, 100)][int]$FastCheckpointEvery = 0,
    [ValidateSet('stage13b', 'stage13c', 'stage13d', 'stage13e', 'stage13f', 'stage18')][string]$ReportStage = 'stage13d',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$PackageName = 'com.qixuan.channelvideoflow'
$ActivityName = "$PackageName/.MainActivity"
$repoRoot = Split-Path $PSScriptRoot -Parent
$reportRoot = Join-Path $repoRoot "build/reports/$ReportStage"
$reportTitle = switch ($ReportStage) {
    'stage13b' { 'Stage 13B preload owner promotion benchmark' }
    'stage13c' { 'Stage 13C random round targeting benchmark' }
    'stage13d' { 'Stage 13D startup range A/B benchmark' }
    'stage13e' { 'Stage 13E random reference resolution benchmark' }
    'stage13f' { 'Stage 13F random final acceptance benchmark' }
    'stage18' { 'Stage 18 HLS and weak-network continuous playback benchmark' }
}
$comparisonGuidance = if ($ReportStage -eq 'stage13f') {
    'Compare bind→first-frame against the Stage 13A RANDOM baseline only when media/cache/network conditions are comparable; compare release→settle against the fresh Stage 13E production baseline.'
}
elseif ($ReportStage -eq 'stage18') {
    'Compare identical Stage 18 flag builds only with the same account, queue, media, quality, network window, and cache precondition; SampleQueue requires at least 15% P95 improvement, 100% FIRST_FRAME, and zero safety failures.'
}
else {
    'Compare bind→first-frame against the fresh Stage 13C RANDOM baseline; startup-range and owner counters classify the long tail without media inspection.'
}
$modulePath = Join-Path $PSScriptRoot 'SwipeFirstFrameBenchmark.psm1'
Import-Module $modulePath -Force

if ($Mode -ne 'Fast' -and $FastCheckpointEvery -ne 0) {
    throw '-FastCheckpointEvery is only valid with -Mode Fast.'
}

function Find-Adb {
    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($rootVariable in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($rootVariable)) {
            $candidates.Add((Join-Path $rootVariable 'platform-tools/adb.exe'))
        }
    }
    $candidates.Add('E:\AndroidStudio2.0\platform-tools\adb.exe')
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) { $candidates.Add($command.Source) }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw 'adb.exe not found. Configure ANDROID_HOME/ANDROID_SDK_ROOT or PATH.'
}

$Adb = Find-Adb

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)][string[]]$AdbArguments,
        [switch]$AllowFailure
    )
    $output = @(& $Adb @AdbArguments 2>&1)
    $exitCode = $LASTEXITCODE
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "adb command failed with exit code $exitCode"
    }
    return ,$output
}

function Get-MainLogLines {
    return @(Invoke-Adb -AdbArguments @(
        '-s', $Serial, 'logcat', '-b', 'main', '-d', '-v', 'threadtime', '-s',
        'CVF-Transition:I', 'CVF-Player:I', 'CVF-Preload:I', 'CVF-Adaptive:I',
        'CVF-StartupRange:I', 'CVF-TdFile:I'
    ))
}

function Get-CrashLogLines {
    return @(Invoke-Adb -AdbArguments @(
        '-s', $Serial, 'logcat', '-b', 'crash', '-d', '-v', 'threadtime'
    ) -AllowFailure)
}

function Clear-BenchmarkLogs {
    Invoke-Adb -AdbArguments @('-s', $Serial, 'logcat', '-b', 'main', '-c') | Out-Null
    Invoke-Adb -AdbArguments @('-s', $Serial, 'logcat', '-b', 'crash', '-c') | Out-Null
}

function Wake-BenchmarkDisplay {
    # The physical benchmark device can dim/sleep while uiautomator is producing the verified
    # playback-page tree. WAKEUP is idempotent while already awake and sends no blind UI action.
    Invoke-Adb -AdbArguments @(
        '-s', $Serial, 'shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'
    ) | Out-Null
    Start-Sleep -Milliseconds 100
}

function Get-TerminalCount {
    param($Summary)
    return $Summary.OutcomeCounts.FIRST_FRAME +
        $Summary.OutcomeCounts.FAILED +
        $Summary.OutcomeCounts.UNSUPPORTED +
        $Summary.OutcomeCounts.UNCHANGED
}

function Test-PlaybackPageSafely {
    $tree = (
        Invoke-Adb -AdbArguments @(
            '-s', $Serial, 'exec-out', 'uiautomator', 'dump', '/dev/tty'
        ) -AllowFailure
    ) -join "`n"
    return $tree.Contains('content-desc="返回频道"') -and
        (Test-CvfRandomSelectedUiTree -UiTree $tree) -and
        (
            $tree.Contains('content-desc="暂停视频"') -or
            $tree.Contains('content-desc="继续播放"')
        )
}

function Wait-ForInitialPlayback {
    $deadline = [DateTime]::UtcNow.AddSeconds($PlaybackReadyTimeoutSeconds)
    Write-Output "请在 $PlaybackReadyTimeoutSeconds 秒内安全进入播放页；脚本不会自动点击、退出账号或清理缓存。"
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-PlaybackPageSafely) { return $true }
        Start-Sleep -Milliseconds 250
    }
    return $false
}

function Wait-ForTerminalAfter {
    param(
        [Parameter(Mandatory = $true)][int]$PreviousCount,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $summary = ConvertFrom-CvfBenchmarkLog -Lines (Get-MainLogLines) -PackageName $PackageName
        if ((Get-TerminalCount -Summary $summary) -gt $PreviousCount) { return $true }
        Start-Sleep -Milliseconds 100
    }
    return $false
}

function Get-AppUid {
    $lines = Invoke-Adb -AdbArguments @(
        '-s', $Serial, 'shell', 'cmd', 'package', 'list', 'packages', '-U', $PackageName
    ) -AllowFailure
    foreach ($line in $lines) {
        if ($line -match '\buid:(?<uid>\d+)\b') { return [int]$Matches['uid'] }
    }
    return $null
}

function Get-UidNetworkBytes {
    param([AllowNull()]$Uid)
    if ($null -eq $Uid) { return $null }
    $lines = Invoke-Adb -AdbArguments @(
        '-s', $Serial, 'shell', 'dumpsys', 'netstats', 'detail'
    ) -AllowFailure
    $rx = 0L
    $tx = 0L
    $matched = 0
    foreach ($line in $lines) {
        if ($line -notmatch ("\buid=" + [int]$Uid + "\b")) { continue }
        if ($line -match '\btag=0x(?!0\b)') { continue }
        if ($line -match '\brxBytes=(?<rx>\d+)\b.*\btxBytes=(?<tx>\d+)\b') {
            $rx += [long]$Matches['rx']
            $tx += [long]$Matches['tx']
            $matched += 1
        }
    }
    if ($matched -eq 0) { return $null }
    return [pscustomobject]@{ RxBytes = $rx; TxBytes = $tx }
}

function Write-FailureReport {
    param(
        [Parameter(Mandatory = $true)][string]$Reason,
        [Parameter(Mandatory = $true)][string]$ReportPath
    )
    New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
    @(
        "# $reportTitle",
        '',
        '- Result: FAIL',
        "- Reason: $Reason",
        '- Samples: insufficient; no PASS was inferred.',
        '- Device identifiers, network names, addresses, paths, Telegram keys, and content were not recorded.'
    ) | Set-Content -LiteralPath $ReportPath -Encoding UTF8
}

function Format-MetricValue {
    param($Value)
    if ($null -eq $Value) { return 'n/a' }
    return "${Value}ms"
}

New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$baseName = "random-swipe-first-frame-$($Mode.ToLowerInvariant())-$($Direction.ToLowerInvariant())-$timestamp"
$reportPath = Join-Path $reportRoot "$baseName.md"
$evidencePath = Join-Path $reportRoot "$baseName.log"

try {
    $deviceLines = @(& $Adb devices)
    if ($LASTEXITCODE -ne 0 -or -not ($deviceLines -match ('^' + [regex]::Escape($Serial) + '\s+device\b'))) {
        throw 'target serial is not connected and authorized as device'
    }
    $isQemu = (Invoke-Adb -AdbArguments @('-s', $Serial, 'shell', 'getprop', 'ro.kernel.qemu')) -join ''
    $bootQemu = (Invoke-Adb -AdbArguments @('-s', $Serial, 'shell', 'getprop', 'ro.boot.qemu')) -join ''
    if ($isQemu.Trim() -eq '1' -or $bootQemu.Trim() -eq '1' -or $Serial -match '^emulator-') {
        throw 'target must be a physical device, not an emulator'
    }

    if (-not $SkipBuild) {
        Push-Location $repoRoot
        try {
            & .\gradlew.bat assembleDebug --no-daemon --console=plain
            if ($LASTEXITCODE -ne 0) { throw 'assembleDebug failed' }
        } finally {
            Pop-Location
        }
        $apkPath = Join-Path $repoRoot 'app/build/outputs/apk/debug/app-debug.apk'
        if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
            throw 'debug APK was not produced'
        }
        Invoke-Adb -AdbArguments @('-s', $Serial, 'install', '-r', '-t', $apkPath) | Out-Null
    }

    $packagePath = Invoke-Adb -AdbArguments @(
        '-s', $Serial, 'shell', 'pm', 'path', $PackageName
    ) -AllowFailure
    if (-not ($packagePath -match '^package:')) {
        throw 'target package is not installed; run without -SkipBuild to install while preserving data'
    }

    $uid = Get-AppUid
    $trafficBefore = Get-UidNetworkBytes -Uid $uid
    Clear-BenchmarkLogs
    # A warm launcher intent resets the current in-app navigation route on this app. Preserve an
    # already verified playback page so the benchmark does not invalidate its own precondition.
    if (-not (Test-PlaybackPageSafely)) {
        Invoke-Adb -AdbArguments @(
            '-s', $Serial, 'shell', 'am', 'start', '-W', '-n', $ActivityName
        ) | Out-Null
    }
    if (-not (Wait-ForInitialPlayback)) {
        Write-FailureReport -Reason 'Could not safely confirm the playback page. Enter it manually and rerun; no blind taps were sent.' -ReportPath $reportPath
        Write-Output "SWIPE_BENCHMARK_RESULT=FAIL"
        Write-Output "REPORT=$reportPath"
        exit 3
    }

    Clear-BenchmarkLogs
    $sizeLines = Invoke-Adb -AdbArguments @('-s', $Serial, 'shell', 'wm', 'size')
    $sizeText = $sizeLines -join "`n"
    $sizeMatches = [regex]::Matches($sizeText, '(?<width>\d+)x(?<height>\d+)')
    if ($sizeMatches.Count -eq 0) { throw 'could not determine physical display size' }
    $sizeMatch = $sizeMatches[$sizeMatches.Count - 1]
    $width = [int]$sizeMatch.Groups['width'].Value
    $height = [int]$sizeMatch.Groups['height'].Value
    $x = [Math]::Floor($width * 0.5)
    $startY = if ($Direction -eq 'Forward') {
        [Math]::Floor($height * 0.75)
    } else {
        [Math]::Floor($height * 0.25)
    }
    $endY = if ($Direction -eq 'Forward') {
        [Math]::Floor($height * 0.25)
    } else {
        [Math]::Floor($height * 0.75)
    }
    $gestureDuration = if ($Mode -eq 'Fast') { 80 } else { 150 }
    $timedOut = $false
    [int[]]$fastBatches = if ($Mode -eq 'Fast') {
        @(Get-CvfFastSwipeBatches -SwipeCount $SwipeCount -CheckpointEvery $FastCheckpointEvery)
    } else {
        @()
    }

    if ($Mode -eq 'Normal') {
        for ($index = 1; $index -le $SwipeCount; $index += 1) {
            $beforeSummary = ConvertFrom-CvfBenchmarkLog -Lines (Get-MainLogLines) -PackageName $PackageName
            $beforeCount = Get-TerminalCount -Summary $beforeSummary
            Wake-BenchmarkDisplay
            Invoke-Adb -AdbArguments @(
                '-s', $Serial, 'shell', 'input', 'swipe',
                "$x", "$startY", "$x", "$endY", "$gestureDuration"
            ) | Out-Null
            if (-not (Wait-ForTerminalAfter -PreviousCount $beforeCount -TimeoutSeconds $PerSwipeTimeoutSeconds)) {
                $timedOut = $true
                break
            }
            Start-Sleep -Milliseconds 250
        }
    } else {
        for ($batchIndex = 0; $batchIndex -lt $fastBatches.Count; $batchIndex += 1) {
            $beforeSummary = ConvertFrom-CvfBenchmarkLog -Lines (Get-MainLogLines) -PackageName $PackageName
            $beforeCount = Get-TerminalCount -Summary $beforeSummary
            for ($index = 1; $index -le $fastBatches[$batchIndex]; $index += 1) {
                Wake-BenchmarkDisplay
                Invoke-Adb -AdbArguments @(
                    '-s', $Serial, 'shell', 'input', 'swipe',
                    "$x", "$startY", "$x", "$endY", "$gestureDuration"
                ) | Out-Null
                Start-Sleep -Milliseconds 100
            }
            if (-not (Wait-ForTerminalAfter -PreviousCount $beforeCount -TimeoutSeconds $PerSwipeTimeoutSeconds)) {
                $timedOut = $true
                break
            }
            if ($batchIndex -lt $fastBatches.Count - 1) {
                Start-Sleep -Milliseconds 250
            }
        }
        if (-not $timedOut) { Start-Sleep -Milliseconds 750 }
    }

    $mainLines = Get-MainLogLines
    $crashLines = Get-CrashLogLines
    $allLines = @($mainLines) + @($crashLines)
    $summary = ConvertFrom-CvfBenchmarkLog -Lines $allLines -PackageName $PackageName
    $trafficAfter = Get-UidNetworkBytes -Uid $uid
    $safeEvidence = Protect-CvfBenchmarkLog -Lines $mainLines
    @($safeEvidence) + @("CVF-Benchmark crashCount=$($summary.CrashCount)") |
        Set-Content -LiteralPath $evidencePath -Encoding UTF8

    $gestureMetric = $summary.Metrics.gestureToTerminalMs
    $bindMetric = $summary.Metrics.bindToTerminalMs
    $directionConfirmed = Test-CvfRequestedDirection -Summary $summary -Direction $Direction
    $enoughSamples = if ($Mode -eq 'Normal') {
        $summary.SuccessfulSampleCount -eq $SwipeCount
    } else {
        $summary.SuccessfulSampleCount -ge 1
    }
    $cleanRun = $summary.OutcomeCounts.FAILED -eq 0 -and
        $summary.OutcomeCounts.UNSUPPORTED -eq 0 -and
        $summary.RebufferCount -eq 0 -and
        $summary.CrashCount -eq 0 -and
        -not $timedOut
    $result = if (
        -not $enoughSamples -or
        -not $cleanRun -or
        -not $summary.RandomOrderConfirmed -or
        -not $directionConfirmed -or
        -not $summary.RequiredFieldsComplete -or
        -not $summary.StartupRangeObservationComplete
    ) {
        'FAIL'
    } else {
        'PASS'
    }

    $metricLabels = [ordered]@{
        gestureToReleaseMs = 'gesture→release'
        gestureToTargetKnownMs = 'gesture→target-known'
        releaseToSettleMs = 'release→settle'
        targetKnownToSettleMs = 'target-known→settle'
        targetKnownToPlanReadyMs = 'target-known→plan-ready'
        planReadyToSettleMs = 'plan-ready→settle'
        settleToPlanMs = 'settle→plan'
        planAgeMs = 'plan age'
        refreshMs = 'message refresh'
        planToBindMs = 'plan→bind'
        bindToPrepareMs = 'bind→prepare'
        prepareToReadyMs = 'prepare→READY'
        bindToFirstByteMs = 'bind→first-byte'
        firstByteToReadyMs = 'first-byte→READY'
        bindToReadyMs = 'bind→READY'
        readyToFirstFrameMs = 'READY→first-frame'
        bindToTerminalMs = 'bind→first-frame'
        releaseToTerminalMs = 'release→first-frame'
        targetKnownToTerminalMs = 'target-known→first-frame'
        gestureToTerminalMs = 'gesture→first-frame'
    }
    $report = [System.Collections.Generic.List[string]]::new()
    $report.Add("# $reportTitle")
    $report.Add('')
    $report.Add("- Result: $result")
    $report.Add("- Mode: $Mode")
    $report.Add("- Requested direction: $Direction")
    $report.Add("- Requested swipes: $SwipeCount")
    $report.Add("- Fast batch sizes: $(if ($Mode -eq 'Fast') { $fastBatches -join ',' } else { 'n/a' })")
    $report.Add("- Successful first-frame samples: $($summary.SuccessfulSampleCount)")
    $report.Add("- Per-swipe timeout: ${PerSwipeTimeoutSeconds}s")
    $report.Add('- Physical device and installed package: verified')
    $report.Add('- App data/cache/network/VPN: unchanged by this script')
    $report.Add('- Display: idempotent WAKEUP sent immediately before each verified gesture')
    $report.Add("- RANDOM order confirmed: $($summary.RandomOrderConfirmed)")
    $report.Add("- Requested direction confirmed: $directionConfirmed")
    $report.Add("- Required metric fields complete: $($summary.RequiredFieldsComplete)")
    $report.Add("- Startup range observation complete: $($summary.StartupRangeObservationComplete)")
    $report.Add('')
    $report.Add('## Outcomes')
    $report.Add('')
    $report.Add('| Outcome | Count |')
    $report.Add('|---|---:|')
    foreach ($outcome in @('FIRST_FRAME', 'FAILED', 'UNSUPPORTED', 'SUPERSEDED', 'UNCHANGED', 'RELEASED')) {
        $report.Add("| $outcome | $($summary.OutcomeCounts.$outcome) |")
    }
    $report.Add('')
    $report.Add('## RANDOM context')
    $report.Add('')
    $report.Add("- order RANDOM/LATEST/UNKNOWN: $($summary.OrderCounts.RANDOM)/$($summary.OrderCounts.LATEST)/$($summary.OrderCounts.UNKNOWN)")
    $report.Add("- direction FORWARD/REVERSE/INITIAL/UNCHANGED/UNKNOWN: $($summary.DirectionCounts.FORWARD)/$($summary.DirectionCounts.REVERSE)/$($summary.DirectionCounts.INITIAL)/$($summary.DirectionCounts.UNCHANGED)/$($summary.DirectionCounts.UNKNOWN)")
    $report.Add("- random round boundaries: $($summary.RandomRoundBoundaryCount)/$($summary.RandomRoundBoundaryEligibleCount)")
    $report.Add(
        "- random boundary plans atomically promoted: " +
            "$($summary.RandomRoundBoundaryPlanPromotedCount)/" +
            "$($summary.RandomRoundBoundaryPlanPromotedEligibleCount) " +
            "($($summary.RandomRoundBoundaryPlanPromotedRatePercent)%)"
    )
    $report.Add("- refresh SUCCESS/FALLBACK/SKIPPED/UNKNOWN: $($summary.RefreshOutcomeCounts.SUCCESS)/$($summary.RefreshOutcomeCounts.FALLBACK)/$($summary.RefreshOutcomeCounts.SKIPPED)/$($summary.RefreshOutcomeCounts.UNKNOWN)")
    $report.Add("- transparent recovery attempts: $($summary.TransparentRecoveryAttemptCount) across $($summary.TransparentRecoveryEligibleCount) terminal samples")
    $report.Add(
        "- transparent recovery REBOUND/SOFT_TIMEOUT/UNAVAILABLE/MESSAGE_UNAVAILABLE/STALE_REFERENCE/REFRESHED_FILE_UNAVAILABLE/UNKNOWN: " +
            "$($summary.TransparentRecoveryOutcomeCounts.REBOUND)/" +
            "$($summary.TransparentRecoveryOutcomeCounts.SOFT_TIMEOUT)/" +
            "$($summary.TransparentRecoveryOutcomeCounts.UNAVAILABLE)/" +
            "$($summary.TransparentRecoveryOutcomeCounts.MESSAGE_UNAVAILABLE)/" +
            "$($summary.TransparentRecoveryOutcomeCounts.STALE_REFERENCE)/" +
            "$($summary.TransparentRecoveryOutcomeCounts.REFRESHED_FILE_UNAVAILABLE)/" +
            "$($summary.TransparentRecoveryOutcomeCounts.UNKNOWN)"
    )
    $report.Add('')
    $report.Add('## Segments (FIRST_FRAME only, nearest-rank)')
    $report.Add('')
    $report.Add('| Segment | N | P50 | P90 | max |')
    $report.Add('|---|---:|---:|---:|---:|')
    foreach ($metricName in $metricLabels.Keys) {
        $metric = $summary.Metrics.$metricName
        $report.Add(
            "| $($metricLabels[$metricName]) | $($metric.Count) | " +
                "$(Format-MetricValue $metric.P50) | $(Format-MetricValue $metric.P90) | " +
                "$(Format-MetricValue $metric.Max) |"
        )
    }
    $report.Add('')
    $report.Add('## Safety and regression counters')
    $report.Add('')
    $report.Add("- promoted: $($summary.PromotedCount)/$($summary.PromotedEligibleCount) ($($summary.PromotedRatePercent)%)")
    $report.Add("- preload yield/resume: $($summary.PreloadYieldCount)/$($summary.PreloadResumeCount)")
    $report.Add("- promotion attempt/matched/terminal: $($summary.PromotionAttemptCount)/$($summary.PromotionMatchedCount)/$($summary.PromotionTerminalCount)")
    $report.Add("- reused active request: $($summary.ReusedActiveRequestCount)")
    $report.Add("- Telegram scheduler REUSED_ACTIVE: $($summary.SchedulerActiveRequestReuseCount)")
    $report.Add(
        "- first uncached DataSpec HEAD/TAIL/MIDDLE/UNKNOWN/NONE: " +
            "$($summary.FirstMissCategoryCounts.HEAD)/$($summary.FirstMissCategoryCounts.TAIL)/" +
            "$($summary.FirstMissCategoryCounts.MIDDLE)/$($summary.FirstMissCategoryCounts.UNKNOWN)/" +
            "$($summary.FirstMissCategoryCounts.NONE)"
    )
    $covered = $summary.CoveredBeforeCurrentMetric
    $report.Add(
        "- bytes covered before current N/P50/P90/max: $($covered.Count)/" +
            "$($covered.P50)/$($covered.P90)/$($covered.Max) bytes"
    )
    $speculative = $summary.SpeculativeCoveredMetric
    $extra = $summary.SpeculativeExtraMetric
    $completedExtra = $summary.SpeculativeCompletedExtraMetric
    $report.Add(
        "- speculative covered N/P50/P90/max/total: $($speculative.Count)/" +
            "$($speculative.P50)/$($speculative.P90)/$($speculative.Max)/" +
            "$($speculative.Total) bytes"
    )
    $report.Add(
        "- speculative requested extra N/P50/P90/max/total: $($extra.Count)/" +
            "$($extra.P50)/$($extra.P90)/$($extra.Max)/$($extra.Total) bytes"
    )
    $report.Add(
        "- speculative completed extra N/P50/P90/max/total: $($completedExtra.Count)/" +
            "$($completedExtra.P50)/$($completedExtra.P90)/$($completedExtra.Max)/" +
            "$($completedExtra.Total) bytes"
    )
    $report.Add(
        "- current request reused next owner: $($summary.CurrentReusedNextOwnerCount)/" +
            "$($summary.CurrentReusedNextOwnerEligibleCount)"
    )
    $report.Add(
        "- extractor switches / Telegram switches / merges / cancels: " +
            "$($summary.ExtractorRangeSwitchCount)/$($summary.TelegramRangeSwitchCount)/" +
            "$($summary.TelegramRangeMergeCount)/$($summary.TelegramRangeCancelCount)"
    )
    $report.Add("- NO_PROGRESS timeouts: $($summary.NoProgressTimeoutCount)")
    $report.Add("- cancelled before current acquire: $($summary.CancelledBeforeCurrentAcquireCount)")
    $handoff = $summary.OwnerHandoffMetric
    $report.Add(
        "- owner handoff N/P50/P90/max: $($handoff.Count)/" +
            "$(Format-MetricValue $handoff.P50)/$(Format-MetricValue $handoff.P90)/" +
            "$(Format-MetricValue $handoff.Max)"
    )
    $report.Add("- rebuffer/crash: $($summary.RebufferCount)/$($summary.CrashCount)")
    if ($null -ne $trafficBefore -and $null -ne $trafficAfter) {
        $rxDelta = [Math]::Max(0L, $trafficAfter.RxBytes - $trafficBefore.RxBytes)
        $txDelta = [Math]::Max(0L, $trafficAfter.TxBytes - $trafficBefore.TxBytes)
        $report.Add("- UID traffic delta (all app activity in window): rx=$rxDelta bytes, tx=$txDelta bytes")
    } else {
        $report.Add('- UID traffic delta: 尚未验证（设备未提供可安全聚合的 UID netstats）')
    }
    $report.Add("- $comparisonGuidance")
    $report.Add('- Evidence is redacted; no Telegram content, names, paths, device/network identifiers, addresses, or credentials are stored.')
    $report | Set-Content -LiteralPath $reportPath -Encoding UTF8

    Write-Output "SWIPE_BENCHMARK_RESULT=$result"
    Write-Output "REPORT=$reportPath"
    Write-Output "EVIDENCE=$evidencePath"
    if ($result -eq 'FAIL') { exit 5 }
} catch {
    Write-FailureReport -Reason $_.Exception.Message -ReportPath $reportPath
    Write-Output 'SWIPE_BENCHMARK_RESULT=FAIL'
    Write-Output "REPORT=$reportPath"
    exit 2
}
