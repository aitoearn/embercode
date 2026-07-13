package dev.phonecode.tools.git

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.FS_POSIX
import org.eclipse.jgit.util.ProcessResult
import java.io.OutputStream

internal class NoExecFs : FS_POSIX() {
    override fun newInstance(): FS = NoExecFs()

    override fun runInShell(command: String, args: Array<out String>): ProcessBuilder =
        throw UnsupportedOperationException("Host process execution is disabled")

    override fun runHookIfPresent(
        repository: Repository,
        hookName: String,
        args: Array<out String>,
        outputStream: OutputStream,
        errorStream: OutputStream,
        stdinArgs: String?,
    ): ProcessResult = ProcessResult(ProcessResult.Status.NOT_SUPPORTED)
}
