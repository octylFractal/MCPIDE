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

package me.kenzierocks.mcpide.mcp

import java.nio.file.Path
import java.util.zip.ZipFile

class McpStep(
    private val context: McpContext,
    val name: String,
    private val function: McpFunction,
    var arguments: Map<String, Any>,
    val workingDir: Path
) {

    var output: Path? = null
        private set

    val safeOutput
        get() = output ?: throw IllegalStateException("Step $name has not been executed yet")

    fun isOfType(type: Class<out McpFunction>) = type.isInstance(function)

    suspend fun initialize(zip: ZipFile) = function.initialize(context, zip)

    suspend fun execute(): Path = try {
        function(context).also { output = it }
    } finally {
        function.cleanup(context)
    }

}

inline fun <reified T : McpFunction> McpStep.isOfType() = isOfType(T::class.java)
