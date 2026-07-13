package dev.phonecode.tools.skills

data class SkillManifest(
    val name: String,
    val description: String,
    val body: String,
    val location: String = "",
    val license: String = "",
    val compatibility: String = "",
)

private val FRONTMATTER = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$", RegexOption.DOT_MATCHES_ALL)

fun parseSkillMarkdown(content: String, location: String = ""): SkillManifest? {
    val normalized = content.replace("\r\n", "\n").removePrefix("﻿").trimStart()
    val match = FRONTMATTER.find(normalized) ?: return null
    val front = frontValues(match.groupValues[1])
    val body = match.groupValues[2].trim()
    val name = front["name"]?.takeIf { it.length <= 64 && SKILL_NAME.matches(it) } ?: return null
    val description = front["description"]?.takeIf { it.isNotBlank() && it.length <= 1024 } ?: return null
    return SkillManifest(
        name = name,
        description = description,
        body = body,
        location = location,
        license = front["license"].orEmpty(),
        compatibility = front["compatibility"].orEmpty(),
    )
}

private fun frontValues(front: String): Map<String, String> {
    val lines = front.lines()
    val values = linkedMapOf<String, String>()
    var index = 0
    while (index < lines.size) {
        val match = Regex("^([A-Za-z][A-Za-z0-9-]*):\\s*(.*)$").find(lines[index])
        if (match == null) {
            index++
            continue
        }
        val key = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        if (raw.startsWith(">") || raw.startsWith("|")) {
            val block = mutableListOf<String>()
            index++
            while (index < lines.size && (lines[index].isBlank() || lines[index].firstOrNull()?.isWhitespace() == true)) {
                block += lines[index].trim()
                index++
            }
            values[key] = if (raw.startsWith(">")) fold(block) else block.joinToString("\n").trim()
            continue
        }
        values[key] = raw.trim('"', '\'')
        index++
    }
    return values
}

private fun fold(lines: List<String>): String = buildString {
    lines.forEach { line ->
        when {
            line.isBlank() && isNotEmpty() && last() != '\n' -> append('\n')
            line.isNotBlank() && isNotEmpty() && last() != '\n' -> append(' ')
        }
        append(line)
    }
}.trim()

private val SKILL_NAME = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
