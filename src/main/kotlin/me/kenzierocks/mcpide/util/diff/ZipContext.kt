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

package me.kenzierocks.mcpide.util.diff

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.HashMap
import java.util.HashSet
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipContext(private val zip: ZipFile) : PatchContextProvider {
    private val modified = HashMap<String, MutableList<String>>()
    private val delete = HashSet<String>()
    private val binary = HashMap<String, ByteArray>()

    @Throws(IOException::class)
    override fun getData(patch: ContextualPatch.SinglePatch): MutableList<String>? {
        if (modified.containsKey(patch.targetPath))
            return modified[patch.targetPath]

        val entry = zip.getEntry(patch.targetPath)
        if (entry == null || patch.binary)
            return null

        zip.getInputStream(entry).use { input ->
            return input.reader().readLines().toMutableList()
        }
    }

    @Throws(IOException::class)
    override fun setData(patch: ContextualPatch.SinglePatch, data: List<String>) {
        if (patch.mode === ContextualPatch.Mode.DELETE || patch.binary && patch.hunks.isEmpty()) {
            delete.add(patch.targetPath)
            binary.remove(patch.targetPath)
            modified.remove(patch.targetPath)
        } else {
            delete.remove(patch.targetPath)
            if (patch.binary) {
                binary[patch.targetPath] = patch.hunks[0].lines
                    .map { Base64.getDecoder().decode(it) }
                    .reduce { acc, bytes -> acc + bytes }
                modified.remove(patch.targetPath)
            } else {
                val cp = data.toMutableList()
                if (!patch.noEndingNewline) {
                    cp.add("")
                }
                modified[patch.targetPath] = cp
                binary.remove(patch.targetPath)
            }
        }
    }

    @Throws(IOException::class)
    fun save(file: File) {
        val parent = file.parentFile
        if (!parent.exists())
            parent.mkdirs()

        ZipOutputStream(FileOutputStream(file)).use { out -> save(out) }
    }

    @Throws(IOException::class)
    fun save(out: ZipOutputStream): Set<String> {
        val files = HashSet<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            files.add(entries.nextElement().name)
        }
        files.addAll(modified.keys)
        files.addAll(binary.keys)
        files.removeAll(delete)
        val sorted = files.sorted()

        for (key in sorted) {
            putNextEntry(out, key)
            when {
                binary.containsKey(key) -> out.write(binary.getValue(key))
                modified.containsKey(key) -> out.write(modified.getValue(key).joinToString("\n").toByteArray(StandardCharsets.UTF_8))
                else -> zip.getInputStream(zip.getEntry(key)).use { ein -> ein.copyTo(out) }
            }
            out.closeEntry()
        }
        return files
    }

    @Throws(IOException::class)
    private fun putNextEntry(zip: ZipOutputStream, name: String) {
        val entry = ZipEntry(name)
        entry.time = 0
        zip.putNextEntry(entry)
    }
}
