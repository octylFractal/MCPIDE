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

package me.kenzierocks.mcpide

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.comms.AskDecompileSetup
import me.kenzierocks.mcpide.comms.AskInitialMappings
import me.kenzierocks.mcpide.comms.DecompileMinecraft
import me.kenzierocks.mcpide.comms.Exit
import me.kenzierocks.mcpide.comms.ExportMappings
import me.kenzierocks.mcpide.comms.InternalizeRenames
import me.kenzierocks.mcpide.comms.LoadProject
import me.kenzierocks.mcpide.comms.MappingInfo
import me.kenzierocks.mcpide.comms.ModelComms
import me.kenzierocks.mcpide.comms.OpenInFileTree
import me.kenzierocks.mcpide.comms.RefreshOpenFiles
import me.kenzierocks.mcpide.comms.RemoveRenames
import me.kenzierocks.mcpide.comms.Rename
import me.kenzierocks.mcpide.comms.RespondingModelMessage
import me.kenzierocks.mcpide.comms.RetrieveDirtyStatus
import me.kenzierocks.mcpide.comms.RetrieveMappings
import me.kenzierocks.mcpide.comms.SaveProject
import me.kenzierocks.mcpide.comms.SetInitialMappings
import me.kenzierocks.mcpide.comms.StatusUpdate
import me.kenzierocks.mcpide.comms.ViewMessage
import me.kenzierocks.mcpide.data.FileCache
import me.kenzierocks.mcpide.mcp.McpConfig
import me.kenzierocks.mcpide.mcp.McpRunner
import me.kenzierocks.mcpide.mcp.McpRunnerCreator
import me.kenzierocks.mcpide.project.ProjectCreator
import me.kenzierocks.mcpide.project.ProjectWorker
import me.kenzierocks.mcpide.project.projectWorker
import me.kenzierocks.mcpide.util.OwnerExecutor
import me.kenzierocks.mcpide.util.openErrorDialog
import me.kenzierocks.mcpide.util.requireEntry
import mu.KotlinLogging
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelProcessing @Inject constructor(
    private val jsonMapper: ObjectMapper,
    @Srg
    private val srgReader: ObjectReader,
    private val fileCache: FileCache,
    @Model
    private val workerScope: CoroutineScope,
    private val modelComms: ModelComms,
    private val projectCreator: ProjectCreator,
    private val mcpRunnerCreator: McpRunnerCreator
) {
    private val logger = KotlinLogging.logger { }
    private var projectWorker: ProjectWorker? = null

    private fun requireProjectWorker() = projectWorker ?: throw IllegalStateException("No project set.")

    private suspend fun sendMessage(viewMessage: ViewMessage) {
        modelComms.viewChannel.send(viewMessage)
    }

    private inline fun <M : RespondingModelMessage<R>, R> M.responseImpl(
        block: M.() -> R
    ) {
        try {
            result.complete(block())
        } catch (e: Throwable) {
            result.completeExceptionally(e)
        }
    }

    fun start() {
        workerScope.launch {
            try {
                fileCache.cacheEntry("most_recent.txt")
                    .takeIf { Files.exists(it) }?.let { Path.of(Files.readString(it)) }
                    ?.takeIf { Files.exists(it) }?.let { loadProject(it) }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load most recent project entry." }
            }
            while (!modelComms.modelChannel.isClosedForReceive) {
                try {
                    exhaustive(when (val msg = modelComms.modelChannel.receive()) {
                        is Exit -> workerScope.coroutineContext[OwnerExecutor]!!.executor.shutdownNow()
                        is LoadProject -> loadProject(msg.projectDirectory)
                        is ExportMappings -> exportMappings()
                        is DecompileMinecraft -> decompileMinecraft(msg.mcpConfigZip)
                        is SetInitialMappings -> initMappings(msg.srgMappingsZip)
                        is Rename -> rename(msg.old, msg.new)
                        is RemoveRenames -> removeRenames(msg.srgNames)
                        is InternalizeRenames -> internalizeRenames(msg.srgNames)
                        is RetrieveMappings -> msg.responseImpl {
                            requireProjectWorker().read {
                                MappingInfo(
                                    initialMappings.toMap(),
                                    exportedMappings.toMap()
                                )
                            }
                        }
                        is RetrieveDirtyStatus -> msg.responseImpl {
                            requireProjectWorker().read { dirty }
                        }
                        is SaveProject -> saveProject()
                    })
                } catch (e: Exception) {
                    logger.warn(e) { "Error in worker loop" }
                    e.openErrorDialog(
                        title = "Worker Error",
                        header = "Error in worker loop"
                    )
                }
            }
        }
    }

    private suspend fun decompileMinecraft(mcpConfigZip: Path) {
        val configJson = ZipFile(mcpConfigZip.toFile()).use { zip ->
            zip.getInputStream(zip.requireEntry("config.json")).use { eis ->
                jsonMapper.readValue<McpConfig>(eis)
            }
        }
        val runner = mcpRunnerCreator.create(
            mcpConfigZip,
            configJson,
            "joined",
            fileCache.mcpWorkDirectory.resolve(mcpConfigZip.fileName.toString().substringBefore(".zip"))
        )
        val decompiledJar = decompileJar(runner) ?: return
        requireProjectWorker().write(suspendFor = true) {
            copyMinecraftJar(decompiledJar)
        }
    }

    private suspend fun decompileJar(runner: McpRunner): Path? {
        return try {
            sendMessage(StatusUpdate("MC Decompile", "Starting decompile"))
            runner.run { step -> sendMessage(StatusUpdate("MC Decompile", step)) }
        } catch (e: Exception) {
            logger.warn(e) { "Error while importing MCP config." }
            e.openErrorDialog()
            null
        } finally {
            sendMessage(StatusUpdate("MC Decompile", ""))
        }
    }

    private suspend fun initMappings(srgMappingsZip: Path) {
        sendMessage(StatusUpdate("Names Import", "Reading files"))
        try {
            val result = flowOf("fields.csv", "methods.csv", "params.csv")
                .flatMapMerge { readSrgZipEntry(srgMappingsZip, it) }

            sendMessage(StatusUpdate("Names Import", "Saving to project"))
            val p = requireProjectWorker()
            p.write(suspendFor = true) {
                clearInitialMappings()
                result.collect {
                    addMapping(it)
                }
            }
            p.write { save() }
        } finally {
            sendMessage(StatusUpdate("Names Import", ""))
        }
        sendMessage(RefreshOpenFiles)
    }

    private fun readSrgZipEntry(srgMappingsZip: Path, entry: String): Flow<SrgMapping> {
        val zipFs = FileSystems.newFileSystem(srgMappingsZip, null)
        val entryPath = zipFs.getPath(entry)
        require(Files.exists(entryPath)) { "No $entry in $srgMappingsZip" }
        return flow {
            Files.newBufferedReader(entryPath).use { reader ->
                srgReader.readValues<SrgMapping>(reader).forEach {
                    emit(it)
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun loadProject(path: Path) {
        projectWorker?.run { channel.close() }
        val p = projectCreator.create(path).let { proj ->
            workerScope.projectWorker(proj).also { projectWorker = it }
        }
        sendMessage(StatusUpdate("", "Opening project at $path"))
        p.write {
            load()
            sendMessage(StatusUpdate("", ""))
            if (!hasInitialMappingsFile()) {
                sendMessage(AskInitialMappings)
            }
            if (!hasMinecraftJar()) {
                sendMessage(AskDecompileSetup)
            }
            val fs = FileSystems.newFileSystem(minecraftJar, null)
            Files.writeString(fileCache.cacheEntry("most_recent.txt"), path.toAbsolutePath().toString())
            sendMessage(OpenInFileTree(fs.getPath("/")))
        }
    }

    private suspend fun rename(old: String, new: String) {
        requireProjectWorker().write(suspendFor = true) { addNewMapping(old, new) }
        sendMessage(RefreshOpenFiles)
    }

    private suspend fun removeRenames(srgNames: Set<String>) {
        requireProjectWorker().write(suspendFor = true) { removeMappings(srgNames) }
        sendMessage(RefreshOpenFiles)
    }

    private suspend fun internalizeRenames(srgNames: Set<String>) {
        requireProjectWorker().write {
            internalizeMappings(srgNames)
        }
    }

    private data class ExportInfo(
        val dir: Path,
        val mapping: Map<String, SrgMapping>,
        val exported: Map<String, SrgMapping>
    )

    private suspend fun exportMappings() {
        val export = requireProjectWorker().read {
            ExportInfo(directory, initialMappings.toMap(), exportedMappings.toMap())
        }
        Files.newBufferedWriter(export.dir.resolve("exported-commands.txt")).use { writer ->
            export.exported.values.forEach { mapping ->
                writer.appendln(mapping.toCommand())
            }
        }
    }

    private suspend fun saveProject() {
        requireProjectWorker().write {
            save()
        }
    }

}