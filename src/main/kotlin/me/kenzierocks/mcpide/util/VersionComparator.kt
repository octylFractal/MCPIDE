/*
 * This file is part of MCPIDE, licensed under the MIT License (MIT).
 *
 * Copyright (c) kenzierocks <https://kenzierocks.me>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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