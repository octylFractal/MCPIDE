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

package me.kenzierocks.mcpide.comms

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import me.kenzierocks.mcpide.SrgMapping
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

sealed class ModelMessage

data class LoadProject(val projectDirectory: Path) : ModelMessage()

object ExportMappings : ModelMessage()

object SaveProject : ModelMessage()

data class DecompileMinecraft(val mcpConfigZip: Path) : ModelMessage()

data class SetInitialMappings(val srgMappingsZip: Path) : ModelMessage()

data class Rename(val old: String, val new: String) : ModelMessage()

data class RemoveRenames(val srgNames: Set<String>) : ModelMessage()

sealed class RespondingModelMessage<R>(parent: Job? = null) : ModelMessage() {
    val result = CompletableDeferred<R>(parent = parent)
}

interface RMMConstructor<R, M : RespondingModelMessage<R>> {
    operator fun invoke(parent: Job?): M
}

suspend inline fun <M : RespondingModelMessage<R>, R> SendChannel<ModelMessage>.sendForResponse(
    constructor: RMMConstructor<R, M>
): R {
    val msg = constructor.invoke(coroutineContext[Job])
    send(msg)
    return msg.result.await()
}

data class MappingInfo(
    val mappings: Map<String, SrgMapping>,
    val exported: Map<String, SrgMapping>
)

class RetrieveMappings(parent: Job? = null) : RespondingModelMessage<MappingInfo>(parent) {
    companion object Constructor : RMMConstructor<MappingInfo, RetrieveMappings> {
        override operator fun invoke(parent: Job?) = RetrieveMappings(parent)
    }
}

class RetrieveDirtyStatus(parent: Job? = null) : RespondingModelMessage<Boolean>(parent) {
    companion object Constructor : RMMConstructor<Boolean, RetrieveDirtyStatus> {
        override operator fun invoke(parent: Job?) = RetrieveDirtyStatus(parent)
    }
}