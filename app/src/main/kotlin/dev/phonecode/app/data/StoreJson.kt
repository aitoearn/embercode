package dev.phonecode.app.data

import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One Json config for every on-device store, so the disk format stays consistent and nothing drifts.
 * ignoreUnknownKeys = forward-compatible reads (a newer field never breaks an older build);
 * encodeDefaults = default-valued fields are written (stable, self-describing files);
 * isLenient = tolerant parsing of slightly-off hand-edited files (e.g. providers.json the agent edits).
 */
internal val storeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

internal fun File.writeTextAtomically(text: String) = writeBytesAtomically(text.toByteArray(Charsets.UTF_8))

internal fun File.writeBytesAtomically(bytes: ByteArray) {
    val target = absoluteFile
    val parent = target.parentFile ?: error("File has no parent: $target")
    parent.mkdirs()
    val temporary = Files.createTempFile(parent.toPath(), ".${target.name}.", ".tmp").toFile()
    try {
        FileOutputStream(temporary).use {
            it.write(bytes)
            it.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temporary.delete()
    }
}
