package me.kenzierocks.mcpide.project

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import me.kenzierocks.mcpide.SrgMapping
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream


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
    private val coroutineScope = CoroutineScope(Dispatchers.IO + CoroutineName("Project-IO"))
    private val srgMappingsFile: Path = directory.resolve("srg-mappings.csv.gz")
    private val exportsFile: Path = directory.resolve("srg-exports.csv.gz")
    private val mutex = Mutex()
    val minecraftJar: Path = directory.resolve("minecraft.jar")

    private inline fun gzReader(path: Path, block: (Reader) -> Unit) {
        InputStreamReader(
            GZIPInputStream(Files.newInputStream(path)),
            StandardCharsets.UTF_8
        ).use(block)
    }

    private fun readSrgMappings(path: Path): ReceiveChannel<SrgMapping> {
        return coroutineScope.produce<SrgMapping> {
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

    fun readAllSrgMappings(): ReceiveChannel<SrgMapping> {
        return readSrgMappings(srgMappingsFile)
    }

    fun readExportSrgMappings(): ReceiveChannel<SrgMapping> {
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

}