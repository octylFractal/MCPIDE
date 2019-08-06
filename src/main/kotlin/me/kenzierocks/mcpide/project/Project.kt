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

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import me.kenzierocks.mcpide.SrgMapping
import me.kenzierocks.mcpide.util.extractTo
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile


private val CSV_MAPPER = CsvMapper().also { it.findAndRegisterModules() }
private val MAPPING_SCHEMA = CSV_MAPPER.schemaFor(jacksonTypeRef<SrgMapping>())
    .withHeader().withStrictHeaders(true)
private val CSV_WRITER = CSV_MAPPER.writer(MAPPING_SCHEMA)
private val CSV_READER = CSV_MAPPER.readerFor(jacksonTypeRef<SrgMapping>())
    .with(MAPPING_SCHEMA)

/**
 * Class for manipulating a Project.
 */
class Project(val directory: Path) {
    private val srgMappingsFile: Path = directory.resolve("srg-mappings.csv.gz")
    private val exportsFile: Path = directory.resolve("srg-exports.csv.gz")
    private val mcpConfigDir: Path = directory.resolve("mcp_config")
    private val mutex = Mutex()
    val minecraftJar: Path = directory.resolve("minecraft.jar")

    private inline fun gzReader(path: Path, block: (Reader) -> Unit) {
        InputStreamReader(
            GZIPInputStream(Files.newInputStream(path)),
            StandardCharsets.UTF_8
        ).use(block)
    }

    private fun CoroutineScope.readSrgMappings(path: Path): ReceiveChannel<SrgMapping> {
        return produce<SrgMapping>(capacity = 100) {
            mutex.withLock {
                gzReader(path) { reader ->
                    CSV_READER.readValues<SrgMapping>(reader).forEach {
                        channel.send(it)
                        yield()
                    }
                }
            }
        }
    }

    fun CoroutineScope.readAllSrgMappings(): ReceiveChannel<SrgMapping> {
        return readSrgMappings(srgMappingsFile)
    }

    fun CoroutineScope.readExportSrgMappings(): ReceiveChannel<SrgMapping> {
        return readSrgMappings(exportsFile)
    }

    private fun gzWriter(path: Path, block: (Writer) -> Unit) {
        OutputStreamWriter(
            GZIPOutputStream(Files.newOutputStream(path)),
            StandardCharsets.UTF_8
        ).use(block)
    }

    suspend fun storeSrgMappings(srgMapping: List<SrgMapping>, forExport: Boolean = false) {
        mutex.withLock {
            gzWriter(srgMappingsFile) { it.writeMappings(srgMapping) }
            if (forExport) {
                gzWriter(exportsFile) { it.writeMappings(srgMapping) }
            }
        }
    }

    private fun Writer.writeMappings(srgMapping: List<SrgMapping>) {
        CSV_WRITER.writeValues(this).writeAll(srgMapping)
    }

    suspend fun saveMcpZip(zip: Path) {
        mutex.withLock {
            ZipFile(zip.toFile()).use { zf ->
                zf.extractTo(mcpConfigDir, zf.entries().toList().map { it.name })
            }
        }
    }

}