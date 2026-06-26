package dev.phonecode.app.agent

import android.content.Context
import android.os.Build
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bootstraps the agent's userland in two tiers.
 *
 * TIER 1 - busybox (always present, zero setup). busybox ships inside the APK as `libbusybox.so`
 * per ABI - the ONLY place Android lets an app exec a binary from (W^X: execve of anything written
 * under app data is denied by SELinux for targetSdk >= 29, which is also why no downloaded binary or
 * package manager can ever run). Each applet is a SYMLINK in $PREFIX/bin resolving to the
 * APK-installed busybox - the standard `busybox --install -s` layout.
 *
 * TIER 2 - a real Linux userland via proot (only when the proot binaries are bundled). proot is a
 * userspace chroot+bind via ptrace: it runs a whole Alpine rootfs reached through mmap (PROT_EXEC
 * mmap survives W^X; only execve is blocked), using a trusted loader that ALSO lives in the APK
 * (jniLibs) so it is exec-permitted. The rootfs is fetched once (~3 MB) on first shell use, in the
 * background, so `apk add python3 py3-pip nodejs ...` works. Until the proot binaries exist or the
 * rootfs finishes downloading, the shell transparently stays on busybox.
 */
object EnvironmentBootstrap {

    /** A bundled Alpine version. Runtime download only, so this can be bumped without touching the APK. */
    private const val ALPINE_BRANCH = "3.21"
    private const val ALPINE_VERSION = "3.21.7"

    class Userland internal constructor(
        private val busyboxShell: List<String>,
        /** busybox/host environment: HOME, TMPDIR, PREFIX, PATH, TERM. */
        val env: Map<String, String>,
        /** Installed busybox applet names; empty when busybox is unavailable (fallback toybox). */
        val applets: List<String>,
        private val linux: Linux?,
    ) {
        /** True when the proot binaries are bundled, so a Linux userland CAN be set up on this build/ABI. */
        val linuxAvailable: Boolean get() = linux != null

        /** True once the Alpine rootfs is downloaded and extracted (the shell runs inside Linux). */
        fun linuxReady(): Boolean = linux?.ready() == true

        /**
         * Current shell argv. Returns the proot-wrapped Linux shell once its rootfs is ready, else the
         * busybox shell. Calling it also kicks off the one-time rootfs setup in the background, so the
         * first command runs on busybox and later commands transparently upgrade to Linux.
         */
        fun shell(): List<String> {
            linux?.kickoffSetup()
            return linux?.takeIf { it.ready() }?.shellArgv() ?: busyboxShell
        }

        /** Environment matching [shell]: proot needs PROOT_* + the guest PATH/HOME; busybox keeps the host env. */
        fun shellEnv(): Map<String, String> = linux?.takeIf { it.ready() }?.env() ?: env
    }

    fun ensure(context: Context): Userland {
        val home = File(context.filesDir, "home").apply { mkdirs() }
        val tmp = File(context.cacheDir, "tmp").apply { mkdirs() }
        val prefix = File(context.filesDir, "usr").apply { mkdirs() }
        val bin = File(prefix, "bin").apply { mkdirs() }
        val busybox = File(context.applicationInfo.nativeLibraryDir, "libbusybox.so")

        val env = mapOf(
            "HOME" to home.absolutePath,
            "TMPDIR" to tmp.absolutePath,
            "PREFIX" to prefix.absolutePath,
            "PATH" to bin.absolutePath + ":/system/bin",
            "TERM" to "dumb",
        )

        val version = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrDefault(0)
        val applets = if (busybox.canExecute()) installApplets(busybox, bin, version) else emptyList()
        val shell = if ("sh" in applets) {
            listOf(File(bin, "sh").absolutePath, "-c")
        } else {
            listOf("/system/bin/sh", "-c")
        }
        return Userland(shell, env, applets, buildLinux(context, busybox))
    }

    /** Symlinks every applet into [bin] once per app version; later calls read the marker. */
    private fun installApplets(busybox: File, bin: File, version: Int): List<String> = runCatching {
        val marker = File(bin.parentFile, ".applets-v$version")
        if (marker.isFile) return@runCatching marker.readLines().filter { it.isNotBlank() }

        val process = ProcessBuilder(busybox.absolutePath, "--list").redirectErrorStream(true).start()
        val listed = process.inputStream.bufferedReader().readText().lines()
            .map { it.trim() }.filter { it.matches(Regex("[a-z0-9._\\[\\]-]+")) }
        process.waitFor()
        if (listed.isEmpty()) return@runCatching emptyList()

        listed.forEach { name ->
            val link = File(bin, name)
            // The per-version marker already gates this loop, so always re-point the link: after an app
            // update the old symlinks target the previous nativeLibraryDir and must be recreated against
            // the current one. Skipping existing links (the old guard) left the shell pointing at a stale path.
            runCatching {
                link.delete()
                android.system.Os.symlink(busybox.absolutePath, link.absolutePath)
            }
        }
        marker.writeText(listed.joinToString("\n"))
        listed
    }.getOrDefault(emptyList())

    /** A [Linux] capability iff the proot binaries are bundled for this ABI; null = busybox-only. */
    private fun buildLinux(context: Context, busybox: File): Linux? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val proot = File(nativeDir, "libproot.so")
        val loader = File(nativeDir, "libproot-loader.so")
        if (!proot.canExecute() || !loader.exists()) return null
        val arch = alpineArch(Build.SUPPORTED_ABIS.firstOrNull()) ?: return null
        return Linux(
            proot = proot,
            loader = loader,
            busybox = busybox,
            rootfs = File(context.filesDir, "linux/$arch"),
            tmpDir = File(context.cacheDir, "proot-tmp"),
            workspacesRoot = File(context.filesDir, "workspaces"),
            rootfsUrl = "https://dl-cdn.alpinelinux.org/alpine/v$ALPINE_BRANCH/releases/$arch/" +
                "alpine-minirootfs-$ALPINE_VERSION-$arch.tar.gz",
        )
    }

    private fun alpineArch(abi: String?): String? = when (abi) {
        "arm64-v8a" -> "aarch64"
        "x86_64" -> "x86_64"
        "armeabi-v7a" -> "armv7"
        else -> null
    }

    /**
     * The proot + Alpine tier. [ready] is a cheap marker check; [kickoffSetup] runs the one-time
     * download+extract once, in the background, so a shell call never blocks on the network.
     */
    class Linux internal constructor(
        private val proot: File,
        private val loader: File,
        private val busybox: File,
        private val rootfs: File,
        private val tmpDir: File,
        private val workspacesRoot: File,
        private val rootfsUrl: String,
    ) {
        private val marker = File(rootfs.parentFile, "${rootfs.name}.ready")
        private val started = AtomicBoolean(false)

        fun ready(): Boolean = marker.isFile

        /** Start the rootfs setup once, on a background thread (no-op if ready or already running). */
        fun kickoffSetup() {
            if (ready() || !started.compareAndSet(false, true)) return
            Thread({ runCatching { setup() } }, "alpine-setup").apply { isDaemon = true }.start()
        }

        /** Download the Alpine minirootfs and extract it with busybox tar (no tar in the JVM stdlib). */
        private fun setup() {
            if (ready()) return
            rootfs.deleteRecursively()
            rootfs.mkdirs()
            tmpDir.mkdirs()
            val tar = File(rootfs.parentFile, "rootfs.tar.gz")
            URL(rootfsUrl).openStream().use { input -> tar.outputStream().use { input.copyTo(it) } }
            val extract = ProcessBuilder(busybox.absolutePath, "tar", "-xzf", tar.absolutePath, "-C", rootfs.absolutePath)
                .redirectErrorStream(true).start()
            extract.inputStream.readBytes() // drain so the pipe never fills
            val ok = extract.waitFor() == 0 && File(rootfs, "bin").exists()
            tar.delete()
            if (!ok) {
                rootfs.deleteRecursively()
                started.set(false) // allow a later retry
                return
            }
            File(rootfs, "etc").mkdirs()
            File(rootfs, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            marker.writeText("ok")
        }

        /** proot argv ending in `/bin/sh -c`; ShellTool appends the command string. */
        fun shellArgv(): List<String> = listOf(
            proot.absolutePath,
            "-r", rootfs.absolutePath,
            "-0", // present as uid 0 so apk can write the rootfs
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${tmpDir.absolutePath}:/tmp",
            // Bind the workspaces root at its REAL path so ShellTool's cwd (a workspace under it) resolves
            // inside the guest and Linux tools edit the same files as the native file tools.
            "-b", "${workspacesRoot.absolutePath}:${workspacesRoot.absolutePath}",
            "/bin/sh", "-c",
        )

        fun env(): Map<String, String> = mapOf(
            "PROOT_LOADER" to loader.absolutePath, // the trusted, exec-permitted loader in jniLibs
            "PROOT_TMP_DIR" to tmpDir.absolutePath,
            "PROOT_NO_SECCOMP" to "1", // Android's seccomp filter breaks proot's seccomp fast-path
            "HOME" to "/root",
            "PATH" to "/usr/bin:/bin:/usr/sbin:/sbin",
            "TERM" to "dumb",
            "LANG" to "C.UTF-8",
        )
    }
}
