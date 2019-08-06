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

package me.kenzierocks.mcpide.data

import kotlinx.io.errors.IOException
import java.nio.file.Files
import java.nio.file.Path

private val SPECIAL_DIRS = setOf("..", ".")

/**
 * Data cache for global MCPIDE data.
 */
class FileCache(
    val directory: Path
) {

    private fun Path.resolveAndCreate(dir: String): Path {
        val newDir = resolve(dir)
        try {
            Files.createDirectory(newDir)
        } catch (ignored: IOException) {
            require(Files.isDirectory(newDir)) {
                "$newDir already exists but is not a directory."
            }
        }
        return newDir
    }

    val mcpideCacheDirectory = directory.resolveAndCreate("files-1")
    val mavenCacheDirectory = directory.resolveAndCreate("maven-2")
    val okHttpCacheDirectory = directory.resolveAndCreate("okhttp-4")

    fun cacheEntry(name: String): Path {
        require(!name.contains('/')) { "Name cannot contain slashes." }
        require(name !in SPECIAL_DIRS) { "Name cannot be '..' or '.'."}
        return mcpideCacheDirectory.resolve(name)
    }

}