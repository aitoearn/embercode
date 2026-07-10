package dev.phonecode.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

@Serializable
data class SharedFolder(
    val id: String,
    val name: String,
    val kind: String,
    val handle: String,
    val writable: Boolean,
)

class SharedFolderStore(private val file: File) {
    private val serializer = ListSerializer(SharedFolder.serializer())

    fun list(): List<SharedFolder> = synchronized(LOCK) {
        if (file.exists()) runCatching { storeJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
        else emptyList()
    }

    fun add(folder: SharedFolder): List<SharedFolder> = synchronized(LOCK) {
        val folders = list().filterNot { it.handle == folder.handle } + folder
        save(folders)
        folders
    }

    fun remove(id: String): List<SharedFolder> = synchronized(LOCK) {
        val folders = list().filterNot { it.id == id }
        save(folders)
        folders
    }

    private fun save(folders: List<SharedFolder>) {
        file.parentFile?.mkdirs()
        file.writeTextAtomically(storeJson.encodeToString(serializer, folders))
    }

    private companion object {
        val LOCK = Any()
    }
}
