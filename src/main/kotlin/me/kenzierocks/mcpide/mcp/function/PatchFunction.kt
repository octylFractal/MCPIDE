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
import kotlinx.coroutines.withContext
import me.kenzierocks.mcpide.mcp.McpContext
import me.kenzierocks.mcpide.mcp.McpFunction
import me.kenzierocks.mcpide.util.computeOutput
import me.kenzierocks.mcpide.util.diff.ContextualPatch
import me.kenzierocks.mcpide.util.diff.PatchFile
import me.kenzierocks.mcpide.util.diff.ZipContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class PatchFunction(
    private val path: String
) : McpFunction {

    private lateinit var patches: Map<String, String>

    override suspend fun initialize(context: McpContext, zip: ZipFile) {
        patches = zip.entries().asSequence().filter {
            !it.isDirectory && it.name.startsWith(path) && it.name.endsWith(".patch")
        }.associate { ze ->
            ze.name.substring(path.length) to withContext(Dispatchers.IO) {
                zip.getInputStream(ze).use { it.reader().readText() }
            }
        }
    }

    override suspend fun invoke(context: McpContext): Path {
        val input = context.arguments.getValue("input") as Path
        val output = context.file("output.jar")

        computeOutput(input, output) {
            Files.createDirectories(output.parent)
            ZipFile(input.toFile()).use { zip ->
                val patchContext = ZipContext(zip)

                var success = true
                patches.forEach { (name, file) ->
                    val patch = ContextualPatch.create(PatchFile.from(file), patchContext)
                    patch.setCanonicalization(access = true, whitespace = false)
                    patch.maxFuzz = 0

                    context.logger.info("Applying patch: $name")
                    patch.patch(false)
                        .filterNot { it.status.success }
                        .forEach { report ->
                            success = false
                            report.hunkReports.forEach { hunk ->
                                if (hunk.hasFailed()) {
                                    when (hunk.failure) {
                                        null -> context.logger.error("Hunk #" + hunk.hunkID + " Failed @" + hunk.index + ", Fuzzing: " + hunk.fuzz)
                                        else -> context.logger.error("Hunk #" + hunk.hunkID + " Failed: " + hunk.failure.message)
                                    }
                                }
                            }
                        }
                }

                if (success) {
                    patchContext.save(output.toFile())
                }
            }
        }

        return output
    }

}