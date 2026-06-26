# Bundled native binaries - provenance

These are prebuilt third-party binaries vendored into `jniLibs` (the only place Android lets an app
exec a binary from - see `EnvironmentBootstrap`). Each is verifiable against the source below: re-fetch
from the pinned commit and compare the SHA-256.

## proot (Linux userland via PRoot) - arm64-v8a only

- **What:** `libproot.so` is PRoot (userspace chroot+bind via ptrace); `libproot-loader.so` is its
  statically-linked loader. Together they run a full Alpine Linux rootfs on-device under Android's W^X
  (`mmap PROT_EXEC` survives where `execve` is denied), so `apk add python3 ...` works.
- **Source:** https://github.com/green-green-avk/build-proot-android (the PRoot build behind the
  AnotherTerm terminal app's "Linux under PRoot" feature).
- **Pinned commit:** `01f83b8841358450c78333d1b33ab30d4943bec4` (2023-06-23), `packages/proot-android-aarch64.tar.gz`.
- **Files + SHA-256 (verified at vendor time):**
  - `root/bin/proot` -> `arm64-v8a/libproot.so` -> `297abc237247682a84a3fd4283b28f69506502b4b852faf71fd726fb5d955d60`
  - `root/libexec/proot/loader` -> `arm64-v8a/libproot-loader.so` -> `de0ace1adc76ab0555b3dbbfbff0e78c1ac14017b255262707867aea70c49437`
- **Licenses:** PRoot (GPL-2.0), talloc (LGPL-3.0) - see the source repo.
- **Scope:** arm64-v8a only. Other ABIs intentionally have no proot and fall back to busybox at runtime
  (`EnvironmentBootstrap.buildLinux` returns null), keeping the unaudited-binary surface minimal.
- **Residual risk:** this is a prebuilt binary, not built from source here, so its bytes are trusted on
  the pin + hash, not independently audited. To harden a release, rebuild from the source repo's scripts.

To re-verify:

    curl -fsSL https://raw.githubusercontent.com/green-green-avk/build-proot-android/01f83b8841358450c78333d1b33ab30d4943bec4/packages/proot-android-aarch64.tar.gz | tar -xz
    shasum -a 256 root/bin/proot root/libexec/proot/loader
