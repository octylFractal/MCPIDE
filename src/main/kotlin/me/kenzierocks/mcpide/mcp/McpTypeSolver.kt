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

import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.kenzierocks.mcpide.inject.MavenAccess
import me.kenzierocks.mcpide.util.gradleCoordsToMaven
import me.kenzierocks.mcpide.util.typesolve.JavaParserTypeSolver
import mu.KotlinLogging
import org.eclipse.aether.artifact.DefaultArtifact
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

class McpTypeSolver @Inject constructor(
    private val mavenAccess: MavenAccess
) {
    private val logger = KotlinLogging.logger {}

    suspend fun buildFrom(mcpRunner: McpRunner): TypeSolver {
        val minecraftJar = requireNotNull(mcpRunner.steps["patch"]?.output) {
            "Runner should be run to the 'patch' step first."
        }
        val libraries = requireNotNull(mcpRunner.steps["listLibraries"]?.output) {
            "Runner should be run to the 'listLibraries' step first."
        }
        return buildFromCustom(mcpRunner, minecraftJar, libraries)
    }

    // Allow custom setup of these files:
    suspend fun buildFromCustom(mcpRunner: McpRunner, minecraftJar: Path, librariesList: Path) : TypeSolver {
        logger.debug { "Added library for type solving: $minecraftJar" }
        val mcFs = FileSystems.newFileSystem(minecraftJar)
        val typeSolver = CombinedTypeSolver(
            CombinedTypeSolver.ExceptionHandlers.IGNORE_ALL,
            JavaParserTypeSolver(mcFs.getPath("/"))
        )
        Files.newBufferedReader(librariesList).useLines { lines ->
            lines.map { it.substringAfter("-e=") }.forEach {
                logger.debug { "Added library for type solving: $it" }
                typeSolver.add(JarTypeSolver(it))
            }
        }
        mcpRunner.config.libraries["joined"]
            .asFlow()
            .flatMapConcat { lib -> flowOf(lib).map { resolveLibrary(lib) }.flowOn(Dispatchers.IO) }
            .collect { typeSolver.add(JarTypeSolver(it)) }
        // Stuff the JDK into there as well. Just use ours, it should be backward-compat with 8
        typeSolver.add(ClassLoaderTypeSolver(ClassLoader.getPlatformClassLoader()))
        return typeSolver
    }

    private fun resolveLibrary(library: String): Path {
        return mavenAccess.resolveArtifactOrFail(
            DefaultArtifact(gradleCoordsToMaven(library))
        )
    }
}
