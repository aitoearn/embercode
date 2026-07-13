package dev.phonecode.app.agent

import android.content.Context
import android.content.res.AssetManager
import android.net.ConnectivityManager
import android.os.Build
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

object EnvironmentBootstrap {

    private const val ALPINE_VERSION = "3.21.7"
    private const val ALPINE_ASSET = "alpine-aarch64.rootfs"

    class Userland internal constructor(
        private val linux: Linux?,
    ) {
        val linuxAvailable: Boolean get() = linux != null

        fun linuxReady(): Boolean = linux?.ready() == true

        fun ensureLinux(): Boolean = linux?.ensure() ?: false

        fun shell(workspacePath: String): List<String> {
            linux?.kickoffSetup()
            check(linux?.ready() == true) { "bundled Alpine environment is not ready" }
            return linux.shellArgv(workspacePath)
        }

        fun shellEnv(): Map<String, String> {
            check(linux?.ready() == true) { "bundled Alpine environment is not ready" }
            return linux.env()
        }
    }

    fun ensure(context: Context): Userland = Userland(buildLinux(context))

    private fun buildLinux(context: Context): Linux? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val proot = File(nativeDir, "libproot.so")
        val loader = File(nativeDir, "libproot-loader.so")
        if (!proot.canExecute() || !loader.exists()) return null
        if (Build.SUPPORTED_ABIS.firstOrNull() != "arm64-v8a") return null
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return Linux(
            proot = proot,
            loader = loader,
            rootfs = File(context.filesDir, "linux/aarch64"),
            tmpDir = File(context.cacheDir, "proot-tmp"),
            workspacesRoot = File(context.filesDir, "workspaces"),
            assets = context.assets,
            dnsServers = {
                connectivity.getLinkProperties(connectivity.activeNetwork)
                    ?.dnsServers
                    ?.mapNotNull { it.hostAddress }
                    .orEmpty()
            },
        )
    }

    /**
     * The proot + Alpine tier. [ready] is a cheap marker check; [ensure]/[kickoffSetup] extract the BUNDLED
     * rootfs once (local, no network), so the base Linux is reliable - the old runtime download could die
     * mid-flight (detached thread, flaky network) and leave the agent with no package manager.
     */
    class Linux internal constructor(
        private val proot: File,
        private val loader: File,
        private val rootfs: File,
        private val tmpDir: File,
        private val workspacesRoot: File,
        private val assets: AssetManager,
        private val dnsServers: () -> List<String>,
    ) {
        // Marker is version-keyed so a newer bundled rootfs (after an app update) re-extracts instead of
        // running against the stale tree.
        private val marker = File(rootfs.parentFile, "${rootfs.name}-$ALPINE_VERSION.ready")
        private val started = AtomicBoolean(false)

        fun ready(): Boolean = marker.isFile

        /** Extract the bundled rootfs once, on a background thread (no-op if ready or already running). */
        fun kickoffSetup() {
            if (ready() || !started.compareAndSet(false, true)) return
            Thread({ runCatching { ensure() } }, "alpine-setup").apply { isDaemon = true }.start()
        }

        /** Idempotent, blocking: extract the bundled Alpine rootfs if not already done. Returns ready state. */
        @Synchronized
        fun ensure(): Boolean {
            if (ready()) return true
            return runCatching { extract() }.getOrDefault(false)
        }

        /**
         * Extract the bundled gzipped tar in PURE KOTLIN. We do NOT shell out to busybox/toybox tar: that
         * process runs in the app's untrusted_app domain whose seccomp filter SIGSYS-kills the metadata
         * syscalls tar uses (timestamps/ownership), so it died with exit 159. This extractor uses only the
         * calls the app already makes (write, mkdir, Os.symlink, chmod), all seccomp-allowed.
         */
        private fun extract(): Boolean {
            rootfs.deleteRecursively()
            rootfs.mkdirs()
            tmpDir.mkdirs()
            GZIPInputStream(assets.open(ALPINE_ASSET).buffered()).use { untar(it, rootfs) }
            if (!File(rootfs, "bin").exists()) {
                rootfs.deleteRecursively()
                return false
            }
            File(rootfs, "etc").mkdirs()
            updateDns()
            marker.writeText("ok")
            return true
        }

        private fun updateDns() {
            val content = resolvConf(dnsServers())
            if (content.isNotEmpty()) File(rootfs, "etc/resolv.conf").writeText(content)
        }

        /**
         * Minimal ustar extractor: directories, regular files, symlinks. Skips hardlinks and device/fifo
         * nodes (the Alpine minirootfs has none, and proot binds the host /dev anyway). Only regular files
         * carry data blocks; every entry is padded to a 512-byte boundary.
         */
        private fun untar(input: InputStream, dest: File) {
            val header = ByteArray(512)
            while (readFully(input, header)) {
                if (header.all { it.toInt() == 0 }) break // end-of-archive marker
                val name = cString(header, 0, 100)
                if (name.isEmpty()) continue
                val mode = octal(header, 100, 8)
                val size = octal(header, 124, 12)
                val type = header[156].toInt().toChar()
                val target = File(dest, name)
                when (type) {
                    '5' -> target.mkdirs()
                    '2' -> { // symlink: target path is in the linkname field, not data
                        target.parentFile?.mkdirs()
                        target.delete()
                        runCatching { android.system.Os.symlink(cString(header, 157, 100), target.absolutePath) }
                    }
                    '0', '\u0000' -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { copyN(input, it, size) }
                        skipN(input, padding(size))
                        if (mode and 0b001_000_000 != 0L) target.setExecutable(true, false)
                    }
                    // '1' hardlink, '3'/'4' device, '6' fifo: no data blocks, skip.
                    else -> Unit
                }
            }
        }

        private fun padding(size: Long): Long = (512 - (size % 512)) % 512

        private fun readFully(input: InputStream, buf: ByteArray): Boolean {
            var off = 0
            while (off < buf.size) {
                val n = input.read(buf, off, buf.size - off)
                if (n < 0) return false
                off += n
            }
            return true
        }

        private fun copyN(input: InputStream, out: java.io.OutputStream, n: Long) {
            val buf = ByteArray(64 * 1024)
            var left = n
            while (left > 0) {
                val r = input.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
                if (r < 0) break
                out.write(buf, 0, r)
                left -= r
            }
        }

        private fun skipN(input: InputStream, n: Long) {
            var left = n
            val buf = ByteArray(8 * 1024)
            while (left > 0) {
                val r = input.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
                if (r < 0) break
                left -= r
            }
        }

        private fun cString(b: ByteArray, off: Int, len: Int): String {
            var end = off
            while (end < off + len && b[end].toInt() != 0) end++
            return String(b, off, end - off, Charsets.UTF_8)
        }

        private fun octal(b: ByteArray, off: Int, len: Int): Long =
            cString(b, off, len).trim().takeIf { it.isNotEmpty() }?.toLongOrNull(8) ?: 0L

        fun shellArgv(workspacePath: String): List<String> {
            updateDns()
            return linuxShellArgv(proot, rootfs, tmpDir, workspacesRoot, workspacePath)
        }

        fun env(): Map<String, String> = mapOf(
            "PROOT_LOADER" to loader.absolutePath,
            "PROOT_TMP_DIR" to tmpDir.absolutePath,
            "HOME" to "/root",
            "PATH" to "/usr/bin:/bin:/usr/sbin:/sbin",
            "TERM" to "dumb",
            "LANG" to "C.UTF-8",
        )
    }
}

internal fun resolvConf(servers: List<String>): String =
    servers.distinct().joinToString("") { "nameserver $it\n" }

internal fun linuxShellArgv(
    proot: File,
    rootfs: File,
    tmpDir: File,
    workspacesRoot: File,
    workspacePath: String,
): List<String> {
    val requested = File(workspacePath)
    require(requested.isAbsolute) { "workspace path must be absolute" }
    val root = workspacesRoot.canonicalFile
    val workspace = requested.canonicalFile
    require(workspace.isDirectory) { "workspace is not a directory" }
    require(workspace.path.startsWith(root.path + File.separator)) { "workspace is outside the workspace root" }
    require(':' !in workspace.path) { "workspace path cannot contain ':'" }
    val guestWorkspace = File(rootfs, "workspace")
    check(guestWorkspace.isDirectory || guestWorkspace.mkdirs()) { "guest workspace is unavailable" }
    check(tmpDir.isDirectory || tmpDir.mkdirs()) { "guest temporary directory is unavailable" }
    return listOf(
        proot.absolutePath,
        "-r", rootfs.canonicalPath,
        "-0",
        "-b", "/dev",
        "-b", "/proc",
        "-b", "${tmpDir.canonicalPath}:/tmp",
        "-b", "${workspace.path}:/workspace",
        "-w", "/workspace",
        "/bin/sh", "-c",
    )
}
