package me.kenzierocks.mcpide.util

import de.skuzzle.semantic.Version
import java.util.function.Function

data class ParsedVersion(
    val version : Version?,
    val original:  String
)

fun parseVersionSafe(version: String) : ParsedVersion {
    return ParsedVersion(
        try {
            Version.parseVersion(version)
        } catch (e: Version.VersionFormatException) {
            null
        },
        version
    )
}

private val COMPARATOR = Comparator.comparing<ParsedVersion, Version?>(
    Function { it.version }, Comparator.nullsLast(Comparator.naturalOrder())
)

fun List<String>.sortedByVersion() : List<String> {
    return map { parseVersionSafe(it) }.sortedWith(COMPARATOR).map { it.original }
}