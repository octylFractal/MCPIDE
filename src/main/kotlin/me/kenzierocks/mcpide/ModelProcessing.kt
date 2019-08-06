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