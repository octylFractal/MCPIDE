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
import me.kenzierocks.mcpide.util.JavaParserTypeSolver
import me.kenzierocks.mcpide.util.gradleCoordsToMaven
import org.eclipse.aether.artifact.DefaultArtifact
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

class McpTypeSolver @Inject constructor(
    private val mavenAccess: MavenAccess
) {
    suspend fun buildFrom(mcpRunner: McpRunner) : TypeSolver {
        val minecraftJar = requireNotNull(mcpRunner.steps["decompile"]?.output) {
            "Runner should be run to the 'decompile' step first."
        }
        val libraries = requireNotNull(mcpRunner.steps["listLibraries"]?.output) {
            "Runner should be run to the 'listLibraries' step first."
        }
        return buildFromCustom(mcpRunner, minecraftJar, libraries)
    }

    // Allow custom setup of these files:
    suspend fun buildFromCustom(mcpRunner: McpRunner, minecraftJar: Path, librariesList: Path) : TypeSolver {
        val mcFs = FileSystems.newFileSystem(minecraftJar, null)
        val typeSolver = CombinedTypeSolver(JavaParserTypeSolver(mcFs.getPath("/")))
        Files.newBufferedReader(librariesList).useLines { lines ->
            lines.map { it.substringAfter("-e=") }.forEach {
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