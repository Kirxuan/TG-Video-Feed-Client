[CmdletBinding()]
param(
    [string] $HostGit = 'C:\Program Files\Git\cmd\git.exe',
    [string] $MsysRoot = 'C:\msys64',
    [string] $MsysBash = 'C:\msys64\usr\bin\bash.exe',
    [string] $AndroidSdkRoot = 'E:\AndroidStudio2.0',
    [string] $AndroidNdkRoot = '',
    [string] $AndroidCMakeRoot = '',
    [string] $PhpExe = 'C:\msys64\ucrt64\bin\php.exe',
    [string] $BuildRoot = 'E:\tdlib-build\channel-video-flow'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$TdLibRepository = 'https://github.com/tdlib/td.git'
$TdLibCommit = '022d60202e446ad1287b9fb68e687c8a0760788b'
$TdLibVersion = '1.8.66'
$OpenSslVersion = '3.5.7'
$OpenSslSha256 = 'a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8'
$OpenSslUrl = "https://github.com/openssl/openssl/releases/download/openssl-$OpenSslVersion/openssl-$OpenSslVersion.tar.gz"
$AndroidNdkVersion = '23.2.8568313'
$CMakeVersion = '3.22.1'
$TargetAbi = 'arm64-v8a'
$AndroidApi = '26'
$AndroidStl = 'c++_static'
$JavaHome = 'E:\Android Studio\jbr'
$ProductionMsysRoot = 'C:\msys64'
$ProductionAndroidSdkRoot = 'E:\AndroidStudio2.0'
$ProductionBuildRoot = 'E:\tdlib-build\channel-video-flow'

function Get-NormalizedPath {
    param([Parameter(Mandatory)][string] $Path)

    if (-not [IO.Path]::IsPathRooted($Path)) {
        throw "Path must be absolute: $Path"
    }

    return [IO.Path]::GetFullPath($Path).TrimEnd([char[]]@('\', '/'))
}

function Assert-ExactPath {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][string] $Actual,
        [Parameter(Mandatory)][string[]] $Allowed
    )

    $normalizedActual = Get-NormalizedPath $Actual
    foreach ($candidate in $Allowed) {
        if ([StringComparer]::OrdinalIgnoreCase.Equals(
                $normalizedActual,
                (Get-NormalizedPath $candidate)
            )) {
            return $normalizedActual
        }
    }

    throw "$Name is outside the approved roots: $normalizedActual"
}

function Assert-File {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Name is missing."
    }
}

function Get-Sha256 {
    param([Parameter(Mandatory)][string] $Path)

    $stream = [IO.File]::OpenRead($Path)
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
        $stream.Dispose()
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][scriptblock] $Action
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $Action 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        $rendered = $output -join [Environment]::NewLine
        throw "$Name failed with exit code $exitCode.`n$rendered"
    }

    return ($output -join [Environment]::NewLine).Trim()
}

$RepositoryRoot = Get-NormalizedPath (Join-Path $PSScriptRoot '..\..')
$RepositoryBuildRoot = Get-NormalizedPath (Join-Path $RepositoryRoot '.tdlib-build\channel-video-flow')
$RepositoryNdkRoot = Get-NormalizedPath (Join-Path $RepositoryRoot ".tdlib-build\toolchain\android-sdk\ndk\$AndroidNdkVersion")
$TempToolchainRoot = Get-NormalizedPath (Join-Path ([IO.Path]::GetTempPath()) 'channel-video-flow-tdlib-toolchain')
$TempMsysRoot = Get-NormalizedPath (Join-Path $TempToolchainRoot 'msys64-portable')
$TempAndroidSdkRoot = Get-NormalizedPath (Join-Path $TempToolchainRoot 'android-sdk')
$TempPhpExe = Get-NormalizedPath (Join-Path $TempToolchainRoot 'php-8.5.8\php.exe')

$HostGit = Assert-ExactPath 'HostGit' $HostGit @('C:\Program Files\Git\cmd\git.exe')
$MsysRoot = Assert-ExactPath 'MsysRoot' $MsysRoot @($ProductionMsysRoot, $TempMsysRoot)
$ExpectedMsysBash = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\bash.exe')
$MsysBash = Assert-ExactPath 'MsysBash' $MsysBash @($ExpectedMsysBash)
$AndroidSdkRoot = Assert-ExactPath 'AndroidSdkRoot' $AndroidSdkRoot @(
    $ProductionAndroidSdkRoot,
    $TempAndroidSdkRoot
)

if ([string]::IsNullOrWhiteSpace($AndroidNdkRoot)) {
    $AndroidNdkRoot = Join-Path $AndroidSdkRoot "ndk\$AndroidNdkVersion"
}
if ([string]::IsNullOrWhiteSpace($AndroidCMakeRoot)) {
    $AndroidCMakeRoot = Join-Path $AndroidSdkRoot "cmake\$CMakeVersion"
}

$AndroidNdkRoot = Assert-ExactPath 'AndroidNdkRoot' $AndroidNdkRoot @(
    (Join-Path $AndroidSdkRoot "ndk\$AndroidNdkVersion"),
    $RepositoryNdkRoot
)
$AndroidCMakeRoot = Assert-ExactPath 'AndroidCMakeRoot' $AndroidCMakeRoot @(
    (Join-Path $AndroidSdkRoot "cmake\$CMakeVersion")
)
$PhpExe = if ([StringComparer]::OrdinalIgnoreCase.Equals($MsysRoot, $TempMsysRoot)) {
    Assert-ExactPath 'PhpExe' $PhpExe @($TempPhpExe)
} else {
    Assert-ExactPath 'PhpExe' $PhpExe @('C:\msys64\ucrt64\bin\php.exe')
}
$BuildRoot = Assert-ExactPath 'BuildRoot' $BuildRoot @($ProductionBuildRoot, $RepositoryBuildRoot)

$CMakeExe = Get-NormalizedPath (Join-Path $AndroidCMakeRoot 'bin\cmake.exe')
$NinjaExe = Get-NormalizedPath (Join-Path $AndroidCMakeRoot 'bin\ninja.exe')
$CurlExe = Get-NormalizedPath (Join-Path $env:SystemRoot 'System32\curl.exe')
$NdkProperties = Get-NormalizedPath (Join-Path $AndroidNdkRoot 'source.properties')
$AndroidToolchain = Get-NormalizedPath (Join-Path $AndroidNdkRoot 'build\cmake\android.toolchain.cmake')
$NdkClang = Get-NormalizedPath (Join-Path $AndroidNdkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe')
$NdkAr = Get-NormalizedPath (Join-Path $AndroidNdkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-ar.exe')
$NdkReadelf = Get-NormalizedPath (Join-Path $AndroidNdkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe')
$AndroidJar = Get-NormalizedPath (Join-Path $AndroidSdkRoot 'platforms\android-34\android.jar')
$JavaExe = Get-NormalizedPath (Join-Path $JavaHome 'bin\java.exe')
$JarExe = Get-NormalizedPath (Join-Path $JavaHome 'bin\jar.exe')
$JavadocExe = Get-NormalizedPath (Join-Path $JavaHome 'bin\javadoc.exe')
$BashBuildScript = Get-NormalizedPath (Join-Path $PSScriptRoot 'build-android-arm64.sh')
$ModuleRoot = Get-NormalizedPath (Join-Path $RepositoryRoot 'telegram\tdlib')
$MsysGit = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\git.exe')
$MsysPacman = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\pacman.exe')
$MsysMake = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\make.exe')
$MsysPerl = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\perl.exe')
$MsysUnzip = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\unzip.exe')
$MsysTar = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\tar.exe')
$MsysGzip = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\gzip.exe')
$MsysFind = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\find.exe')
$MsysGrep = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\grep.exe')
$MsysCp = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\cp.exe')
$MsysRm = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\rm.exe')
$MsysMkdir = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\mkdir.exe')
$MsysCygpath = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\cygpath.exe')
$MsysSed = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\sed.exe')
$MsysYes = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\yes.exe')
$MsysWget = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\wget.exe')
$MsysWhich = Get-NormalizedPath (Join-Path $MsysRoot 'usr\bin\which.exe')
$MsysGcc = Get-NormalizedPath (Join-Path $MsysRoot 'ucrt64\bin\gcc.exe')
$MsysGxx = Get-NormalizedPath (Join-Path $MsysRoot 'ucrt64\bin\g++.exe')
$MsysGperf = Get-NormalizedPath (Join-Path $MsysRoot 'ucrt64\bin\gperf.exe')

Assert-File 'MSYS2 bash' $MsysBash
Assert-File 'Git for Windows' $HostGit
Assert-File 'Android CMake' $CMakeExe
Assert-File 'Android Ninja' $NinjaExe
Assert-File 'Windows curl' $CurlExe
Assert-File 'NDK source.properties' $NdkProperties
Assert-File 'NDK Android toolchain' $AndroidToolchain
Assert-File 'NDK clang++' $NdkClang
Assert-File 'NDK llvm-ar' $NdkAr
Assert-File 'NDK llvm-readelf' $NdkReadelf
Assert-File 'android-34 android.jar' $AndroidJar
Assert-File 'PHP' $PhpExe
Assert-File 'JDK java' $JavaExe
Assert-File 'JDK jar' $JarExe
Assert-File 'JDK javadoc' $JavadocExe
Assert-File 'MSYS2 Git' $MsysGit
Assert-File 'MSYS2 pacman' $MsysPacman
Assert-File 'MSYS2 make' $MsysMake
Assert-File 'MSYS2 Perl' $MsysPerl
Assert-File 'MSYS2 unzip' $MsysUnzip
Assert-File 'MSYS2 tar' $MsysTar
Assert-File 'MSYS2 gzip' $MsysGzip
Assert-File 'MSYS2 find' $MsysFind
Assert-File 'MSYS2 grep' $MsysGrep
Assert-File 'MSYS2 cp' $MsysCp
Assert-File 'MSYS2 rm' $MsysRm
Assert-File 'MSYS2 mkdir' $MsysMkdir
Assert-File 'MSYS2 cygpath' $MsysCygpath
Assert-File 'MSYS2 sed' $MsysSed
Assert-File 'MSYS2 yes' $MsysYes
Assert-File 'MSYS2 wget' $MsysWget
Assert-File 'MSYS2 which' $MsysWhich
Assert-File 'MSYS2 UCRT64 GCC' $MsysGcc
Assert-File 'MSYS2 UCRT64 G++' $MsysGxx
Assert-File 'MSYS2 UCRT64 gperf' $MsysGperf
Assert-File 'checked-in arm64 build script' $BashBuildScript
Assert-File 'TDLib Android module build file' (Join-Path $ModuleRoot 'build.gradle.kts')
Assert-File 'TDLib Android module manifest' (Join-Path $ModuleRoot 'src\main\AndroidManifest.xml')

$NdkRevision = Select-String -LiteralPath $NdkProperties -Pattern '^Pkg.Revision\s*=\s*(.+)$' |
    Select-Object -First 1
if ($null -eq $NdkRevision -or $NdkRevision.Matches[0].Groups[1].Value.Trim() -ne $AndroidNdkVersion) {
    throw "NDK revision must be $AndroidNdkVersion."
}

$CMakeOutput = Invoke-Checked 'CMake version check' { & $CMakeExe --version }
if ($CMakeOutput -notmatch '^cmake version 3\.22\.1(?:\D|$)') {
    throw "CMake version must be $CMakeVersion."
}
$NinjaOutput = Invoke-Checked 'Ninja version check' { & $NinjaExe --version }
$CurlOutput = Invoke-Checked 'curl version check' { & $CurlExe --version }
$NdkClangOutput = Invoke-Checked 'NDK clang++ version check' { & $NdkClang --version }
$NdkArOutput = Invoke-Checked 'NDK llvm-ar version check' { & $NdkAr --version }
$JavaOutput = Invoke-Checked 'JDK version check' { & $JavaExe -version }
$PhpOutput = Invoke-Checked 'PHP version check' { & $PhpExe --version }
$GccOutput = Invoke-Checked 'MSYS2 GCC version check' { & $MsysGcc --version }
$GxxOutput = Invoke-Checked 'MSYS2 G++ version check' { & $MsysGxx --version }
$GperfOutput = Invoke-Checked 'MSYS2 gperf version check' { & $MsysGperf --version }
$MakeOutput = Invoke-Checked 'MSYS2 make version check' { & $MsysMake --version }
$PerlOutput = Invoke-Checked 'MSYS2 Perl version check' { & $MsysPerl --version }
$GitOutput = Invoke-Checked 'MSYS2 Git version check' { & $MsysGit --version }
$HostGitOutput = Invoke-Checked 'Git for Windows version check' { & $HostGit --version }
$MsysPackageOutput = Invoke-Checked 'MSYS2 direct package version check' {
    & $MsysPacman -Q `
        msys2-runtime `
        bash `
        coreutils `
        findutils `
        git `
        grep `
        gzip `
        make `
        perl `
        sed `
        tar `
        unzip `
        wget `
        which `
        mingw-w64-ucrt-x86_64-binutils `
        mingw-w64-ucrt-x86_64-cmake `
        mingw-w64-ucrt-x86_64-gcc `
        mingw-w64-ucrt-x86_64-gperf `
        mingw-w64-ucrt-x86_64-ninja
}
$UnzipOutput = Invoke-Checked 'MSYS2 unzip version check' { & $MsysUnzip -v }
$MsysToolOutput = @(
    ($GccOutput -split "`r?`n")[0]
    ($GxxOutput -split "`r?`n")[0]
    ($GperfOutput -split "`r?`n")[0]
    ($MakeOutput -split "`r?`n")[0]
    ($PerlOutput -split "`r?`n" | Where-Object { $_ -match '^This is perl ' } | Select-Object -First 1)
    $GitOutput
    ($UnzipOutput -split "`r?`n")[0]
) -join [Environment]::NewLine

New-Item -ItemType Directory -Force -Path $BuildRoot | Out-Null
$TdLibSource = Get-NormalizedPath (Join-Path $BuildRoot $TdLibCommit)
$DownloadsRoot = Get-NormalizedPath (Join-Path $BuildRoot 'downloads')
$OpenSslArchive = Get-NormalizedPath (Join-Path $DownloadsRoot "openssl-$OpenSslVersion.tar.gz")
$StagingRoot = Get-NormalizedPath (Join-Path $BuildRoot "staging-$TdLibCommit-$TargetAbi")
New-Item -ItemType Directory -Force -Path $DownloadsRoot | Out-Null

if (-not (Test-Path -LiteralPath $TdLibSource -PathType Container)) {
    Invoke-Checked 'TDLib official clone' {
        & $HostGit clone --filter=blob:none --no-checkout $TdLibRepository $TdLibSource
    } | Out-Null
}

Assert-File 'TDLib checkout .git HEAD' (Join-Path $TdLibSource '.git\HEAD')
$VerifiedRemote = Invoke-Checked 'TDLib pre-fetch remote verification' {
    & $HostGit -C $TdLibSource remote get-url origin
}
if ($VerifiedRemote -ne $TdLibRepository) {
    throw "TDLib origin does not match the pinned official remote."
}
Invoke-Checked 'TDLib fixed commit fetch' {
    & $HostGit -C $TdLibSource fetch --depth=1 origin $TdLibCommit
} | Out-Null
Invoke-Checked 'TDLib fixed commit checkout' {
    & $HostGit -C $TdLibSource checkout --detach $TdLibCommit
} | Out-Null

$VerifiedHead = Invoke-Checked 'TDLib HEAD verification' {
    & $HostGit -C $TdLibSource rev-parse HEAD
}
if ($VerifiedHead -ne $TdLibCommit) {
    throw "TDLib HEAD does not match the pinned commit."
}

$TdLibCMakeLists = Join-Path $TdLibSource 'CMakeLists.txt'
Assert-File 'TDLib root CMakeLists.txt' $TdLibCMakeLists
$TdLibCMakeProject = Get-Content -LiteralPath $TdLibCMakeLists -Raw
if ($TdLibCMakeProject -notmatch '(?m)^project\(TDLib VERSION 1\.8\.66 LANGUAGES CXX C\)\s*$') {
    throw "TDLib CMake project version must be $TdLibVersion."
}

$PreCleanSourceStatus = Invoke-Checked 'TDLib pre-clean source status check' {
    & $HostGit -C $TdLibSource status --porcelain --untracked-files=all --ignored=matching
}
$AllowedGeneratedStatus = @(
    '?? example/android/org/drinkless/tdlib/TdApi.java',
    '!! td/generate/auto/',
    '!! tdutils/generate/auto/'
)
$PreCleanSourceStatusLines = @($PreCleanSourceStatus -split "`r?`n" | Where-Object { $_ })
$UnexpectedSourceStatus = @($PreCleanSourceStatusLines | Where-Object { $_ -notin $AllowedGeneratedStatus })
if ($UnexpectedSourceStatus.Count -ne 0) {
    throw "TDLib source contains unexpected modified, untracked, or ignored files:`n$($UnexpectedSourceStatus -join [Environment]::NewLine)"
}

$TdLibSourcePrefix = $TdLibSource + [IO.Path]::DirectorySeparatorChar
foreach ($generatedRelativePath in @(
        'example\android\org',
        'td\generate\auto',
        'tdutils\generate\auto'
    )) {
    $generatedPath = Get-NormalizedPath (Join-Path $TdLibSource $generatedRelativePath)
    if (-not $generatedPath.StartsWith($TdLibSourcePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'TDLib generated source cleanup escaped the verified checkout.'
    }
    if (Test-Path -LiteralPath $generatedPath -PathType Container) {
        Remove-Item -LiteralPath $generatedPath -Recurse -Force
    }
}

$SourceStatus = Invoke-Checked 'TDLib source cleanliness check' {
    & $HostGit -C $TdLibSource status --porcelain --untracked-files=all --ignored=matching
}
if (-not [string]::IsNullOrWhiteSpace($SourceStatus)) {
    throw 'TDLib source contains modified, untracked, or ignored files.'
}

$OpenSslPart = Get-NormalizedPath "$OpenSslArchive.part"
$DownloadsPrefix = $DownloadsRoot + [IO.Path]::DirectorySeparatorChar
foreach ($downloadPath in @($OpenSslArchive, $OpenSslPart)) {
    if (-not $downloadPath.StartsWith($DownloadsPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'OpenSSL download path escaped the approved downloads root.'
    }
}

$OpenSslArchiveIsValid = $false
if (Test-Path -LiteralPath $OpenSslArchive -PathType Leaf) {
    $ExistingOpenSslSha = Get-Sha256 $OpenSslArchive
    $OpenSslArchiveIsValid = $ExistingOpenSslSha -eq $OpenSslSha256
}

if (-not $OpenSslArchiveIsValid) {
    Remove-Item -LiteralPath $OpenSslArchive, $OpenSslPart -Force -ErrorAction SilentlyContinue
    Invoke-Checked 'OpenSSL official archive download' {
        & $CurlExe `
            --fail `
            --location `
            --silent `
            --show-error `
            --retry 10 `
            --retry-delay 2 `
            --retry-all-errors `
            --connect-timeout 30 `
            --output $OpenSslPart `
            $OpenSslUrl
    } | Out-Null
    Move-Item -LiteralPath $OpenSslPart -Destination $OpenSslArchive
}
$VerifiedOpenSslSha = Get-Sha256 $OpenSslArchive
if ($VerifiedOpenSslSha -ne $OpenSslSha256) {
    throw "OpenSSL archive SHA-256 mismatch: $VerifiedOpenSslSha"
}

$env:CVF_TDLIB_VERSION = $TdLibVersion
$env:CVF_TDLIB_REPOSITORY = $TdLibRepository
$env:CVF_TDLIB_COMMIT = $TdLibCommit
$env:CVF_TDLIB_SOURCE_WINDOWS = $TdLibSource
$env:CVF_OPENSSL_VERSION = $OpenSslVersion
$env:CVF_OPENSSL_ARCHIVE_WINDOWS = $OpenSslArchive
$env:CVF_BUILD_ROOT_WINDOWS = $BuildRoot
$env:CVF_STAGING_ROOT_WINDOWS = $StagingRoot
$env:CVF_ANDROID_SDK_ROOT_WINDOWS = $AndroidSdkRoot
$env:CVF_ANDROID_NDK_ROOT_WINDOWS = $AndroidNdkRoot
$env:CVF_ANDROID_CMAKE_ROOT_WINDOWS = $AndroidCMakeRoot
$env:CVF_PHP_EXE_WINDOWS = $PhpExe
$env:CVF_BUILD_SCRIPT_WINDOWS = $BashBuildScript
$env:CVF_TARGET_ABI = $TargetAbi
$env:CVF_ANDROID_API = $AndroidApi
$env:CVF_ANDROID_STL = $AndroidStl
$env:MSYSTEM = 'UCRT64'
$env:CHERE_INVOKING = '1'
$env:MSYS2_PATH_TYPE = 'inherit'
$env:JAVA_HOME = $JavaHome
$env:PATH = "$(Join-Path $JavaHome 'bin');$env:PATH"

Invoke-Checked 'TDLib arm64-v8a native build' {
    & $MsysBash $BashBuildScript
} | Out-Null

$StagedClient = Get-NormalizedPath (Join-Path $StagingRoot 'java\org\drinkless\tdlib\Client.java')
$StagedTdApi = Get-NormalizedPath (Join-Path $StagingRoot 'java\org\drinkless\tdlib\TdApi.java')
$StagedSo = Get-NormalizedPath (Join-Path $StagingRoot 'jniLibs\arm64-v8a\libtdjni.so')
$StagedTdLicense = Get-NormalizedPath (Join-Path $StagingRoot 'licenses\LICENSE_1_0.txt')
$StagedOpenSslLicense = Get-NormalizedPath (Join-Path $StagingRoot 'licenses\LICENSE_OPENSSL.txt')

Assert-File 'staged Client.java' $StagedClient
Assert-File 'staged TdApi.java' $StagedTdApi
Assert-File 'staged libtdjni.so' $StagedSo
Assert-File 'staged TDLib license' $StagedTdLicense
Assert-File 'staged OpenSSL license' $StagedOpenSslLicense

$StagedAbiDirectories = @(Get-ChildItem -LiteralPath (Join-Path $StagingRoot 'jniLibs') -Directory)
if ($StagedAbiDirectories.Count -ne 1 -or $StagedAbiDirectories[0].Name -ne $TargetAbi) {
    throw 'Staging contains an ABI other than arm64-v8a.'
}

$ModuleJavaRoot = Join-Path $ModuleRoot 'src\main\java\org\drinkless\tdlib'
$ModuleJniRoot = Join-Path $ModuleRoot 'src\main\jniLibs\arm64-v8a'
$ModuleLicensesRoot = Join-Path $ModuleRoot 'licenses'
New-Item -ItemType Directory -Force -Path $ModuleJavaRoot, $ModuleJniRoot, $ModuleLicensesRoot | Out-Null

$ExistingAbiRoot = Join-Path $ModuleRoot 'src\main\jniLibs'
if (Test-Path -LiteralPath $ExistingAbiRoot -PathType Container) {
    $UnexpectedAbis = @(Get-ChildItem -LiteralPath $ExistingAbiRoot -Directory |
        Where-Object { $_.Name -ne $TargetAbi })
    if ($UnexpectedAbis.Count -ne 0) {
        throw 'The TDLib module already contains an ABI other than arm64-v8a.'
    }

    $ExpectedModuleSo = Get-NormalizedPath (Join-Path $ModuleJniRoot 'libtdjni.so')
    $UnexpectedJniFiles = @(Get-ChildItem -LiteralPath $ExistingAbiRoot -Recurse -File |
        Where-Object {
            -not [StringComparer]::OrdinalIgnoreCase.Equals(
                (Get-NormalizedPath $_.FullName),
                $ExpectedModuleSo
            )
        })
    if ($UnexpectedJniFiles.Count -ne 0) {
        throw 'The TDLib module contains an unexpected JNI file.'
    }
} else {
    $ExpectedModuleSo = Get-NormalizedPath (Join-Path $ModuleJniRoot 'libtdjni.so')
}

Copy-Item -LiteralPath $StagedClient -Destination (Join-Path $ModuleJavaRoot 'Client.java') -Force
Copy-Item -LiteralPath $StagedTdApi -Destination (Join-Path $ModuleJavaRoot 'TdApi.java') -Force
Copy-Item -LiteralPath $StagedSo -Destination (Join-Path $ModuleJniRoot 'libtdjni.so') -Force
Copy-Item -LiteralPath $StagedTdLicense -Destination (Join-Path $ModuleLicensesRoot 'LICENSE_1_0.txt') -Force
Copy-Item -LiteralPath $StagedOpenSslLicense -Destination (Join-Path $ModuleLicensesRoot 'LICENSE_OPENSSL.txt') -Force

$ModuleClient = Join-Path $ModuleJavaRoot 'Client.java'
$ModuleTdApi = Join-Path $ModuleJavaRoot 'TdApi.java'
$ModuleSo = $ExpectedModuleSo
$ModuleJniFiles = @(Get-ChildItem -LiteralPath $ExistingAbiRoot -Recurse -File)
if ($ModuleJniFiles.Count -ne 1 -or
    -not [StringComparer]::OrdinalIgnoreCase.Equals(
        (Get-NormalizedPath $ModuleJniFiles[0].FullName),
        $ModuleSo
    )) {
    throw 'The TDLib module JNI tree must contain only arm64-v8a/libtdjni.so.'
}
$ClientSha = Get-Sha256 $ModuleClient
$TdApiSha = Get-Sha256 $ModuleTdApi
$SoSha = Get-Sha256 $ModuleSo

$BuildCommand = @"
powershell -ExecutionPolicy Bypass -File .\tools\tdlib\build-android.ps1 ``
  -HostGit '$HostGit' ``
  -MsysRoot '$MsysRoot' ``
  -MsysBash '$MsysBash' ``
  -AndroidSdkRoot '$AndroidSdkRoot' ``
  -AndroidNdkRoot '$AndroidNdkRoot' ``
  -AndroidCMakeRoot '$AndroidCMakeRoot' ``
  -PhpExe '$PhpExe' ``
  -BuildRoot '$BuildRoot'
"@.Trim()

$Provenance = @"
# TDLib provenance

This module contains only the official Java/JNI TDLib interface built locally for this application.

## Pinned supply chain

- TDLib repository: $TdLibRepository
- TDLib commit: $TdLibCommit
- TDLib version: $TdLibVersion
- Verified origin: $VerifiedRemote
- Verified HEAD: $VerifiedHead
- OpenSSL source: $OpenSslUrl
- OpenSSL version: $OpenSslVersion LTS
- OpenSSL archive SHA-256: $VerifiedOpenSslSha
- Android NDK: $AndroidNdkVersion
- Android CMake: $CMakeVersion
- ABI: $TargetAbi only
- Android native API: $AndroidApi
- STL: $AndroidStl

## Paths

- Production build root: $ProductionBuildRoot
- Production MSYS2 root: $ProductionMsysRoot
- Production Android SDK root: $ProductionAndroidSdkRoot
- Actual build root: $BuildRoot
- Actual Git for Windows: $HostGit
- Actual MSYS2 root: $MsysRoot
- Actual Android SDK root: $AndroidSdkRoot
- Actual Android NDK root: $AndroidNdkRoot
- Actual Android CMake root: $AndroidCMakeRoot
- Actual PHP executable: $PhpExe

## Tool evidence

~~~text
$CMakeOutput
Ninja $NinjaOutput
$CurlOutput
$NdkClangOutput
$NdkArOutput
$HostGitOutput
$JavaOutput
$PhpOutput
$MsysToolOutput
$MsysPackageOutput
~~~

## Build command

~~~powershell
$BuildCommand
~~~

The checked-in Bash script performs the official environment check, prepares host code generation, builds OpenSSL statically for android-arm64, invokes TDLib's tl_generate_java preparation and AddIntDef.php, and builds only tdjni for arm64-v8a.

## Output hashes

- src/main/java/org/drinkless/tdlib/Client.java: $ClientSha
- src/main/java/org/drinkless/tdlib/TdApi.java: $TdApiSha
- src/main/jniLibs/arm64-v8a/libtdjni.so: $SoSha

## Licenses

- TDLib: Boost Software License 1.0, copied to licenses/LICENSE_1_0.txt from the pinned checkout.
- OpenSSL: Apache License 2.0, copied to licenses/LICENSE_OPENSSL.txt from the verified OpenSSL 3.5.7 source archive.
"@

$ProvenancePath = Join-Path $ModuleRoot 'TDLIB_PROVENANCE.md'
$Utf8NoBom = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($ProvenancePath, ($Provenance.TrimEnd() + [Environment]::NewLine), $Utf8NoBom)

Write-Host "TDLib remote: $VerifiedRemote"
Write-Host "TDLib HEAD: $VerifiedHead"
Write-Host "TDLib version: $TdLibVersion"
Write-Host "OpenSSL SHA-256: $VerifiedOpenSslSha"
Write-Host "ABI/API/STL: $TargetAbi/android-$AndroidApi/$AndroidStl"
Write-Host "Client.java SHA-256: $ClientSha"
Write-Host "TdApi.java SHA-256: $TdApiSha"
Write-Host "libtdjni.so SHA-256: $SoSha"
