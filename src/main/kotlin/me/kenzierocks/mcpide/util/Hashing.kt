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

import com.google.common.hash.Funnels
import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import com.google.common.io.MoreFiles
import java.nio.file.Files
import java.nio.file.Path

val GOOD_HASH: HashFunction = Hashing.sha256()

fun Hasher.putFile(file: Path): Hasher {
    MoreFiles.asByteSource(file).copyTo(Funnels.asOutputStream(this))
    return this
}

/**
 * Compute output content, avoiding calls to [block] if input hashing matches.
 *
 * [block] should write to [output] when called.
 */
inline fun computeOutput(input: Path, output: Path, block: () -> Unit) {
    if (canReuseOutput(input, output)) {
        return
    }
    block()
    saveHashFile(input, output)
}

fun saveHashFile(input: Path, output: Path) {
    val hashFile = hashFileFor(input, output)
    Files.writeString(hashFile, hashFile(input).toString())
}

fun canReuseOutput(input: Path, output: Path): Boolean {
    if (!Files.exists(output)) {
        return false
    }
    val hashFile = hashFileFor(input, output)
    if (!Files.exists(hashFile)) {
        return false
    }
    val hash = HashCode.fromString(Files.readString(hashFile))
    return hash == hashFile(input)
}

private fun hashFile(input: Path): HashCode = GOOD_HASH.newHasher().putFile(input).hash()

/**
 * Hash file location. Uses output as the "name" of the operation, to allow an input to be re-used.
 */
private fun hashFileFor(input: Path, output: Path): Path =
    input.resolveSibling("${input.fileName}-${output.fileName}.sha256")

object HashBuilding {
    fun StringBuilder.addEntry(key: String, value: Any): StringBuilder {
        return addEntry(key) {
            append(when {
                value is Path && Files.isRegularFile(value) -> hashFile(value)
                else -> value
            })
        }
    }

    inline fun StringBuilder.addEntry(key: String, value: StringBuilder.() -> Unit): StringBuilder {
        return append(key).append('=').also(value).append('\n')
    }

    fun StringBuilder.addArgs(key: String, args: List<String>): StringBuilder {
        return addEntry(key) {
            args.joinTo(this, separator = " ")
        }
    }
}
