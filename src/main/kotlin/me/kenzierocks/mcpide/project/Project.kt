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
import me.kenzierocks.mcpide.Side
import me.kenzierocks.mcpide.SrgMapping
import me.kenzierocks.mcpide.data.FileCache
import me.kenzierocks.mcpide.inject.ProjectQ
import me.kenzierocks.mcpide.inject.ProjectScope
import me.kenzierocks.mcpide.inject.Srg
import me.kenzierocks.mcpide.mcp.McpRunner
import me.kenzierocks.mcpide.mcp.McpRunnerCreator
import mu.KotlinLogging
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

/**
 * Class for manipulating a project. Uses no internal locking, and should either
 * have external synchronization applied or be used with [ProjectWorker].
 */
@ProjectScope
class Project @Inject constructor(
    @ProjectQ
    val directory: Path,
    @Srg
    private val srgReader: ObjectReader,
    @Srg
    private val srgWriter: ObjectWriter,
    private val mcpRunnerCreator: McpRunnerCreator,
    private val fileCache: FileCache
) : AutoCloseable {
    private val logger = KotlinLogging.logger { }
    // Acquire project lock first.
    private val projectLock = projectLock(directory).also { it.acquire() }

    override fun close() {
        projectLock.close()
    }

    private val srgMappingsFile: Path = directory.resolve("srg-mappings.csv.gz")
    private val exportsFile: Path = directory.resolve("srg-exports.csv.gz")
    private val mcpConfigZip: Path = directory.resolve("mcp_config.zip")
    val minecraftJar: Path = directory.resolve("minecraft.jar")
    val initialMappings = LinkedHashMap<String, SrgMapping>()
    val exportedMappings = LinkedHashMap<String, SrgMapping>()
    var dirty = false

    // Non-IO functions, work with in-memory representation

    fun addMapping(newMapping: SrgMapping, forExport: Boolean = false) {
        (when {
            forExport -> exportedMappings
            else -> initialMappings
        })[newMapping.srgName] = newMapping
        dirty = true
    }

    fun addNewMapping(srgName: String, newName: String) {
        val newMapping = initialMappings[srgName]?.copy(
            srgName = srgName,
            newName = newName
        ) ?: SrgMapping(srgName, newName, Side.JOINED)
        addMapping(newMapping, forExport = true)
    }

    fun clearInitialMappings() {
        initialMappings.clear()
        dirty = true
    }

    fun removeMappings(srgNames: Set<String>) {
        dirty = dirty || exportedMappings.keys.removeAll(srgNames)
    }

    fun internalizeMappings(srgNames: Set<String>) {
        val moves = srgNames.mapNotNull { exportedMappings.remove(it) }.map { it.srgName to it }
        dirty = dirty || moves.isNotEmpty()
        initialMappings.putAll(moves)
    }

    // IO, save/load functions, work with files

    fun isInitializedOnDisk(): Boolean {
        return hasInitialMappingsFile() && hasMinecraftJar()
    }

    fun hasInitialMappingsFile() = Files.exists(srgMappingsFile)

    fun hasMinecraftJar() = Files.exists(minecraftJar)

    fun hasMcpConfig() = Files.exists(mcpConfigZip)

    fun copyMinecraftJar(from: Path) {
        safeWrite(minecraftJar) { output ->
            Files.newInputStream(from).use { it.copyTo(output) }
        }
    }

    val minecraftJarFileSystem: FileSystem
        get() = FileSystems.newFileSystem(minecraftJar, null)

    fun copyMcpConfigZip(from: Path) {
        safeWrite(mcpConfigZip) { output ->
            Files.newInputStream(from).use { it.copyTo(output) }
        }
    }

    fun newMcpRunner() : McpRunner {
        return mcpRunnerCreator.create(
            mcpConfigZip,
            "joined",
            fileCache.mcpWorkDirectory.resolve(mcpConfigZip.fileName.toString().substringBefore(".zip"))
        )
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
        readSrgMappings(srgMappingsFile).associateByTo(initialMappings) { it.srgName }
        logger.info { "Loaded mappings from $srgMappingsFile (count=${initialMappings.size})" }
        readSrgMappings(exportsFile).associateByTo(exportedMappings) { it.srgName }
        logger.info { "Loaded exports from $exportsFile (count=${exportedMappings.size})" }
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
        gzWriter(srgMappingsFile) { it.writeMappings(initialMappings.values.asSequence()) }
        logger.info { "Saved mappings to $srgMappingsFile (count=${initialMappings.size})" }
        gzWriter(exportsFile) { it.writeMappings(exportedMappings.values.asSequence()) }
        logger.info { "Saved mappings to $exportsFile (count=${exportedMappings.size})" }
    }

    private inline fun gzWriter(path: Path, block: (Writer) -> Unit) {
        safeWrite(path) { output ->
            OutputStreamWriter(
                GZIPOutputStream(output),
                StandardCharsets.UTF_8
            ).use(block)
        }
    }

    private fun Writer.writeMappings(srgMapping: Sequence<SrgMapping>) {
        // write header
        append("srgName,newName,side,desc").append('\n')
        srgWriter.writeValues(this).use { seqWriter -> srgMapping.forEach { seqWriter.write(it) } }
    }

}

/**
 * Write to [file] in a way that ensures a crash won't erase existing data.
 *
 * This ensures integrity of save-files.
 */
private inline fun safeWrite(file: Path, block: (OutputStream) -> Unit) {
    val tmp = file.resolveSibling(".new-${file.fileName}")
    Files.newOutputStream(tmp).use(block)
    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
