#!/usr/bin/env bash
set -euo pipefail

readonly TDLIB_REPOSITORY='https://github.com/tdlib/td.git'
readonly TDLIB_COMMIT='022d60202e446ad1287b9fb68e687c8a0760788b'
readonly TDLIB_VERSION='1.8.66'
readonly OPENSSL_VERSION='3.5.7'
readonly OPENSSL_SHA256='a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8'
readonly TARGET_ABI='arm64-v8a'
readonly OPENSSL_TARGET='android-arm64'
readonly ANDROID_API='26'
readonly ANDROID_NDK_VERSION='23.2.8568313'
readonly CMAKE_VERSION='3.22.1'
readonly TDLIB_INTERFACE='Java'
readonly ANDROID_STL='c++_static'
readonly BUILD_JOBS='8'

export PATH="/ucrt64/bin:/usr/bin:${PATH:-}"

fail() {
  printf 'TDLib build error: %s\n' "$1" >&2
  exit 1
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "required environment variable $name is missing"
}

require_file() {
  [[ -f "$1" ]] || fail "required file is missing: $2"
}

require_dir() {
  [[ -d "$1" ]] || fail "required directory is missing: $2"
}

to_msys_path() {
  cygpath -u "$1"
}

for required_name in \
  CVF_TDLIB_REPOSITORY \
  CVF_TDLIB_COMMIT \
  CVF_TDLIB_VERSION \
  CVF_TDLIB_SOURCE_WINDOWS \
  CVF_OPENSSL_VERSION \
  CVF_OPENSSL_ARCHIVE_WINDOWS \
  CVF_BUILD_ROOT_WINDOWS \
  CVF_STAGING_ROOT_WINDOWS \
  CVF_ANDROID_SDK_ROOT_WINDOWS \
  CVF_ANDROID_NDK_ROOT_WINDOWS \
  CVF_ANDROID_CMAKE_ROOT_WINDOWS \
  CVF_PHP_EXE_WINDOWS \
  CVF_TARGET_ABI \
  CVF_ANDROID_API \
  CVF_ANDROID_STL; do
  require_env "$required_name"
done

[[ "${MSYSTEM:-}" == 'UCRT64' ]] || fail 'MSYS2 shell must run with MSYSTEM=UCRT64'
[[ "$TDLIB_INTERFACE" == 'Java' ]] || fail 'TDLib interface must be Java'
[[ "$CVF_TDLIB_REPOSITORY" == "$TDLIB_REPOSITORY" ]] || fail 'TDLib remote is not pinned'
[[ "$CVF_TDLIB_COMMIT" == "$TDLIB_COMMIT" ]] || fail 'TDLib commit is not pinned'
[[ "$CVF_TDLIB_VERSION" == "$TDLIB_VERSION" ]] || fail 'TDLib version is not pinned'
[[ "$CVF_OPENSSL_VERSION" == "$OPENSSL_VERSION" ]] || fail 'OpenSSL version is not pinned'
[[ "$CVF_TARGET_ABI" == "$TARGET_ABI" ]] || fail 'only arm64-v8a is permitted'
[[ "$CVF_ANDROID_API" == "$ANDROID_API" ]] || fail 'Android API must be 26'
[[ "$CVF_ANDROID_STL" == "$ANDROID_STL" ]] || fail 'Android STL must be c++_static'

TDLIB_SOURCE="$(to_msys_path "$CVF_TDLIB_SOURCE_WINDOWS")"
OPENSSL_ARCHIVE="$(to_msys_path "$CVF_OPENSSL_ARCHIVE_WINDOWS")"
BUILD_ROOT="$(to_msys_path "$CVF_BUILD_ROOT_WINDOWS")"
STAGING_ROOT="$(to_msys_path "$CVF_STAGING_ROOT_WINDOWS")"
ANDROID_SDK_ROOT="$(to_msys_path "$CVF_ANDROID_SDK_ROOT_WINDOWS")"
ANDROID_NDK_ROOT="$(to_msys_path "$CVF_ANDROID_NDK_ROOT_WINDOWS")"
ANDROID_CMAKE_ROOT="$(to_msys_path "$CVF_ANDROID_CMAKE_ROOT_WINDOWS")"
PHP_EXE="$(to_msys_path "$CVF_PHP_EXE_WINDOWS")"

readonly TDLIB_SOURCE OPENSSL_ARCHIVE BUILD_ROOT STAGING_ROOT
readonly ANDROID_SDK_ROOT ANDROID_NDK_ROOT ANDROID_CMAKE_ROOT PHP_EXE

readonly CMAKE_EXE="$ANDROID_CMAKE_ROOT/bin/cmake.exe"
readonly NINJA_EXE="$ANDROID_CMAKE_ROOT/bin/ninja.exe"
readonly TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake"
readonly LLVM_BIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/windows-x86_64/bin"
readonly READELF_EXE="$LLVM_BIN/llvm-readelf.exe"
readonly OPENSSL_SOURCE_ROOT="$BUILD_ROOT/sources/openssl-$OPENSSL_VERSION"
readonly OPENSSL_BUILD_DIR="$BUILD_ROOT/build-openssl-$OPENSSL_VERSION-$TARGET_ABI"
readonly OPENSSL_INSTALL_DIR="$BUILD_ROOT/install-openssl-$OPENSSL_VERSION/$TARGET_ABI"
readonly HOST_BUILD_DIR="$BUILD_ROOT/build-tdlib-host-$TDLIB_COMMIT"
readonly ANDROID_BUILD_DIR="$BUILD_ROOT/build-tdlib-android-$TDLIB_COMMIT-$TARGET_ABI"
readonly OFFICIAL_ENV_CHECK="$TDLIB_SOURCE/example/android/check-environment.sh"
readonly ADD_INT_DEF="$TDLIB_SOURCE/example/android/AddIntDef.php"
readonly CLIENT_SOURCE="$TDLIB_SOURCE/example/java/org/drinkless/tdlib/Client.java"
readonly GENERATED_TD_API="$TDLIB_SOURCE/example/android/org/drinkless/tdlib/TdApi.java"

case "$STAGING_ROOT" in
  "$BUILD_ROOT"/staging-*) ;;
  *) fail 'staging root must stay under the approved build root' ;;
esac
case "$OPENSSL_SOURCE_ROOT" in
  "$BUILD_ROOT"/sources/openssl-*) ;;
  *) fail 'OpenSSL source root must stay under the approved build root' ;;
esac

require_dir "$TDLIB_SOURCE/.git" 'TDLib .git directory'
require_file "$OPENSSL_ARCHIVE" 'verified OpenSSL archive'
require_file "$CMAKE_EXE" 'Android CMake 3.22.1'
require_file "$NINJA_EXE" 'Android Ninja'
require_file "$TOOLCHAIN_FILE" 'NDK CMake toolchain'
require_file "$READELF_EXE" 'NDK llvm-readelf'
require_file "$PHP_EXE" 'PHP executable'
require_file "$OFFICIAL_ENV_CHECK" 'official TDLib Android environment check'
require_file "$ADD_INT_DEF" 'official TDLib AddIntDef.php'
require_file "$CLIENT_SOURCE" 'official TDLib Client.java'

export PATH="/ucrt64/bin:/usr/bin:$(dirname "$PHP_EXE"):$LLVM_BIN:$PATH"
export ANDROID_SDK_ROOT ANDROID_NDK_ROOT

[[ "$(git -C "$TDLIB_SOURCE" remote get-url origin)" == "$TDLIB_REPOSITORY" ]] ||
  fail 'TDLib origin verification failed'
[[ "$(git -C "$TDLIB_SOURCE" rev-parse HEAD)" == "$TDLIB_COMMIT" ]] ||
  fail 'TDLib HEAD verification failed'
pre_build_source_status="$(
  git -C "$TDLIB_SOURCE" status --porcelain --untracked-files=all --ignored=matching
)" || fail 'TDLib pre-build source status command failed'
[[ -z "$pre_build_source_status" ]] ||
  fail 'TDLib source contains modified, untracked, or ignored files'

(
  cd "$TDLIB_SOURCE/example/android"
  export OSTYPE=msys
  bash "$OFFICIAL_ENV_CHECK"
)

for disposable_dir in \
  "$OPENSSL_SOURCE_ROOT" \
  "$OPENSSL_BUILD_DIR" \
  "$OPENSSL_INSTALL_DIR" \
  "$HOST_BUILD_DIR" \
  "$ANDROID_BUILD_DIR" \
  "$STAGING_ROOT"; do
  case "$disposable_dir" in
    "$BUILD_ROOT"/*) ;;
    *) fail 'disposable build directory escaped the approved build root' ;;
  esac
done

rm -rf \
  "$OPENSSL_SOURCE_ROOT" \
  "$OPENSSL_BUILD_DIR" \
  "$OPENSSL_INSTALL_DIR" \
  "$HOST_BUILD_DIR" \
  "$ANDROID_BUILD_DIR" \
  "$STAGING_ROOT"
mkdir -p "$BUILD_ROOT/sources" "$OPENSSL_BUILD_DIR" "$OPENSSL_INSTALL_DIR"
tar -xzf "$OPENSSL_ARCHIVE" -C "$BUILD_ROOT/sources"
require_file "$OPENSSL_SOURCE_ROOT/Configure" 'OpenSSL Configure script'

(
  cd "$OPENSSL_BUILD_DIR"
  perl "$OPENSSL_SOURCE_ROOT/Configure" \
    "$OPENSSL_TARGET" \
    -D__ANDROID_API__="$ANDROID_API" \
    no-shared \
    no-tests \
    --prefix="$OPENSSL_INSTALL_DIR" \
    --openssldir="$OPENSSL_INSTALL_DIR/ssl"
  grep -Eq '^CPPFLAGS=-D__ANDROID_API__=26$' Makefile ||
    fail 'OpenSSL Makefile does not prove Android API 26'
  make -j"$BUILD_JOBS"
  make install_sw
)
require_file "$OPENSSL_INSTALL_DIR/lib/libcrypto.a" 'arm64 OpenSSL libcrypto.a'
require_file "$OPENSSL_INSTALL_DIR/lib/libssl.a" 'arm64 OpenSSL libssl.a'

"$CMAKE_EXE" \
  -S "$TDLIB_SOURCE/example/android" \
  -B "$HOST_BUILD_DIR" \
  -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$NINJA_EXE" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER=gcc \
  -DCMAKE_CXX_COMPILER=g++ \
  -DTD_GENERATE_SOURCE_FILES=ON
"$CMAKE_EXE" --build "$HOST_BUILD_DIR" --parallel "$BUILD_JOBS"
"$CMAKE_EXE" --build "$HOST_BUILD_DIR" --target tl_generate_java --parallel "$BUILD_JOBS"
require_file "$GENERATED_TD_API" 'generated TdApi.java'

"$CMAKE_EXE" \
  -S "$TDLIB_SOURCE/example/android" \
  -B "$ANDROID_BUILD_DIR" \
  -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$NINJA_EXE" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
  -DOPENSSL_ROOT_DIR="$OPENSSL_INSTALL_DIR" \
  -DOPENSSL_INCLUDE_DIR="$OPENSSL_INSTALL_DIR/include" \
  -DOPENSSL_CRYPTO_LIBRARY="$OPENSSL_INSTALL_DIR/lib/libcrypto.a" \
  -DOPENSSL_SSL_LIBRARY="$OPENSSL_INSTALL_DIR/lib/libssl.a" \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DANDROID_ABI="$TARGET_ABI" \
  -DANDROID_STL="$ANDROID_STL" \
  -DANDROID_PLATFORM="android-$ANDROID_API" \
  -DTD_ENABLE_JNI=ON
"$CMAKE_EXE" --build "$ANDROID_BUILD_DIR" --target tdjni --parallel "$BUILD_JOBS"
grep -Eq '^ANDROID_STL(:[^=]+)?=c\+\+_static$' "$ANDROID_BUILD_DIR/CMakeCache.txt" ||
  fail 'CMake cache does not prove c++_static'
grep -Eq '^ANDROID_ABI(:[^=]+)?=arm64-v8a$' "$ANDROID_BUILD_DIR/CMakeCache.txt" ||
  fail 'CMake cache does not prove arm64-v8a'
grep -Eq '^ANDROID_PLATFORM(:[^=]+)?=android-26$' "$ANDROID_BUILD_DIR/CMakeCache.txt" ||
  fail 'CMake cache does not prove Android API 26'

mapfile -t tdjni_outputs < <(find "$ANDROID_BUILD_DIR" -type f -name 'libtdjni.so')
[[ "${#tdjni_outputs[@]}" -eq 1 ]] || fail 'native build must produce exactly one libtdjni.so'
"$READELF_EXE" -h "${tdjni_outputs[0]}" | grep -Eq 'Machine:[[:space:]]+AArch64' ||
  fail 'libtdjni.so is not an AArch64 ELF'
tdjni_dynamic_section="$("$READELF_EXE" -d "${tdjni_outputs[0]}")" ||
  fail 'llvm-readelf failed to inspect libtdjni.so dynamic dependencies'
if grep -Fq 'libc++_shared.so' <<< "$tdjni_dynamic_section"; then
  fail 'libtdjni.so unexpectedly depends on libc++_shared.so'
fi

post_build_source_status="$(
  git -C "$TDLIB_SOURCE" status --porcelain --untracked-files=all --ignored=matching
)" || fail 'TDLib post-build source status command failed'
while IFS= read -r source_status_line; do
  [[ -z "$source_status_line" ]] && continue
  case "$source_status_line" in
    '?? example/android/org/drinkless/tdlib/TdApi.java' | \
    '!! td/generate/auto/' | \
    '!! tdutils/generate/auto/') ;;
    *) fail "TDLib build produced an unexpected source-tree change: $source_status_line" ;;
  esac
done <<< "$post_build_source_status"

mkdir -p \
  "$STAGING_ROOT/java/org/drinkless/tdlib" \
  "$STAGING_ROOT/jniLibs/$TARGET_ABI" \
  "$STAGING_ROOT/licenses"
cp "$CLIENT_SOURCE" "$STAGING_ROOT/java/org/drinkless/tdlib/Client.java"
cp "$GENERATED_TD_API" "$STAGING_ROOT/java/org/drinkless/tdlib/TdApi.java"
"$PHP_EXE" "$ADD_INT_DEF" "$STAGING_ROOT/java/org/drinkless/tdlib/TdApi.java"
cp "${tdjni_outputs[0]}" "$STAGING_ROOT/jniLibs/$TARGET_ABI/libtdjni.so"
cp "$TDLIB_SOURCE/LICENSE_1_0.txt" "$STAGING_ROOT/licenses/LICENSE_1_0.txt"

if [[ -f "$OPENSSL_SOURCE_ROOT/LICENSE.txt" ]]; then
  cp "$OPENSSL_SOURCE_ROOT/LICENSE.txt" "$STAGING_ROOT/licenses/LICENSE_OPENSSL.txt"
elif [[ -f "$OPENSSL_SOURCE_ROOT/LICENSE" ]]; then
  cp "$OPENSSL_SOURCE_ROOT/LICENSE" "$STAGING_ROOT/licenses/LICENSE_OPENSSL.txt"
else
  fail 'OpenSSL license file is missing'
fi

mapfile -t staged_abis < <(find "$STAGING_ROOT/jniLibs" -mindepth 1 -maxdepth 1 -type d)
[[ "${#staged_abis[@]}" -eq 1 ]] || fail 'staging must contain exactly one ABI directory'
[[ "$(basename "${staged_abis[0]}")" == "$TARGET_ABI" ]] || fail 'unexpected staged ABI'

printf 'TDLib %s (%s) built for %s android-%s using %s.\n' \
  "$TDLIB_VERSION" "$TDLIB_COMMIT" "$TARGET_ABI" "$ANDROID_API" "$ANDROID_STL"
