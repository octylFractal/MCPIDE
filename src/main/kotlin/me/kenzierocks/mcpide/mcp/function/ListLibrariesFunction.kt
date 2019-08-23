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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.kenzierocks.mcpide.data.MojangPackageManifest
import me.kenzierocks.mcpide.inject.MavenAccess
import me.kenzierocks.mcpide.mcp.McpContext
import me.kenzierocks.mcpide.mcp.McpFunction
import me.kenzierocks.mcpide.mcp.getStepOutput
import me.kenzierocks.mcpide.util.gradleCoordsToMaven
import org.eclipse.aether.artifact.DefaultArtifact
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListLibrariesFunction @Inject constructor(
    private val mapper: ObjectMapper,
    private val mavenAccess: MavenAccess
) : McpFunction {

    override suspend fun invoke(context: McpContext): Path {
        val output = context.arguments.getOrElse("output", { context.file("libraries.txt") }) as Path

        val result = Files.newBufferedReader(context.getStepOutput<DownloadPackageManifestFunction>()).use { reader ->
            mapper.readValue<MojangPackageManifest>(reader)
        }

        val files = result.libraries.map {
            mavenAccess.resolveArtifactOrFail(
                DefaultArtifact(gradleCoordsToMaven(it.name)),
                messageFunc = { IllegalStateException("Unable to resolve ${it.name}.") }
            )
        }.toSet()

        Files.deleteIfExists(output)
        Files.createDirectories(output.parent)

        Files.newBufferedWriter(output).use { writer ->
            files.forEach { f -> writer.append("-e=").append(f.toAbsolutePath().toString()).append('\n') }
        }

        return output
    }
}