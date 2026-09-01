# Third-party notices

VELORA contains or depends on third-party software. Those components remain under their own licenses; the repository-level Apache License 2.0 does not replace those terms.

## TDLib

- Project: Telegram Database Library (TDLib)
- Source: <https://github.com/tdlib/td>
- Pinned version: 1.8.66
- Pinned commit: `022d60202e446ad1287b9fb68e687c8a0760788b`
- License: Boost Software License 1.0
- License copy: [telegram/tdlib/licenses/LICENSE_1_0.txt](telegram/tdlib/licenses/LICENSE_1_0.txt)

The checked-in Java bindings and `libtdjni.so` are reproducible artifacts from the pinned official source. Provenance and hashes are documented in [telegram/tdlib/TDLIB_PROVENANCE.md](telegram/tdlib/TDLIB_PROVENANCE.md).

## OpenSSL

- Project: OpenSSL
- Source: <https://www.openssl.org/>
- Pinned version: 3.5.7 LTS
- License: Apache License 2.0
- License copy: [telegram/tdlib/licenses/LICENSE_OPENSSL.txt](telegram/tdlib/licenses/LICENSE_OPENSSL.txt)

OpenSSL is statically used by the checked-in TDLib native build. The verified source archive hash and rebuild instructions are documented in [docs/TDLIB_BUILD.md](docs/TDLIB_BUILD.md).

## Android and JVM dependencies

AndroidX, Jetpack Compose, Media3, Room, DataStore, Hilt, Kotlin, Coroutines, JUnit and Robolectric are resolved from their official Maven repositories by Gradle. Their versions are pinned in [gradle/libs.versions.toml](gradle/libs.versions.toml), and each remains subject to its upstream license.
