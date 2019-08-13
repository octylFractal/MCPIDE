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
import kotlinx.coroutines.invoke
import me.kenzierocks.mcpide.mcp.McpContext
import me.kenzierocks.mcpide.mcp.McpFunction
import me.kenzierocks.mcpide.util.HashBuilding
import me.kenzierocks.mcpide.util.JavaExec
import me.kenzierocks.mcpide.util.computeOutput
import me.kenzierocks.mcpide.util.suspendFor
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private val REPLACE_PATTERN = Regex("^\\{(\\w+)}$")

class ExecuteFunction(
    private val jar: Path,
    private val jvmArgs: List<String>,
    private var progArgs: List<String>,
    private val data: Map<String, String>
) : McpFunction {

    override suspend fun initialize(context: McpContext, zip: ZipFile) {
        analyzeAndExtract(context, zip, jvmArgs)
        analyzeAndExtract(context, zip, progArgs)
    }

    override suspend fun invoke(context: McpContext): Path {
        val arguments = context.arguments.toMutableMap()
        val outputExt = arguments.getOrDefault("outputExtension", "jar") as String
        val output = arguments.computeIfAbsent("output") { context.file("output.$outputExt") } as Path
        arguments.computeIfAbsent("log") { context.file("log.log") }

        val replacedArgs = mutableMapOf<String, Any>()
        val jvmArgList = applyVariableSubstitutions(context, jvmArgs, arguments, replacedArgs)
        val progArgList = applyVariableSubstitutions(context, progArgs, arguments, replacedArgs)

        replacedArgs.remove("output")
        replacedArgs.remove("log")

        val hashStore = with(HashBuilding) {
            StringBuilder().apply {
                addArgs("args", progArgList)
                addArgs("jvmArgs", jvmArgList)
                addEntry("jar", jar)
                replacedArgs.forEach { (name, value) ->
                    addEntry(name, value)
                }
            }
        }
        val hashInput = context.file("inputhash.properties")
        Files.createDirectories(hashInput.parent)
        Files.writeString(hashInput, hashStore)
        computeOutput(hashInput, output) {
            Files.deleteIfExists(output)

            val workingDir = context.workingDirectory
            Files.createDirectories(workingDir)

            val mainClass = JarFile(jar.toFile()).use { jarFile ->
                jarFile.manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS)
            }

            context.logger.apply {
                info { (listOf("JVM Args:") + jvmArgList).joinToString("\t\n") }
                info { (listOf("Program Args:") + progArgList).joinToString("\t\n") }
                info { "Classpath: ${jar.toAbsolutePath()}" }
                info { "Working Directory: ${workingDir.toAbsolutePath()}" }
                info { "Main Class: $mainClass" }
            }

            JavaExec(
                jvmArgList,
                progArgList,
                listOf(jar),
                workingDir,
                mainClass
            ).use {
                val exec = it.execute()

                // Inform process no input is coming
                exec.outputStream.close()

                // Copy from the input/error stream (merged in JavaExec)
                // Use a liberal UTF-8 decoder, in case the java program isn't well behaved
                Dispatchers.IO {
                    InputStreamReader(exec.inputStream, StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)).useLines { lines ->
                        lines.forEach { line ->
                            context.logger.info { line }
                        }
                    }
                }

                val exitCode = exec.suspendFor()
                if (exitCode != 0) {
                    throw IllegalStateException("Process failed, exit code $exitCode")
                }
            }
        }

        return output
    }

    private fun applyVariableSubstitutions(context: McpContext, list: List<String>, arguments: Map<String, Any>, inputs: MutableMap<String, Any>): List<String> {
        return list.map { s ->
            applyVariableSubstitutions(context, s, arguments, inputs)
        }
    }

    private fun applyVariableSubstitutions(context: McpContext, value: String, arguments: Map<String, Any>, inputs: MutableMap<String, Any>): String {
        val matcher = REPLACE_PATTERN.find(value) ?: return value

        val argName = matcher.groupValues[1]
        if (argName.isNotEmpty()) {
            val argument = arguments[argName]
            if (argument is Path) {
                inputs[argName] = argument
                return argument.toAbsolutePath().toString()
            } else if (argument is String) {
                inputs[argName] = argument
                return argument
            }

            val dataElement = data[argName]
            if (dataElement != null) {
                inputs[argName] = context.file(dataElement)
                return dataElement
            }
        }
        throw IllegalStateException("The string '$value' did not return a valid substitution match!")
    }

    private fun analyzeAndExtract(context: McpContext, zip: ZipFile, args: List<String>) {
        for (arg in args) {
            val matcher = REPLACE_PATTERN.find(arg) ?: continue

            val argName = matcher.groupValues[1].takeUnless { it.isEmpty() } ?: continue

            val referencedData = data[argName] ?: continue

            val entry = zip.getEntry(referencedData) ?: continue
            val entryName = entry.name

            if (entry.isDirectory) {
                zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .filter { it.name.startsWith(entryName) }
                    .forEach { e ->
                        extractFile(zip, e, context.file(e.name))
                    }
            } else {
                val out = context.file(entryName)
                extractFile(zip, entry, out)
            }
        }
    }

    private fun extractFile(zip: ZipFile, entry: ZipEntry, out: Path) {
        Files.createDirectories(out.parent)

        zip.getInputStream(entry).use {
            Files.copy(it, out, StandardCopyOption.REPLACE_EXISTING)
        }
    }

}