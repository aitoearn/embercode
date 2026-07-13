package dev.phonecode.tools.git

import dev.phonecode.tools.ToolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GitToolsTest {

    private fun ctxFor(dir: File) = object : ToolContext {
        override val workspacePath: String = dir.absolutePath
        override suspend fun requestPermission(tool: String, summary: String) = true
    }

    private val tools = gitTools { null }.associateBy { it.name }
    private val empty = buildJsonObject {}

    @Test fun initStageCommitStatusLog() = runBlocking {
        val dir = Files.createTempDirectory("gittools").toFile()
        try {
            val ctx = ctxFor(dir)

            // Before init, a repo op fails gracefully (not a crash).
            assertTrue(tools.getValue("git_status").execute(empty, ctx).isError)

            assertFalse(tools.getValue("git_init").execute(empty, ctx).isError)

            File(dir, "hello.txt").writeText("hello world")
            assertTrue(tools.getValue("git_status").execute(empty, ctx).output.contains("hello.txt"))

            assertFalse(tools.getValue("git_add").execute(empty, ctx).isError)

            val commit = tools.getValue("git_commit").execute(buildJsonObject { put("message", "initial commit") }, ctx)
            assertFalse(commit.isError)
            assertTrue(commit.output.contains("committed"))

            assertTrue(tools.getValue("git_log").execute(empty, ctx).output.contains("initial commit"))
            assertTrue(tools.getValue("git_status").execute(empty, ctx).output.contains("clean"))

            // Branch create + list.
            tools.getValue("git_branch").execute(buildJsonObject { put("name", "feature") }, ctx)
            assertTrue(tools.getValue("git_branch").execute(empty, ctx).output.contains("feature"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun pushWithoutCredentialsFailsClearly() = runBlocking {
        val dir = Files.createTempDirectory("gitpush").toFile()
        try {
            val ctx = ctxFor(dir)
            tools.getValue("git_init").execute(empty, ctx)
            val push = tools.getValue("git_push").execute(empty, ctx)
            assertTrue(push.isError)
            assertTrue(push.output.contains("credentials"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun repositoryHooksNeverExecute() = runBlocking {
        val dir = Files.createTempDirectory("githooks").toFile()
        try {
            val ctx = ctxFor(dir)
            tools.getValue("git_init").execute(empty, ctx)
            val marker = File(dir, "hook-ran")
            File(dir, ".git/hooks/post-commit").apply {
                parentFile.mkdirs()
                writeText("#!/bin/sh\ntouch '${marker.absolutePath}'\n")
                setExecutable(true)
            }
            File(dir, "file.txt").writeText("safe")
            tools.getValue("git_add").execute(empty, ctx)

            val commit = tools.getValue("git_commit")
                .execute(buildJsonObject { put("message", "no hooks") }, ctx)

            assertFalse(commit.isError)
            assertFalse(marker.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun pushRejectsNonHttpsOrigin() = runBlocking {
        val dir = Files.createTempDirectory("gitremote").toFile()
        try {
            val ctx = ctxFor(dir)
            tools.getValue("git_init").execute(empty, ctx)
            openGit(dir).use { git ->
                git.repository.config.apply {
                    setString("remote", "origin", "url", "file:///tmp/repository.git")
                    save()
                }
            }
            val authenticated = gitTools { "user" to "token" }.associateBy { it.name }

            val push = authenticated.getValue("git_push").execute(empty, ctx)

            assertTrue(push.isError)
            assertTrue(push.output.contains("HTTPS"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun pushRejectsNonGitHubHttpsOrigin() = runBlocking {
        val dir = Files.createTempDirectory("gitremote-host").toFile()
        try {
            val ctx = ctxFor(dir)
            tools.getValue("git_init").execute(empty, ctx)
            openGit(dir).use { git ->
                git.repository.config.apply {
                    setString("remote", "origin", "url", "https://example.com/repository.git")
                    save()
                }
            }
            val authenticated = gitTools { "user" to "token" }.associateBy { it.name }

            val push = authenticated.getValue("git_push").execute(empty, ctx)

            assertTrue(push.isError)
            assertTrue(push.output.contains("GitHub HTTPS"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun onlySuccessfulRemoteStatusesCountAsPushed() {
        assertTrue(successfulPushStatus(RemoteRefUpdate.Status.OK))
        assertTrue(successfulPushStatus(RemoteRefUpdate.Status.UP_TO_DATE))
        assertFalse(successfulPushStatus(RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD))
        assertFalse(successfulPushStatus(RemoteRefUpdate.Status.REJECTED_OTHER_REASON))
    }

    @Test fun repositoryMetadataCannotEscapeThroughASymbolicLink() = runBlocking {
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val dir = Files.createTempDirectory("git-symlink").toFile()
        val outside = Files.createTempDirectory("git-outside").toFile()
        try {
            Files.createSymbolicLink(File(dir, ".git").toPath(), outside.toPath())

            val result = tools.getValue("git_status").execute(empty, ctxFor(dir))

            assertTrue(result.isError)
            assertTrue(result.output.contains("symbolic link"))
        } finally {
            dir.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
