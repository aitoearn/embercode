package dev.phonecode.tools.skills

import dev.phonecode.tools.Tool
import dev.phonecode.tools.ToolContext
import dev.phonecode.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class SkillTool(private val skills: List<SkillManifest>) : Tool {
    override val name = "skill"
    override val description: String = buildString {
        append("Load a skill's instructions or a referenced text file on demand. Call with the exact name below.")
        if (skills.isEmpty()) {
            append("\n(no skills are currently configured)")
        } else {
            append("\nAvailable skills:")
            skills.forEach { skill ->
                val location = skill.location.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
                val line = "\n- ${skill.name}: ${skill.description}$location"
                if (length + line.length > MAX_CATALOG_CHARS) {
                    val notice = "\n(more installed skills omitted from this catalog)"
                    if (length + notice.length <= MAX_CATALOG_CHARS) append(notice)
                    return@buildString
                }
                append(line)
            }
        }
    }
    override val promptSnippet = "load a configured skill's full instructions on demand (progressive disclosure)"
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") {
                put("type", "string")
                put("description", "The exact name of the skill to load")
            }
            putJsonObject("path") {
                put("type", "string")
                put("description", "An optional relative text file from the skill directory")
            }
        }
        put("required", buildJsonArray { add("name") })
        put("additionalProperties", false)
    }

    private val byName: Map<String, SkillManifest> = skills.associateBy { it.name }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val name = (args["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return ToolResult("skill: missing 'name'", isError = true)
        val skill = byName[name] ?: return ToolResult(
            "skill: unknown skill '$name'. Available: ${skills.joinToString(", ") { it.name }.ifEmpty { "(none)" }}",
            isError = true,
        )
        val path = (args["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (path != null) return loadResource(skill, path)
        val location = skill.location.takeIf { it.isNotBlank() }?.let { " location=\"${escape(it)}\"" }.orEmpty()
        return ToolResult("<skill_content name=\"${escape(skill.name)}\"$location>\n${skill.body}\n</skill_content>")
    }

    private fun loadResource(skill: SkillManifest, path: String): ToolResult {
        if (skill.location.isBlank() || path.isBlank()) return ToolResult("skill: resource path is unavailable", isError = true)
        val root = java.io.File(skill.location).parentFile?.canonicalFile
            ?: return ToolResult("skill: resource path is unavailable", isError = true)
        val file = runCatching { java.io.File(root, path).canonicalFile }.getOrNull()
            ?: return ToolResult("skill: invalid resource path", isError = true)
        if (!file.toPath().startsWith(root.toPath()) || !file.isFile || file.length() > MAX_RESOURCE_BYTES) {
            return ToolResult("skill: resource is unavailable", isError = true)
        }
        val content = runCatching {
            val bytes = file.readBytes()
            require(0.toByte() !in bytes)
            bytes.toString(Charsets.UTF_8)
        }.getOrElse {
            return ToolResult("skill: resource is not readable text", isError = true)
        }
        return ToolResult("<skill_resource name=\"${escape(skill.name)}\" path=\"${escape(path)}\">\n$content\n</skill_resource>")
    }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    private companion object {
        const val MAX_CATALOG_CHARS = 8_000
        const val MAX_RESOURCE_BYTES = 512L * 1024L
    }
}
