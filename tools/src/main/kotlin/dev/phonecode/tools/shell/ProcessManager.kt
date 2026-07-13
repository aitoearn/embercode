package dev.phonecode.tools.shell

import dev.phonecode.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ProcessManager(
    private val shellProvider: (String) -> List<String>,
    private val environmentProvider: () -> Map<String, String> = { emptyMap() },
    private val onStarted: (String) -> Unit = {},
    private val onStopped: (String) -> Unit = {},
    storageDirectory: File? = null,
) {
    private enum class State {
        RUNNING,
        EXITED,
        STOPPED,
        INTERRUPTED,
    }

    @Serializable
    private data class StoredSession(
        val id: String,
        val command: String,
        val workspacePath: String,
        val startedAt: Long,
        val finishedAt: Long? = null,
        val state: String,
        val exitCode: Int? = null,
    )

    private class LogBuffer(initial: String = "") {
        private val value = StringBuilder(initial.takeLast(MAX_LOG))

        @Synchronized
        fun append(chars: CharArray, count: Int) {
            value.append(chars, 0, count)
            if (value.length > MAX_LOG) value.delete(0, value.length - MAX_LOG)
        }

        @Synchronized
        fun read(): String = value.toString()
    }

    private class Session(
        val id: String,
        val command: String,
        val workspacePath: String,
        val startedAt: Long,
        val process: Process?,
        val output: LogBuffer,
        state: State,
        exitCode: Int?,
        finishedAt: Long?,
        released: Boolean,
    ) {
        val state = AtomicReference(state)
        val exitCode = AtomicInteger(exitCode ?: EXIT_UNKNOWN)
        val finishedAt = AtomicLong(finishedAt ?: TIME_UNKNOWN)
        val released = AtomicBoolean(released)
        val stopRequested = AtomicBoolean(state == State.STOPPED)
    }

    private class Store(private val directory: File?) {
        private val json = Json { ignoreUnknownKeys = true }
        private val records = directory?.resolve("processes.json")

        init {
            directory?.mkdirs()
        }

        fun load(): List<StoredSession> = runCatching {
            val file = records ?: return emptyList()
            if (!file.isFile) emptyList() else json.decodeFromString<List<StoredSession>>(file.readText())
        }.getOrDefault(emptyList())

        @Synchronized
        fun save(sessions: Collection<Session>): Boolean {
            val file = records ?: return true
            return runCatching {
                atomicWrite(file, json.encodeToString(sessions.sortedBy { it.startedAt }.map { it.stored() }))
            }.isSuccess
        }

        fun readLog(id: String): String = runCatching { logFile(id)?.readText().orEmpty() }.getOrDefault("")

        @Synchronized
        fun saveLog(id: String, value: String): Boolean =
            runCatching { logFile(id)?.let { atomicWrite(it, value.takeLast(MAX_LOG)) } }.isSuccess

        fun deleteLog(id: String) {
            runCatching { logFile(id)?.delete() }
        }

        private fun logFile(id: String): File? = directory?.resolve("$id.log")

        private fun atomicWrite(file: File, value: String) {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, ".${file.name}.tmp")
            temporary.writeText(value)
            runCatching {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        private fun Session.stored() = StoredSession(
            id = id,
            command = command,
            workspacePath = workspacePath,
            startedAt = startedAt,
            finishedAt = finishedAt.get().takeUnless { it == TIME_UNKNOWN },
            state = state.get().name,
            exitCode = exitCode.get().takeUnless { it == EXIT_UNKNOWN },
        )
    }

    private val sequence = AtomicInteger()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val store = Store(storageDirectory)

    init {
        var interrupted = false
        store.load().forEach { saved ->
            val savedState = runCatching { State.valueOf(saved.state) }.getOrDefault(State.INTERRUPTED)
            val restoredState = if (savedState == State.RUNNING) State.INTERRUPTED else savedState
            if (restoredState != savedState) interrupted = true
            sessions[saved.id] = Session(
                id = saved.id,
                command = saved.command,
                workspacePath = saved.workspacePath,
                startedAt = saved.startedAt,
                process = null,
                output = LogBuffer(store.readLog(saved.id)),
                state = restoredState,
                exitCode = saved.exitCode,
                finishedAt = if (restoredState == State.INTERRUPTED) System.currentTimeMillis() else saved.finishedAt,
                released = true,
            )
            sequence.set(maxOf(sequence.get(), saved.id.substringAfter("proc-", "0").toIntOrNull() ?: 0))
        }
        if (interrupted) persist()
        prune()
    }

    suspend fun start(command: String, workspacePath: String): ToolResult = withContext(Dispatchers.IO) {
        val commandEnvironment = environmentProvider()
        val process = runCatching {
            ProcessBuilder(shellProvider(workspacePath) + command)
                .directory(if ("PROOT_LOADER" in commandEnvironment) File("/") else File(workspacePath))
                .redirectErrorStream(true)
                .apply { environment().putAll(commandEnvironment) }
                .start()
        }.getOrElse { return@withContext ToolResult("background process failed: ${it.message}", true) }
        val id = "proc-${sequence.incrementAndGet()}"
        val session = Session(
            id = id,
            command = command,
            workspacePath = workspacePath,
            startedAt = System.currentTimeMillis(),
            process = process,
            output = LogBuffer(),
            state = State.RUNNING,
            exitCode = null,
            finishedAt = null,
            released = false,
        )
        sessions[id] = session
        if (!store.saveLog(id, "") || !persist()) {
            terminate(process)
            sessions.remove(id, session)
            store.deleteLog(id)
            return@withContext ToolResult("background process failed: process state could not be saved", true)
        }
        var leased = false
        try {
            onStarted(id)
            leased = true
            watch(session)
        } catch (error: Throwable) {
            session.stopRequested.set(true)
            terminate(process)
            sessions.remove(id, session)
            store.deleteLog(id)
            persist()
            if (leased) runCatching { onStopped(id) }
            return@withContext ToolResult("background process failed: ${error.message}", true)
        }
        prune()
        delay(350)
        if (!process.isAlive) {
            finish(session, runCatching { process.exitValue() }.getOrDefault(-1))
            return@withContext ToolResult(render(session), true)
        }
        ToolResult(
            "Started $id in the background.\nCommand: ${command.take(240)}\n" +
                "Use process action=output session_id=$id for logs or action=stop to stop it.",
        )
    }

    fun list(): ToolResult {
        val items = sessions.values.sortedBy { it.startedAt }
        if (items.isEmpty()) return ToolResult("No managed background processes.")
        return ToolResult(items.joinToString("\n") { "${it.id} ${status(it)} ${it.command.take(160)}" })
    }

    fun output(id: String): ToolResult {
        val session = sessions[id] ?: return ToolResult("Unknown process session: $id", true)
        return ToolResult(render(session))
    }

    fun input(id: String, data: String, appendNewline: Boolean = true): ToolResult {
        val session = sessions[id] ?: return ToolResult("Unknown process session: $id", true)
        val process = session.process
        if (process == null || !process.isAlive) return ToolResult("Process session is not running: $id", true)
        return runCatching {
            process.outputStream.writer().apply {
                write(data)
                if (appendNewline) write("\n")
                flush()
            }
            ToolResult("Sent input to $id.")
        }.getOrElse { ToolResult("Unable to send input to $id: ${it.message}", true) }
    }

    suspend fun stop(id: String): ToolResult = withContext(Dispatchers.IO) {
        val session = sessions[id] ?: return@withContext ToolResult("Unknown process session: $id", true)
        val process = session.process
        session.stopRequested.set(true)
        if (process?.isAlive == true) {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
        }
        finish(session, process?.let { runCatching { it.exitValue() }.getOrDefault(-1) } ?: session.exitCode.get())
        ToolResult("Stopped $id.\n${session.output.read().ifBlank { "(no output)" }}")
    }

    fun stopAll() {
        sessions.values.filter { it.state.get() == State.RUNNING }.forEach { session ->
            session.stopRequested.set(true)
            session.process?.let { process ->
                runCatching { process.destroy() }
                if (process.isAlive) runCatching { process.destroyForcibly() }
            }
            finish(session, session.process?.let { runCatching { it.exitValue() }.getOrDefault(-1) } ?: -1)
        }
    }

    private fun watch(session: Session) {
        Thread({
            var lastSavedAt = 0L
            runCatching {
                session.process?.inputStream?.bufferedReader()?.use { reader ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        session.output.append(buffer, count)
                        val now = System.nanoTime()
                        if (now - lastSavedAt >= LOG_SAVE_INTERVAL_NANOS) {
                            store.saveLog(session.id, session.output.read())
                            lastSavedAt = now
                        }
                    }
                }
            }
            finish(session, session.process?.let { runCatching { it.waitFor() }.getOrDefault(-1) } ?: -1)
        }, "phonecode-${session.id}").apply { isDaemon = true }.start()
    }

    private fun finish(session: Session, exitCode: Int) {
        val finalState = if (session.stopRequested.get()) State.STOPPED else State.EXITED
        if (session.state.compareAndSet(State.RUNNING, finalState)) {
            session.exitCode.set(exitCode)
            session.finishedAt.set(System.currentTimeMillis())
            store.saveLog(session.id, session.output.read())
            persist()
        }
        if (session.released.compareAndSet(false, true)) runCatching { onStopped(session.id) }
    }

    private fun render(session: Session): String =
        "${session.id} ${status(session)}\nCommand: ${session.command}\n${session.output.read().ifBlank { "(no output yet)" }}"

    private fun status(session: Session): String = when {
        session.process?.isAlive == true && session.state.get() == State.RUNNING -> "running"
        session.state.get() == State.INTERRUPTED -> "interrupted (PhoneCode stopped before completion)"
        session.state.get() == State.STOPPED -> "exited (${session.exitCode.valueOrFallback()}, stopped)"
        else -> "exited (${session.exitCode.valueOrFallback()})"
    }

    private fun AtomicInteger.valueOrFallback(): Int = get().takeUnless { it == EXIT_UNKNOWN } ?: -1

    private fun persist(): Boolean = store.save(sessions.values)

    private fun terminate(process: Process) {
        runCatching { process.destroy() }
        if (!runCatching { process.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)) {
            runCatching { process.destroyForcibly() }
            runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        }
    }

    private fun prune() {
        if (sessions.size <= MAX_SESSIONS) return
        sessions.values
            .filter { it.state.get() != State.RUNNING }
            .sortedBy { it.startedAt }
            .take(sessions.size - MAX_SESSIONS)
            .forEach {
                sessions.remove(it.id, it)
                store.deleteLog(it.id)
            }
        persist()
    }

    private companion object {
        const val MAX_LOG = 48_000
        const val MAX_SESSIONS = 24
        const val EXIT_UNKNOWN = Int.MIN_VALUE
        const val TIME_UNKNOWN = Long.MIN_VALUE
        const val LOG_SAVE_INTERVAL_NANOS = 1_000_000_000L
    }
}
