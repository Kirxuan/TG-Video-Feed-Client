# TDLib provenance

This module contains only the official Java/JNI TDLib interface built locally for this application.

## Pinned supply chain

- TDLib repository: https://github.com/tdlib/td.git
- TDLib commit: 022d60202e446ad1287b9fb68e687c8a0760788b
- TDLib version: 1.8.66
- Verified origin: https://github.com/tdlib/td.git
- Verified HEAD: 022d60202e446ad1287b9fb68e687c8a0760788b
- OpenSSL source: https://github.com/openssl/openssl/releases/download/openssl-3.5.7/openssl-3.5.7.tar.gz
- OpenSSL version: 3.5.7 LTS
- OpenSSL archive SHA-256: a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8
- Android NDK: 23.2.8568313
- Android CMake: 3.22.1
- ABI: arm64-v8a only
- Android native API: 26
- STL: c++_static

## Paths

Public documentation normalizes the Windows profile directory through `%LOCALAPPDATA%`; hashes, versions and tool outputs are unchanged.

- Production build root: E:\tdlib-build\channel-video-flow
- Production MSYS2 root: C:\msys64
- Production Android SDK root: E:\AndroidStudio2.0
- Actual build root: E:\tdlib-build\channel-video-flow
- Actual Git for Windows: C:\Program Files\Git\cmd\git.exe
- Actual MSYS2 root: %LOCALAPPDATA%\Temp\channel-video-flow-tdlib-toolchain\msys64-portable
- Actual Android SDK root: E:\AndroidStudio2.0
- Actual Android NDK root: E:\AndroidStudio2.0\ndk\23.2.8568313
- Actual Android CMake root: E:\AndroidStudio2.0\cmake\3.22.1
- Actual PHP executable: %LOCALAPPDATA%\Temp\channel-video-flow-tdlib-toolchain\php-8.5.8\php.exe

## Tool evidence

~~~text
cmake version 3.22.1-g37088a8-dirty

CMake suite maintained and supported by Kitware (kitware.com/cmake).
Ninja 1.10.2
curl 8.21.0 (Windows) libcurl/8.21.0 Schannel zlib/1.3.2 WinIDN WinLDAP
Release-Date: 2026-06-24
Protocols: dict file ftp ftps gopher gophers http https imap imaps ipfs ipns ldap ldaps mqtt mqtts pop3 pop3s smtp smtps telnet tftp ws wss
Features: alt-svc AsynchDNS HSTS HTTPS-proxy IDN IPv6 Kerberos Largefile libz SPNEGO SSL SSPI threadsafe Unicode UnixSockets
Android (8481493, based on r416183c2) clang version 12.0.9 (https://android.googlesource.com/toolchain/llvm-project c935d99d7cf2016289302412d708641d52d2f7ee)
Target: x86_64-w64-windows-gnu
Thread model: posix
InstalledDir: E:\AndroidStudio2.0\ndk\23.2.8568313\toolchains\llvm\prebuilt\windows-x86_64\bin
LLVM (http://llvm.org/):
  LLVM version 12.0.9git
  Optimized build.
  Default target: x86_64-w64-windows-gnu
  Host CPU: znver3
git version 2.54.0.windows.1
Picked up JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+-14961533-b1163.108)
OpenJDK 64-Bit Server VM (build 21.0.10+-14961533-b1163.108, mixed mode)
PHP 8.5.8 (cli) (built: Jul  1 2026 04:03:04) (NTS Visual C++ 2022 x64)
Copyright (c) The PHP Group
Built by The PHP Group
Zend Engine v4.5.8, Copyright (c) Zend Technologies
    with Zend OPcache v8.5.8, Copyright (c), by Zend Technologies
gcc.exe (Rev5, Built by MSYS2 project) 16.1.0
g++.exe (Rev5, Built by MSYS2 project) 16.1.0
GNU gperf 3.3
GNU Make 4.4.1
This is perl 5, version 42, subversion 2 (v5.42.2) built for x86_64-cygwin-thread-multi
git version 2.55.0
UnZip 6.00 of 20 April 2009, by Info-ZIP.  Maintained by C. Spieler.  Send
msys2-runtime 3.6.10-1
bash 5.3.015-1
coreutils 8.32-5
findutils 4.10.0-3
git 2.55.0-1
grep 1~3.0-7
gzip 1.14-2
make 4.4.1-3
perl 5.42.2-1
sed 4.9-1
tar 1.35-3
unzip 6.0-3
wget 1.25.0-2
which 2.25-1
mingw-w64-ucrt-x86_64-binutils 2.46.1-2
mingw-w64-ucrt-x86_64-cmake 4.4.0-1
mingw-w64-ucrt-x86_64-gcc 16.1.0-5
mingw-w64-ucrt-x86_64-gperf 3.3-1
mingw-w64-ucrt-x86_64-ninja 1.13.2-1
~~~

## Build command

~~~powershell
powershell -ExecutionPolicy Bypass -File .\tools\tdlib\build-android.ps1 `
  -HostGit 'C:\Program Files\Git\cmd\git.exe' `
  -MsysRoot "$env:LOCALAPPDATA\Temp\channel-video-flow-tdlib-toolchain\msys64-portable" `
  -MsysBash "$env:LOCALAPPDATA\Temp\channel-video-flow-tdlib-toolchain\msys64-portable\usr\bin\bash.exe" `
  -AndroidSdkRoot 'E:\AndroidStudio2.0' `
  -AndroidNdkRoot 'E:\AndroidStudio2.0\ndk\23.2.8568313' `
  -AndroidCMakeRoot 'E:\AndroidStudio2.0\cmake\3.22.1' `
  -PhpExe "$env:LOCALAPPDATA\Temp\channel-video-flow-tdlib-toolchain\php-8.5.8\php.exe" `
  -BuildRoot 'E:\tdlib-build\channel-video-flow'
~~~

The checked-in Bash script performs the official environment check, prepares host code generation, builds OpenSSL statically for android-arm64, invokes TDLib's tl_generate_java preparation and AddIntDef.php, and builds only tdjni for arm64-v8a.

## Output hashes

- src/main/java/org/drinkless/tdlib/Client.java: 5b30cb91dc25eb26b5dd93622974cec7024a0d87d81714965ae0416054347b26
- src/main/java/org/drinkless/tdlib/TdApi.java: 9a462ae179b8d8ff90bda85d56f6ac526d63ff56670f492bf8e86783fd5edc55
- src/main/jniLibs/arm64-v8a/libtdjni.so: 7e07cd3b069639bb0c5db094d4cf081526d3c06db81b3ebe9f9bda65ddee84e3

## Licenses

- TDLib: Boost Software License 1.0, copied to licenses/LICENSE_1_0.txt from the pinned checkout.
- OpenSSL: Apache License 2.0, copied to licenses/LICENSE_OPENSSL.txt from the verified OpenSSL 3.5.7 source archive.
