# On-device development backend

PhoneCode will ship one global Google Play application and execute development work on the phone. It
will not provide, require, or fall back to remote execution.

## Release boundary

Google Play prohibits downloading executable code outside Play unless that code runs inside a real
virtual machine or interpreter. PRoot translates Linux paths and syscalls but executes guest ELF
code natively under PhoneCode's Android UID. It is neither a VM nor a security boundary.

The bundled PRoot and Alpine environment is therefore a development prototype, not the Google Play
release architecture. Arbitrary `apk`, native `pip` wheels, native npm modules, downloaded ELF files,
and shared libraries must not ship as a claimed Play-safe capability. A release cannot rely on
foreground-service classification, disclosure, or user consent to bypass this rule.

## Target runtime

The production runtime is a software-emulated QEMU system VM using TCG. QEMU, the Linux kernel,
initramfs, trusted guest daemon, and base image are delivered in the signed app and updated only
through Google Play. Downloaded Linux packages execute only on QEMU's virtual CPU and receive no
Android API bridge.

The first supported host architectures are arm64-v8a and x86_64. Each app split carries the matching
QEMU host executable and Alpine guest. Android Virtualization Framework is not available to ordinary
third-party apps, so it cannot be the primary or fallback backend. WASM Linux remains a research
option, not a production dependency.

## Isolation

QEMU runs in an Android isolated-process service with no app permissions. The main process passes
only explicit file descriptors for the kernel, initramfs, disks, console, QMP, guest-control channel,
and network channel. The VM receives no Android path and cannot open PhoneCode's credentials,
sessions, provider configuration, or unrelated projects.

The main process owns provider and Git credentials. Any credential request from the guest is
host-validated, scoped to the configured origin, short-lived, and never persisted in the guest.
Guest networking is brokered by the main process. Private, loopback, link-local, multicast,
carrier-grade NAT, and IPv6 ULA destinations are denied by default. User-started preview servers are
exposed only through app-local loopback ports unless a later reviewed feature states otherwise.

## Persistent state

The runtime uses three storage layers:

- A global system disk for Alpine, global packages, and global Skills.
- A per-project disk for `/workspace`, Git data, dependencies, build output, and project Skills.
- Android-owned state for chats, provider credentials, permissions, Storage Access Framework grants,
  project metadata, and process history.

Only one VM may write the global system disk at a time. Base-image hashes are verified at startup. A
damaged base can be restored without deleting project disks.

## Phone folders

Android document-tree URIs do not provide POSIX or Git filesystem semantics and are not mounted
directly. A selected folder is imported into the project disk. `.git`, dependencies, virtual
environments, caches, and build output remain private to the project disk.

User-visible files return to the phone folder at explicit checkpoints and through Sync now. Sync
tracks a last-common content hash, uses recoverable temporary and backup writes, and surfaces
conflicts without overwriting either side. Git administrative files are never synchronized
file-by-file. Unlinking asks whether to retain or delete the project disk.

Until that bridge passes destructive-provider, concurrent-edit, low-storage, symlink, and large-tree
tests, linked phone folders remain accessible only through the explicit shared-file tools.

## Lifecycle

Active turns and user-started VM processes use a `specialUse` foreground service with a persistent
notification and Stop action. The service exists only while perceptible work is active. The Stop
action cancels the turn, terminates managed processes, exits QEMU, and releases the wake lock.

The workspace and disks are permanent; the VM is not. Android can still stop work after force stop,
reboot, resource pressure, or permission changes. Restored sessions report interrupted work instead
of claiming that a process survived.

## First vertical slice

1. Reproducibly build QEMU for arm64-v8a with pinned sources and Android NDK 28.2.
2. Package a pinned Alpine kernel and minimal BusyBox initramfs.
3. Boot one virtual CPU with 256 MiB in an isolated service using descriptor-only inputs.
4. Wait for `PHONECODE_VM_READY`, run one shell command, and return bounded output.
5. Keep the command alive for two minutes while backgrounded with the screen off.
6. Stop it from the notification and verify that QEMU and the guest process exit.
7. Verify that the isolated process cannot open a credential sentinel in PhoneCode's data directory.
8. Measure boot time, RSS, idle CPU, battery, thermal state, and app size on API 26, 34, and 35.

Package installation, persistent disks, networking, project synchronization, and loopback preview
ports are added only after this slice passes.

## Verified foundation

The Android-native QEMU proof passed on an API 34 arm64 emulator with QEMU 11.0.2, Android NDK
28.2.13676358, and a minimum host API of 26. The stripped 11,516,872-byte PIE has no RUNPATH and
depends directly only on Android libc/libm plus zlib, libfdt, and GLib. Its SHA-256 is
`dfd8427a009800beb119c2103d5207ab5f286c610c3b13d1fef9fef2bf885c71`; the QEMU source archive
SHA-256 is `3745f6ea88e2e87fe0dc838b2b1d4e0a770bf48e01a1d5a186842a1fff76ccf5`.

One virtual CPU and 256 MiB booted the signed-input prototype to a live Alpine shell in 1.003 seconds
of host wall time. The kernel reached `/init` at 0.821 seconds, the guest returned an exact command
marker, and `poweroff -f` removed QEMU cleanly. Idle host use was 191,436 KiB RSS, 189,283 KiB PSS,
three threads, and no swap. The proof kernel SHA-256 is
`47970e0ee0478fe5c60824a89f162d5a353fa29466e5d3bddb0f9c506f1ed756`; the proof initramfs
SHA-256 is `e530da460998be9029223f6c74e9025cac70f1254e2f41d4caa1f1dc2f7fc104`.

The app now contains a private isolated-process service, descriptor-only Binder contract, native
fork/exec and process-group stop shim, and an emulator-tested isolation probe. The probe confirms
that the service UID differs from the app UID, cannot open an app-private sentinel by path, and can
read the same file only when the app delegates a read-only descriptor.

The proof QEMU dependency libraries are not shipping artifacts. Their current Termux build metadata
is not reproducible enough for release. Isolated QEMU boot, guest control, API 26 and 35 coverage,
background lifecycle, persistent disks, networking, energy measurements, source rebuilds, and final
AAB inventory remain release blockers.

## Initial resource gates

- Less than 150 MB compressed download per delivered ABI.
- Less than 400 MB installed before user packages.
- 256-512 MiB guest RAM and one virtual CPU by default.
- No more than 768 MiB and two virtual CPUs initially.
- Less than 180 MiB host overhead beyond guest RAM.
- Less than 1% idle CPU.
- Cold boot under 15 seconds on a flagship and 30 seconds on a midrange device.
- A 2 GiB sparse system-disk quota and 4 GiB sparse per-project default with an 80% warning.

## Licensing gate

Before distribution, PhoneCode publishes the complete license texts, corresponding sources,
Android portability patches, reproducible build scripts, NDK version, source offer, SBOM, and hashes
for QEMU, the Linux kernel, BusyBox, apk-tools, and every linked library. QEMU remains a separate
executable and process rather than a JNI library.
