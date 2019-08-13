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
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.comms.AskDecompileSetup
import me.kenzierocks.mcpide.comms.AskInitialMappings
import me.kenzierocks.mcpide.comms.DecompileMinecraft
import me.kenzierocks.mcpide.comms.ExportMappings
import me.kenzierocks.mcpide.comms.LoadProject
import me.kenzierocks.mcpide.comms.ModelComms
import me.kenzierocks.mcpide.comms.OpenInFileTree
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
import me.kenzierocks.mcpide.resolver.MavenAccess
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

    fun start() {
        workerScope.launch {
            while (!modelComms.modelChannel.isClosedForReceive) {
                exhaustive(when (val msg = modelComms.modelChannel.receive()) {
                    is LoadProject -> loadProject(msg.projectDirectory)
                    is ExportMappings -> exportMappings()
                    is DecompileMinecraft -> decompileMinecraft(msg.mcpConfigZip)
                    is SetInitialMappings -> initMappings(msg.srgMappingsZip)
                })
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
        val result = flowOf("fields.csv", "methods.csv", "params.csv")
            .flatMapMerge { readSrgZipEntry(srgMappingsZip, it) }
            .toList()

        val p = requireProjectWorker()
        p.write {
            addMappings(result)
        }
        p.write { save() }
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
            if (isInitializedOnDisk()) {
                sendMessage(OpenInFileTree(p.read { directory }))
            } else {
                if (!hasInitialMappingsFile()) {
                    sendMessage(AskInitialMappings)
                }
                if (!hasMinecraftJar()) {
                    sendMessage(AskDecompileSetup)
                }
            }
        }
    }

    private fun exportMappings() {

    }
}