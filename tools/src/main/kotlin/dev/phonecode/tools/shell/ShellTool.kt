package dev.phonecode.tools.shell

import dev.phonecode.tools.Tool
import dev.phonecode.tools.ToolContext
import dev.phonecode.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.concurrent.TimeUnit

class ShellTool(
    private val shellProvider: (String) -> List<String> = { emptyList() },
    private val environment: () -> Map<String, String> = { emptyMap() },
    private val processManager: ProcessManager? = null,
) : Tool {
    override val name = "bash"
    override val description =
        "Run a shell command in the active workspace using the bundled Alpine environment. " +
            "Set background=true for any long-running command that must outlive the tool call."
    override val mutating = true

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("command") { put("type", "string"); put("description", "The shell command line to execute") }
            putJsonObject("timeout_s") { put("type", "integer"); put("description", "Wall-clock limit in seconds (default 60, max 1800)") }
            putJsonObject("background") { put("type", "boolean"); put("description", "Keep the command running and return a process session") }
        }
        put("required", kotlinx.serialization.json.buildJsonArray { add(JsonPrimitive("command")) })
    }

    override val promptSnippet = "bash - run commands or start managed background processes in the workspace"
    override val promptGuidelines = listOf(
        "bash runs inside a private Alpine environment with only the active workspace mounted at /workspace.",
        "Do not download or install executable packages until the VM runtime is active.",
        "Long operations: pass timeout_s (max 1800); the process is killed at the limit.",
        "Long-running commands: set background=true and do not append '&'; use the process tool for logs, stdin, and shutdown.",
        "Verify background work with its logs and a capability-specific check before reporting success.",
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult = withContext(Dispatchers.IO) {
        val command = (args["command"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult("bash: missing 'command'", isError = true)
        val background = (args["background"] as? JsonPrimitive)?.booleanOrNull == true
        val shell = runCatching { shellProvider(context.workspacePath) }.getOrElse {
            return@withContext ToolResult("bash: ${it.message ?: "bundled Alpine environment is not ready"}", true)
        }
        if (shell.isEmpty()) {
            return@withContext ToolResult("bash: bundled Alpine environment is not ready", true)
        }
        if (background) {
            return@withContext processManager?.start(command, context.workspacePath)
                ?: ToolResult("bash: background processes are unavailable", isError = true)
        }
        val timeoutS = ((args["timeout_s"] as? JsonPrimitive)?.intOrNull ?: 60).coerceIn(1, 1800)

        runCatching {
            val commandEnvironment = environment()
            val process = ProcessBuilder(shell + command)
                .directory(if ("PROOT_LOADER" in commandEnvironment) File("/") else File(context.workspacePath))
                .redirectErrorStream(true)
                .apply { environment().putAll(commandEnvironment) }
                .start()
            coroutineScope {
                var timedOut = false
                val output = StringBuilder()
                val reader = async(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { stream ->
                        val buf = CharArray(4096)
                        while (true) {
                            val n = stream.read(buf)
                            if (n < 0) break
                            if (output.length < MAX_OUTPUT) output.append(buf, 0, n)
                        }
                    }
                }
                val exit = try {
                    withTimeout(timeoutS * 1_000L) { runInterruptible { process.waitFor() } }
                } catch (_: TimeoutCancellationException) {
                    timedOut = true
                    terminate(process)
                    runCatching { process.exitValue() }.getOrDefault(-1)
                } finally {
                    if (process.isAlive) terminate(process)
                }
                reader.await()
                val truncated = output.length >= MAX_OUTPUT
                val body = buildString {
                    append(output.take(MAX_OUTPUT))
                    if (truncated) append("\n... (output truncated at ${MAX_OUTPUT / 1024} KB)")
                    if (timedOut) append("\n(killed after ${timeoutS}s timeout)")
                    if (exit != 0 && !timedOut) append("\n(exit code $exit)")
                }.ifBlank { if (exit == 0) "(no output)" else "(no output, exit code $exit)" }
                ToolResult(body, isError = exit != 0 || timedOut)
            }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            ToolResult("bash failed: ${e.message}", isError = true)
        }
    }

    private companion object {
        const val MAX_OUTPUT = 48_000

        private fun terminate(process: Process) {
            process.destroy()
            if (!runCatching { process.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)) {
                process.destroyForcibly()
                runCatching { process.waitFor(2, TimeUnit.SECONDS) }
            }
        }
    }
}
