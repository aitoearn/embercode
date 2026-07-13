package dev.phonecode.tools.shell

import dev.phonecode.tools.ToolContext
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ShellToolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context get() = object : ToolContext {
        override val workspacePath: String get() = tmp.root.absolutePath
        override suspend fun requestPermission(tool: String, summary: String) = true
    }

    /** Host shell: the tool's default targets Android's /system/bin/sh; tests use the JVM host's. */
    private fun hostShell(): List<String> =
        if (System.getProperty("os.name").lowercase().contains("win")) listOf("cmd.exe", "/c")
        else listOf("/bin/sh", "-c")

    private fun args(command: String, timeoutS: Int? = null) = buildJsonObject {
        put("command", command)
        timeoutS?.let { put("timeout_s", it) }
    }

    private fun backgroundArgs(command: String) = buildJsonObject {
        put("command", command)
        put("background", true)
    }

    @Test fun runsACommandInTheWorkspace() = runBlocking {
        tmp.newFile("hello.txt")
        val result = ShellTool({ hostShell() }).execute(args("dir") , context).let {
            // "dir" works on cmd; on sh use ls
            if (it.isError) ShellTool({ hostShell() }).execute(args("ls"), context) else it
        }
        assertFalse(result.output, result.isError)
        assertTrue(result.output, result.output.contains("hello.txt"))
    }

    @Test fun nonZeroExitIsAnError() = runBlocking {
        val result = ShellTool({ hostShell() }).execute(args("exit 3"), context)
        assertTrue(result.isError)
        assertTrue(result.output, result.output.contains("exit code 3"))
    }

    @Test fun missingCommandIsAnError() = runBlocking {
        val result = ShellTool({ hostShell() }).execute(buildJsonObject { }, context)
        assertTrue(result.isError)
    }

    @Test fun refusesToUseAHostShellWhenAlpineIsUnavailable() = runBlocking {
        val result = ShellTool().execute(args("echo unsafe"), context)

        assertTrue(result.isError)
        assertEquals("bash: bundled Alpine environment is not ready", result.output)
    }

    @Test fun cancellationStopsTheForegroundProcess() = runBlocking {
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val pidFile = File(tmp.root, "foreground.pid")
        val running = async {
            ShellTool({ hostShell() }).execute(args("echo \$\$ > foreground.pid; exec sleep 30"), context)
        }
        withTimeout(5_000) {
            while (!pidFile.isFile) delay(20)
        }
        val pid = pidFile.readText().trim().toLong()

        running.cancelAndJoin()

        withTimeout(5_000) {
            while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) delay(20)
        }
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
    }

    @Test fun injectedEnvironmentReachesTheProcess() = runBlocking {
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val echo = if (isWin) "echo %PC_TEST%" else "echo \$PC_TEST"
        val result = ShellTool({ hostShell() }, { mapOf("PC_TEST" to "phonecode-env") }).execute(args(echo), context)
        assertFalse(result.output, result.isError)
        assertTrue(result.output, result.output.contains("phonecode-env"))
    }

    @Test fun schemaRequiresCommand() {
        val schema = ShellTool().parameters.toString()
        assertTrue(schema, schema.contains("\"required\":[\"command\"]"))
        assertTrue(schema, schema.contains("\"background\""))
        assertEquals("bash", ShellTool().name)
        assertTrue(ShellTool().mutating)
    }

    @Test fun managesBackgroundCommands() = runBlocking {
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val manager = ProcessManager({ hostShell() })
        val tool = ShellTool({ hostShell() }, processManager = manager)
        val started = tool.execute(backgroundArgs("printf ready; exec sleep 30"), context)

        assertFalse(started.output, started.isError)
        val id = Regex("proc-\\d+").find(started.output)!!.value
        assertTrue(manager.output(id).output.contains("ready"))
        assertTrue(manager.list().output.contains(id))
        assertFalse(manager.stop(id).isError)
        assertTrue(manager.output(id).output.contains("exited"))
    }

    @Test fun reportsBackgroundStartupFailure() = runBlocking {
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val manager = ProcessManager({ hostShell() })
        val result = ShellTool({ hostShell() }, processManager = manager)
            .execute(backgroundArgs("exit 7"), context)

        assertTrue(result.output, result.isError)
        assertTrue(result.output, result.output.contains("exited (7)"))
    }

    @Test fun sendsInputToManagedCommands() = runBlocking {
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val manager = ProcessManager({ hostShell() })
        val started = manager.start("read value; printf 'received:%s\\n' \"\$value\"; exec sleep 30", context.workspacePath)
        val id = Regex("proc-\\d+").find(started.output)!!.value

        assertFalse(manager.input(id, "hello").isError)
        repeat(20) {
            if (manager.output(id).output.contains("received:hello")) return@repeat
            kotlinx.coroutines.delay(25)
        }
        assertTrue(manager.output(id).output, manager.output(id).output.contains("received:hello"))
        assertFalse(manager.stop(id).isError)
    }

    @Test fun reportsProcessesInterruptedByAppDeath() = runBlocking {
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val storage = tmp.newFolder("process-records")
        val manager = ProcessManager({ hostShell() }, storageDirectory = storage)
        val started = manager.start("printf ready; exec sleep 30", context.workspacePath)
        val id = Regex("proc-\\d+").find(started.output)!!.value

        val restored = ProcessManager({ hostShell() }, storageDirectory = storage)

        assertTrue(restored.output(id).output, restored.output(id).output.contains("interrupted"))
        assertTrue(restored.output(id).output, restored.output(id).output.contains("ready"))
        assertFalse(manager.stop(id).isError)
    }

    @Test fun stopAllStopsEveryRunningProcess() = runBlocking {
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val stopped = mutableListOf<String>()
        val manager = ProcessManager({ hostShell() }, onStopped = stopped::add)
        val first = manager.start("exec sleep 30", context.workspacePath)
        val second = manager.start("exec sleep 30", context.workspacePath)
        val ids = listOf(first, second).map { Regex("proc-\\d+").find(it.output)!!.value }

        manager.stopAll()

        assertTrue(ids.all { manager.output(it).output.contains("stopped") })
        assertEquals(ids.toSet(), stopped.toSet())
    }

    @Test fun persistenceFailureDoesNotStartAnUnmanagedProcess() = runBlocking {
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val starts = AtomicInteger()
        val stops = AtomicInteger()
        val manager = ProcessManager(
            shellProvider = { hostShell() },
            onStarted = { starts.incrementAndGet() },
            onStopped = { stops.incrementAndGet() },
            storageDirectory = tmp.newFile("not-a-directory"),
        )

        val started = manager.start("exec sleep 30", context.workspacePath)

        assertTrue(started.isError)
        assertTrue(started.output.contains("could not be saved"))
        assertEquals(0, starts.get())
        assertEquals(0, stops.get())
        assertEquals("No managed background processes.", manager.list().output)
    }

    @Test fun serviceStartFailureDoesNotOrphanTheProcess() = runBlocking {
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val pidFile = File(tmp.root, "failed-start.pid")
        val manager = ProcessManager(
            shellProvider = { hostShell() },
            onStarted = {
                repeat(250) {
                    if (pidFile.isFile) return@repeat
                    Thread.sleep(20)
                }
                error("service unavailable")
            },
        )

        val result = manager.start("echo \$\$ > failed-start.pid; exec sleep 30", context.workspacePath)
        val pid = pidFile.readText().trim().toLong()

        assertTrue(result.isError)
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
        assertEquals("No managed background processes.", manager.list().output)
    }
}
