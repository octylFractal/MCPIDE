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

package me.kenzierocks.mcpide.project

import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import me.kenzierocks.mcpide.Srg
import me.kenzierocks.mcpide.SrgMapping
import net.octyl.aptcreator.GenerateCreator
import net.octyl.aptcreator.Provided
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Class for manipulating a project. Uses no internal locking, and should either
 * have external synchronization applied or be used with [ProjectWorker].
 */
@GenerateCreator
class Project(
    val directory: Path,
    @[Provided Srg]
    private val srgReader: ObjectReader,
    @[Provided Srg]
    private val srgWriter: ObjectWriter
) : AutoCloseable {
    // Acquire project lock first.
    private val projectLock = projectLock(directory).also { it.acquire() }

    override fun close() {
        projectLock.close()
    }

    private val srgMappingsFile: Path = directory.resolve("srg-mappings.csv.gz")
    private val exportsFile: Path = directory.resolve("srg-exports.csv.gz")
    val minecraftJar: Path = directory.resolve("minecraft.jar")
    private val srgMappings = mutableMapOf<String, SrgMapping>()
    private val exportMappings = mutableSetOf<String>()
    var dirty = false

    // Non-IO functions, work with in-memory representation

    val mappings: Map<String, SrgMapping> = srgMappings

    fun addMappings(mappings: Iterable<SrgMapping>, itemsForExport: Iterable<String> = setOf()) {
        mappings.associateByTo(srgMappings) { it.srgName }
        exportMappings.addAll(itemsForExport)
        dirty = true
    }

    // IO, save/load functions, work with files

    fun isInitializedOnDisk(): Boolean {
        return hasInitialMappingsFile() && hasMinecraftJar()
    }

    fun hasInitialMappingsFile() = Files.exists(srgMappingsFile)

    fun hasMinecraftJar() = Files.exists(minecraftJar)

    fun copyMinecraftJar(from: Path) {
        Files.copy(from, minecraftJar, StandardCopyOption.REPLACE_EXISTING)
    }

    fun load() {
        loadMappings()
        dirty = false
    }

    fun save(force: Boolean = false) {
        if (!force && !dirty) {
            return
        }
        saveMappings()
        dirty = false
    }

    private fun loadMappings() {
        readSrgMappings(srgMappingsFile).associateByTo(srgMappings) { it.srgName }
        readSrgMappings(exportsFile)
            .map { it.srgName }
            .filterTo(exportMappings) { it in srgMappings }
    }

    private inline fun gzReader(path: Path, block: (Reader) -> Unit) {
        InputStreamReader(
            GZIPInputStream(Files.newInputStream(path)),
            StandardCharsets.UTF_8
        ).use(block)
    }

    private fun readSrgMappings(path: Path): Sequence<SrgMapping> {
        if (Files.notExists(path)) {
            return emptySequence()
        }
        return sequence {
            gzReader(path) { reader ->
                srgReader.readValues<SrgMapping>(reader).forEach {
                    yield(it)
                }
            }
        }.constrainOnce()
    }

    private fun saveMappings() {
        gzWriter(srgMappingsFile) { it.writeMappings(srgMappings.values.asSequence()) }
        gzWriter(exportsFile) {
            it.writeMappings(exportMappings.asSequence().map { k ->
                srgMappings.getValue(k)
            })
        }
    }

    private fun gzWriter(path: Path, block: (Writer) -> Unit) {
        OutputStreamWriter(
            GZIPOutputStream(Files.newOutputStream(path)),
            StandardCharsets.UTF_8
        ).use(block)
    }

    private fun Writer.writeMappings(srgMapping: Sequence<SrgMapping>) {
        srgWriter.writeValues(this).use { seqWriter -> srgMapping.forEach { seqWriter.write(it) } }
    }

}