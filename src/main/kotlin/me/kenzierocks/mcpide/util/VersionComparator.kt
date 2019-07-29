package me.kenzierocks.mcpide.util

import de.skuzzle.semantic.Version
import mu.KotlinLogging

data class ParsedVersion(
    val version: Version?,
    val subVersion: Version?,
    val original: String
)

private val logger = KotlinLogging.logger { }

private fun fixVersion(version: String): String {
    val parts = version.split('.').map { p -> p.trimStart { it == '0' } }
    return when (parts.size) {
        1 -> parts + listOf("0", "0")
        2 -> parts + listOf("0")
        3 -> parts
        else -> {
            // join the last dots as part of the patch version
            parts.subList(0, 2) + parts.subList(2, parts.size).joinToString("")
        }
    }.joinToString(".")
}

private fun tryParseVersion(version: String): Version? {
    return try {
        Version.parseVersion(version)
    } catch (e: Version.VersionFormatException) {
        logger.info(e) { "Failed to parse $version" }
        null
    }
}

fun parseVersionSafe(version: String): ParsedVersion {
    val parts = version.split('-', limit = 2)
    val baseVersion = fixVersion(parts[0])
    val subVersion = parts.elementAtOrNull(1)?.let { fixVersion(it) }
    return ParsedVersion(
        tryParseVersion(baseVersion),
        subVersion?.let { tryParseVersion(it) },
        version
    )
}

private val COMPARATOR = compareBy<ParsedVersion, Version?>(nullsFirst()) {
    it.version
}.thenBy(nullsFirst()) { it.subVersion }

fun List<String>.sortedByVersion(): List<String> {
    return map { parseVersionSafe(it) }.sortedWith(COMPARATOR).map { it.original }
}