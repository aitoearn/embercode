package dev.phonecode.app.data

import android.content.res.AssetManager
import dev.phonecode.tools.mcp.McpConfig
import dev.phonecode.tools.mcp.McpServerConfig
import dev.phonecode.tools.mcp.decodeMcpConfig
import dev.phonecode.tools.mcp.serialize
import dev.phonecode.tools.skills.SkillManifest
import dev.phonecode.tools.skills.parseSkillMarkdown
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

sealed interface McpConfigLoad {
    data class Ready(val config: McpConfig) : McpConfigLoad
    data class Invalid(val raw: String, val message: String) : McpConfigLoad
}

enum class SkillScope { PROJECT, GLOBAL }

enum class SkillStatus { ACTIVE, DISABLED, SHADOWED, INVALID }

data class ManagedSkill(
    val id: String,
    val name: String,
    val manifest: SkillManifest?,
    val location: String,
    val scope: SkillScope,
    val status: SkillStatus,
    val issue: String? = null,
)

data class SkillInventory(val items: List<ManagedSkill>) {
    val active: List<SkillManifest> = items.mapNotNull { item ->
        item.manifest?.takeIf { item.status == SkillStatus.ACTIVE }
    }
}

data class RuntimeConfigFingerprint(val mcp: String, val skills: String)

class InvalidMcpConfigException(message: String) : IllegalStateException(message)

class McpSkillRepository(private val configDir: File) {
    private val mcpFile = File(configDir, "opencode.json")
    private val skillStateFile = File(configDir, "skills-state.json")

    fun loadMcpConfigState(): McpConfigLoad {
        if (!mcpFile.exists()) return McpConfigLoad.Ready(McpConfig())
        val raw = runCatching { mcpFile.readText() }.getOrElse {
            return McpConfigLoad.Invalid("", "MCP configuration could not be read")
        }
        return runCatching { McpConfigLoad.Ready(decodeMcpConfig(raw)) }
            .getOrElse { McpConfigLoad.Invalid(raw, "MCP configuration is invalid") }
    }

    fun loadMcpConfig(): McpConfig = when (val loaded = loadMcpConfigState()) {
        is McpConfigLoad.Ready -> loaded.config
        is McpConfigLoad.Invalid -> throw InvalidMcpConfigException(loaded.message)
    }

    @Synchronized
    fun saveMcpConfig(config: McpConfig): Result<Unit> = runCatching {
        requireValidMcpConfig()
        configDir.mkdirs()
        mcpFile.writeTextAtomically(config.serialize())
    }

    @Synchronized
    fun replaceMcpConfig(raw: String): Result<McpConfig> = runCatching {
        require(raw.toByteArray().size <= MAX_MCP_CONFIG_BYTES) { "MCP configuration is too large" }
        val config = decodeMcpConfig(raw)
        configDir.mkdirs()
        mcpFile.writeTextAtomically(config.serialize())
        config
    }

    fun upsertMcpServer(
        originalName: String? = null,
        name: String,
        server: McpServerConfig,
    ): Result<McpConfig> = mutateMcpConfig { current ->
        val finalName = name.trim()
        require(finalName.isNotEmpty()) { "Server name is required" }
        if (originalName != null) require(originalName in current.mcp) { "Server does not exist" }
        require(finalName == originalName || finalName !in current.mcp) { "Server name already exists" }
        val servers = current.mcp.toMutableMap()
        if (originalName != null && originalName != finalName) servers.remove(originalName)
        servers[finalName] = server
        McpConfig(servers)
    }

    fun removeMcpServer(name: String): Result<McpConfig> = mutateMcpConfig { current ->
        require(name in current.mcp) { "Server does not exist" }
        McpConfig(current.mcp - name)
    }

    fun setMcpEnabled(name: String, enabled: Boolean): Result<McpConfig> = mutateMcpConfig { current ->
        val server = current.mcp[name] ?: error("Server does not exist")
        McpConfig(current.mcp + (name to server.copy(enabled = enabled)))
    }

    fun runtimeFingerprint(projectDir: File? = null): RuntimeConfigFingerprint = RuntimeConfigFingerprint(
        mcp = fingerprint(listOf(mcpFile)),
        skills = fingerprint(buildList {
            add(skillStateFile)
            skillRootCandidates(projectDir).forEach { root ->
                add(root.file)
                root.file.listFiles().orEmpty().filter { it.isDirectory }.forEach { directory ->
                    add(directory)
                    add(File(directory, "SKILL.md"))
                }
            }
        }),
    )

    fun watchedDirectories(projectDir: File? = null): List<File> = buildSet {
        listOfNotNull(configDir, projectDir).mapNotNull(::canonicalDirectory).forEach(::add)
        skillRootCandidates(projectDir).forEach { root ->
            generateSequence(root.file.parentFile) { parent ->
                parent.parentFile?.takeUnless { parent == configDir || parent == projectDir }
            }.take(2).mapNotNull(::canonicalDirectory).forEach(::add)
            canonicalDirectory(root.file)?.let { directory ->
                add(directory)
                directory.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull(::canonicalDirectory).forEach(::add)
            }
        }
    }.toList()

    fun readSkillFile(
        scope: SkillScope,
        name: String,
        path: String = "SKILL.md",
        projectDir: File? = null,
    ): Result<String> = runCatching {
        val file = resolveEditableSkillFile(scope, name, path, projectDir, createRoot = false)
        require(file.isFile && file.length() <= MAX_SKILL_RESOURCE_BYTES) { "Skill file is unavailable" }
        val bytes = file.readBytes()
        require(0.toByte() !in bytes) { "Skill file is not readable text" }
        bytes.toString(Charsets.UTF_8)
    }

    @Synchronized
    fun writeSkillFile(
        scope: SkillScope,
        name: String,
        path: String = "SKILL.md",
        content: String,
        projectDir: File? = null,
    ): Result<Unit> = runCatching {
        require(content.toByteArray().size <= MAX_SKILL_RESOURCE_BYTES) { "Skill file is too large" }
        require('\u0000' !in content) { "Skill file must be text" }
        if (path == "SKILL.md") {
            val manifest = parseSkillMarkdown(content) ?: error("Invalid SKILL.md frontmatter")
            require(manifest.name == name) { "Skill name must match its folder" }
        }
        val file = resolveEditableSkillFile(scope, name, path, projectDir, createRoot = true)
        file.writeTextAtomically(content)
    }

    @Synchronized
    fun deleteEditableSkill(scope: SkillScope, name: String, projectDir: File? = null): Result<Unit> = runCatching {
        val directory = editableSkillRoot(scope, projectDir, create = false).resolve(name).canonicalFile
        require(directory.parentFile == editableSkillRoot(scope, projectDir, create = false)) { "Skill is outside its managed root" }
        require(directory.isDirectory) { "Skill does not exist" }
        check(directory.deleteRecursively()) { "Skill could not be deleted" }
        val id = File(directory, "SKILL.md").canonicalPath
        saveSkillState(SkillState(loadSkillState().disabled - id))
    }

    fun seedBundledSkills(assets: AssetManager) {
        val marker = File(configDir, ".bundled-skills")
        val seeded = runCatching { marker.readLines().toSet() }.getOrDefault(emptySet()).toMutableSet()
        val bundled = runCatching { assets.list("skills").orEmpty().sorted() }.getOrDefault(emptyList())
        bundled.filterNot(seeded::contains).forEach { name ->
            runCatching {
                val target = File(configDir, "skills/$name/SKILL.md")
                if (!target.exists()) {
                    target.parentFile?.mkdirs()
                    assets.open("skills/$name/SKILL.md").use { input -> target.writeBytesAtomically(input.readBytes()) }
                }
                seeded += name
            }
        }
        configDir.mkdirs()
        runCatching { marker.writeTextAtomically(seeded.sorted().joinToString("\n", postfix = "\n")) }
    }

    fun scanSkills(projectDir: File? = null): SkillInventory {
        val disabled = loadSkillState().disabled
        val candidates = skillRoots(projectDir).flatMap { root -> scanRoot(root, disabled) }
        val activeNames = mutableSetOf<String>()
        val items = candidates.map { candidate ->
            when {
                candidate.status == SkillStatus.INVALID -> candidate
                candidate.status == SkillStatus.DISABLED -> candidate
                activeNames.add(candidate.name) -> candidate.copy(status = SkillStatus.ACTIVE)
                else -> candidate.copy(status = SkillStatus.SHADOWED)
            }
        }
        return SkillInventory(items)
    }

    fun discoverSkills(projectDir: File? = null): List<SkillManifest> = scanSkills(projectDir).active

    @Synchronized
    fun setSkillEnabled(id: String, enabled: Boolean, projectDir: File? = null): Result<Unit> = runCatching {
        val item = scanSkills(projectDir).items.firstOrNull { it.id == id } ?: error("Skill does not exist")
        require(item.manifest != null) { "Invalid skills cannot be enabled" }
        val disabled = loadSkillState().disabled.toMutableSet()
        if (enabled) disabled.remove(id) else disabled.add(id)
        saveSkillState(SkillState(disabled))
    }

    @Synchronized
    fun deleteSkill(id: String, projectDir: File? = null): Result<Unit> = runCatching {
        val roots = skillRoots(projectDir)
        val item = scanSkills(projectDir).items.firstOrNull { it.id == id } ?: error("Skill does not exist")
        val directory = File(item.location).parentFile?.canonicalFile ?: error("Skill location is invalid")
        val root = roots.firstOrNull { directory.toPath().startsWith(it.file.toPath()) }
            ?: error("Skill is outside managed roots")
        require(directory.parentFile == root.file) { "Skill is outside managed roots" }
        check(directory.deleteRecursively()) { "Skill could not be deleted" }
        val disabled = loadSkillState().disabled - id
        saveSkillState(SkillState(disabled))
    }

    @Synchronized
    private fun mutateMcpConfig(transform: (McpConfig) -> McpConfig): Result<McpConfig> = runCatching {
        val updated = transform(requireValidMcpConfig())
        configDir.mkdirs()
        mcpFile.writeTextAtomically(updated.serialize())
        updated
    }

    private fun requireValidMcpConfig(): McpConfig = when (val loaded = loadMcpConfigState()) {
        is McpConfigLoad.Ready -> loaded.config
        is McpConfigLoad.Invalid -> throw InvalidMcpConfigException(loaded.message)
    }

    private fun skillRoots(projectDir: File?): List<SkillRoot> = skillRootCandidates(projectDir).mapNotNull { root ->
        canonicalDirectory(root.file)?.let { root.copy(file = it) }
    }

    private fun skillRootCandidates(projectDir: File?): List<SkillRoot> = buildList {
        projectDir?.let { project ->
            listOf(".agents/skills", ".opencode/skills", ".claude/skills", ".codex/skills")
                .forEach { add(SkillRoot(File(project, it), SkillScope.PROJECT)) }
        }
        listOf(".agents/skills", "skills", ".opencode/skills", ".claude/skills", ".codex/skills")
            .forEach { add(SkillRoot(File(configDir, it), SkillScope.GLOBAL)) }
    }

    private fun scanRoot(root: SkillRoot, disabled: Set<String>): List<ManagedSkill> =
        root.file.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.mapNotNull { directory ->
            val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return@mapNotNull null
            if (canonicalDirectory.parentFile != root.file) return@mapNotNull null
            val skillFile = File(canonicalDirectory, "SKILL.md")
            val id = runCatching { skillFile.canonicalPath }.getOrDefault(skillFile.absolutePath)
            val location = skillFile.absolutePath
            if (!skillFile.isFile) {
                return@mapNotNull ManagedSkill(id, canonicalDirectory.name, null, location, root.scope, SkillStatus.INVALID, "Missing SKILL.md")
            }
            val canonicalSkill = runCatching { skillFile.canonicalFile }.getOrNull()
            if (canonicalSkill == null || canonicalSkill.parentFile != canonicalDirectory) {
                return@mapNotNull ManagedSkill(id, canonicalDirectory.name, null, location, root.scope, SkillStatus.INVALID, "SKILL.md points outside its skill directory")
            }
            if (canonicalSkill.length() > MAX_SKILL_BYTES) {
                return@mapNotNull ManagedSkill(id, canonicalDirectory.name, null, location, root.scope, SkillStatus.INVALID, "SKILL.md is too large")
            }
            val manifest = runCatching { parseSkillMarkdown(canonicalSkill.readText(), canonicalSkill.absolutePath) }.getOrNull()
                ?: return@mapNotNull ManagedSkill(id, canonicalDirectory.name, null, location, root.scope, SkillStatus.INVALID, "Invalid SKILL.md frontmatter")
            if (manifest.name != canonicalDirectory.name) {
                return@mapNotNull ManagedSkill(id, manifest.name, manifest, location, root.scope, SkillStatus.INVALID, "Skill name must match its folder")
            }
            val status = if (id in disabled) SkillStatus.DISABLED else SkillStatus.ACTIVE
            ManagedSkill(id, manifest.name, manifest, location, root.scope, status)
        }

    private fun canonicalDirectory(file: File): File? = runCatching { file.canonicalFile }.getOrNull()?.takeIf { it.isDirectory }

    private fun loadSkillState(): SkillState = if (skillStateFile.isFile) {
        runCatching { storeJson.decodeFromString(SkillState.serializer(), skillStateFile.readText()) }.getOrDefault(SkillState())
    } else {
        SkillState()
    }

    private fun saveSkillState(state: SkillState) {
        skillStateFile.writeTextAtomically(storeJson.encodeToString(SkillState.serializer(), state))
    }

    private fun editableSkillRoot(scope: SkillScope, projectDir: File?, create: Boolean): File {
        val root = when (scope) {
            SkillScope.GLOBAL -> File(configDir, "skills")
            SkillScope.PROJECT -> File(requireNotNull(projectDir) { "A project is required" }, ".agents/skills")
        }
        if (create) root.mkdirs()
        return root.canonicalFile
    }

    private fun resolveEditableSkillFile(
        scope: SkillScope,
        name: String,
        path: String,
        projectDir: File?,
        createRoot: Boolean,
    ): File {
        require(SKILL_NAME.matches(name)) { "Invalid skill name" }
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && ':' !in path) { "Invalid skill path" }
        val root = editableSkillRoot(scope, projectDir, createRoot)
        val directory = File(root, name).canonicalFile
        require(directory.parentFile == root) { "Skill is outside its managed root" }
        if (createRoot) directory.mkdirs()
        val file = File(directory, path).canonicalFile
        require(file.toPath().startsWith(directory.toPath()) && file != directory) { "Skill file is outside its directory" }
        return file
    }

    private fun fingerprint(files: List<File>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        files.distinctBy { it.absolutePath }.sortedBy { it.absolutePath }.forEach { file ->
            digest.update(file.absolutePath.toByteArray())
            when {
                file.isDirectory -> digest.update(1.toByte())
                file.isFile && file.length() <= MAX_FINGERPRINT_BYTES -> file.inputStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                file.isFile -> digest.update("${file.length()}:${file.lastModified()}".toByteArray())
                else -> digest.update(0.toByte())
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    @Serializable
    private data class SkillState(val disabled: Set<String> = emptySet())

    private data class SkillRoot(val file: File, val scope: SkillScope)

    private companion object {
        const val MAX_SKILL_BYTES = 512L * 1024L
        const val MAX_SKILL_RESOURCE_BYTES = 512L * 1024L
        const val MAX_FINGERPRINT_BYTES = 1024L * 1024L
        const val MAX_MCP_CONFIG_BYTES = 1024L * 1024L
        val SKILL_NAME = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    }
}
