package me.kenzierocks.mcpide

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.comms.BeginProjectInitialize
import me.kenzierocks.mcpide.comms.ExportMappings
import me.kenzierocks.mcpide.comms.LoadProject
import me.kenzierocks.mcpide.comms.ModelComms
import me.kenzierocks.mcpide.comms.OpenInFileTree
import me.kenzierocks.mcpide.comms.ViewMessage
import java.nio.file.Files
import java.nio.file.Path

private const val PROJECT_META = ".mcpide"

class ModelProcessing(
    private val workerScope: CoroutineScope,
    private val modelComms: ModelComms
) {
    private suspend fun sendMessage(viewMessage: ViewMessage) {
        modelComms.viewChannel.send(viewMessage)
    }

    fun start() {
        workerScope.launch {
            while (!modelComms.modelChannel.isClosedForReceive) {
                exhaustive(when (val msg = modelComms.modelChannel.receive()) {
                    is LoadProject -> loadProject(msg.projectDirectory)
                    is ExportMappings -> exportMappings()
                })
            }
        }
    }

    private suspend fun loadProject(path: Path) {
        if (Files.exists(path.resolve(PROJECT_META))) {
            sendMessage(OpenInFileTree(path))
        }
        sendMessage(BeginProjectInitialize(path))
    }

    private fun exportMappings() {

    }
}