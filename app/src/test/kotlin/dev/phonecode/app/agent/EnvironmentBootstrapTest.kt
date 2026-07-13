package dev.phonecode.app.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EnvironmentBootstrapTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test fun bindsOnlyTheCanonicalWorkspaceAndRequiredGuestPaths() {
        val workspacesRoot = tmp.newFolder("workspaces")
        val workspace = File(workspacesRoot, "default").apply { mkdirs() }
        val rootfs = tmp.newFolder("rootfs")
        val guestTmp = tmp.newFolder("guest-tmp")
        val argv = linuxShellArgv(
            File(tmp.root, "libproot.so"),
            rootfs,
            guestTmp,
            workspacesRoot,
            File(workspace, ".").path,
        )

        val binds = argv.windowed(2).filter { it.first() == "-b" }.map { it.last() }
        assertEquals(
            listOf("/dev", "/proc", "${guestTmp.canonicalPath}:/tmp", "${workspace.canonicalPath}:/workspace"),
            binds,
        )
        assertEquals("/workspace", argv[argv.indexOf("-w") + 1])
    }

    @Test fun rejectsAWorkspaceOutsideTheWorkspaceRoot() {
        val workspacesRoot = tmp.newFolder("workspaces")
        val outside = tmp.newFolder("outside")

        assertThrows(IllegalArgumentException::class.java) {
            linuxShellArgv(
                File(tmp.root, "libproot.so"),
                tmp.newFolder("rootfs"),
                tmp.newFolder("guest-tmp"),
                workspacesRoot,
                outside.absolutePath,
            )
        }
    }

    @Test fun usesTheDeviceDnsServersWithoutPublicFallbacks() {
        assertEquals("nameserver 10.0.0.1\nnameserver 2001:db8::1\n", resolvConf(listOf("10.0.0.1", "2001:db8::1", "10.0.0.1")))
        assertEquals("", resolvConf(emptyList()))
    }
}
