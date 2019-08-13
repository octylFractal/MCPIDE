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

import me.kenzierocks.mcpide.mcp.function.ExecuteFunction
import me.kenzierocks.mcpide.resolver.MavenAccess
import me.kenzierocks.mcpide.util.gradleCoordsToMaven
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository
import java.nio.file.Path
import javax.inject.Inject

class ExecuteFunctionProvider @Inject constructor(
    private val mavenAccess: MavenAccess
) {
    fun provide(step: McpConfig.Step,
                functions: Map<String, McpConfig.Function>,
                data: Map<String, String>): McpFunction {
        val jsonFunc = functions[step.type]
            ?: throw IllegalArgumentException("Invalid MCP config, unknown function type: ${step.type}")
        val jar = fetchFunction(step.type, jsonFunc)
        return ExecuteFunction(jar, jsonFunc.jvmArgs, jsonFunc.args, data)
    }

    private fun fetchFunction(type: String, func: McpConfig.Function): Path {
        val artifact = mavenAccess.resolveArtifact(DefaultArtifact(gradleCoordsToMaven(func.version)),
            listOf(func.repo).map { RemoteRepository.Builder(type, "default", func.repo).build() })
        if (artifact.isResolved) {
            return artifact.artifact.file.toPath()
        }
        throw IllegalArgumentException("No such function JAR: ${func.version} (resolving '$type')")
    }

}