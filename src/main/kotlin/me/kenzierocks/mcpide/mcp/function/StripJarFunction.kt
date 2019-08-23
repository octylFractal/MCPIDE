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

package me.kenzierocks.mcpide.mcp.function

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.io.cancel
import kotlinx.coroutines.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.io.readUTF8Line
import kotlinx.coroutines.withContext
import me.kenzierocks.mcpide.mcp.McpContext
import me.kenzierocks.mcpide.mcp.McpFunction
import me.kenzierocks.mcpide.util.computeOutput
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

class StripJarFunction(
    private val mappingsEntryName: String
) : McpFunction {

    private lateinit var filter: Set<String>

    override suspend fun initialize(context: McpContext, zip: ZipFile) {
        val lines = flow {
            val channel = zip.getInputStream(zip.getEntry(mappingsEntryName)).toByteReadChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                emit(line)
            }
            channel.cancel()
        }
        filter = lines.filterNot { it.startsWith("\t") }
            .map { s -> "${s.substringBefore(' ')}.class" }
            .toSet()
    }

    override suspend fun invoke(context: McpContext): Path {
        val input = context.arguments["input"] as Path
        val output = context.file("output.jar")
        val whitelist = "whitelist".equals(context.arguments.getOrDefault("mode", "whitelist") as String, ignoreCase = true)

        computeOutput(input, output) {
            strip(input, output, whitelist)
        }

        return output
    }

    private suspend fun strip(input: Path, output: Path, whitelist: Boolean) {
        Files.createDirectories(output.parent)
        withContext(Dispatchers.IO) {
            JarInputStream(Files.newInputStream(input)).use { jarIn ->
                JarOutputStream(Files.newOutputStream(output)).use { jarOut ->
                    while (true) {
                        val entry = jarIn.nextJarEntry ?: break
                        if (isEntryValid(entry, whitelist)) {
                            jarOut.putNextEntry(entry)
                            jarIn.copyTo(jarOut)
                            jarOut.closeEntry()
                        }
                    }
                }
            }
        }
    }

    private fun isEntryValid(entry: JarEntry, whitelist: Boolean): Boolean {
        return !entry.isDirectory && filter.contains(entry.name) == whitelist
    }

}
