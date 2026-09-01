# License staging

`build-android.ps1` copies the exact TDLib and OpenSSL license files here only after
the official TDLib checkout and the SHA-256-verified OpenSSL source archive have both
passed validation. They are not synthesized or downloaded from a third party.

The absence of `LICENSE_1_0.txt` and `LICENSE_OPENSSL.txt` means the native supply
chain build has not completed and must not be reported as verified.
