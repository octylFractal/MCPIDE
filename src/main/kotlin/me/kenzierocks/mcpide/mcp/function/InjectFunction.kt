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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class InjectFunction(
    private val inject: String
) : McpFunction {

    private var template: String? = null
    private lateinit var added: Map<String, ByteArray>

    override suspend fun initialize(context: McpContext, zip: ZipFile) {
        val added = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(inject) }
            .associateTo(LinkedHashMap()) { ze ->
                val k = ze.name.substring(inject.length)
                val v = withContext(Dispatchers.IO) {
                    zip.getInputStream(ze).use { it.readAllBytes() }
                }
                k to v
            }
        added.remove("package-info-template.java")?.let { pkgInfo ->
            template = String(pkgInfo, StandardCharsets.UTF_8)
        }
        this.added = added
    }

    override suspend fun invoke(context: McpContext): Path {
        val input = context.arguments.getValue("input") as Path
        val output = context.file("output.jar")

        computeOutput(input, output) {
            Files.deleteIfExists(output)
            Files.createDirectories(output.parent)

            ZipInputStream(Files.newInputStream(input)).use { zis ->
                ZipOutputStream(Files.newOutputStream(output)).use { zos ->
                    withContext(Dispatchers.IO) {
                        copy(context, zis, zos)
                    }
                }
            }
        }

        return output
    }

    private fun copy(context: McpContext, zis: ZipInputStream, zos: ZipOutputStream) {
        val visited = mutableSetOf<String>()

        while (true) {
            val entry = zis.nextEntry ?: break
            zos.putNextEntry(entry)
            zis.copyTo(zos)
            val t = template
            if (t != null) {
                val pkg = when {
                    entry.isDirectory && !entry.name.endsWith("/") -> entry.name
                    else -> entry.name.substringBeforeLast('/')
                }
                if (visited.add(pkg) && pkg.startsWith("net/minecraft/")) {
                    val info = ZipEntry("$pkg/package-info.java")
                    info.time = 0
                    zos.putNextEntry(info)
                    zos.write(t.replace("{PACKAGE}", pkg.replace("/", ".")).toByteArray())
                }
            }
        }

        added.filterKeys {
            when (context.side) {
                "server" -> !it.contains("/client/")
                else -> !it.contains("/server/")
            }
        }.forEach { (name, value) ->
            val info = ZipEntry(name)
            info.time = 0
            zos.putNextEntry(info)
            zos.write(value)
        }
    }

}