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

private val GRADLE_COORDS = Regex("([^: ]+)(:)", RegexOption.COMMENTS)

/**
 * Parse Gradle's `group:name:version:classifier@extension` into Maven's
 * `group:name:extension:classifier:version`
 *
 * Extension defaults to `jar` if it is unspecified and classifier is specified.
 */
fun gradleCoordsToMaven(gradle: String): String {
    val extParts = gradle.split('@', limit = 2)
    val extension = extParts.takeIf { it.size == 2 }?.first()
    val rest = extParts.last()
    val parts = rest.split(':', limit = 4)
    val iter = parts.iterator()
    val group = iter.takeIf { it.hasNext() }?.next()
    val name = iter.takeIf { it.hasNext() }?.next()
    val version = iter.takeIf { it.hasNext() }?.next()
    val classifier = iter.takeIf { it.hasNext() }?.next()
    return listOfNotNull(group, name, extension ?: classifier?.let { "jar" }, classifier, version)
        .joinToString(":")
}