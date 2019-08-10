package me.kenzierocks.mcpide.mcp.function

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.kenzierocks.mcpide.data.MojangPackageManifest
import me.kenzierocks.mcpide.mcp.McpContext
import me.kenzierocks.mcpide.mcp.McpFunction
import me.kenzierocks.mcpide.mcp.getStepOutput
import me.kenzierocks.mcpide.resolver.RemoteRepositories
import me.kenzierocks.mcpide.util.gradleCoordsToMaven
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.resolution.ArtifactRequest
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.nio.file.Files
import java.nio.file.Path

object ListLibrariesFunction : McpFunction, KoinComponent {

    private val mapper by inject<ObjectMapper>()
    private val resolver by inject<RepositorySystem>()
    private val session by inject<RepositorySystemSession>()
    private val repositories by inject<RemoteRepositories>()

    override suspend fun invoke(context: McpContext): Path {
        val output = context.arguments.getOrElse("output", { context.file("libraries.txt") }) as Path

        val result = Files.newBufferedReader(context.getStepOutput<DownloadPackageManifestFunction>()).use { reader ->
            mapper.readValue<MojangPackageManifest>(reader)
        }

        val files = result.libraries.map {
            val artifact = resolver.resolveArtifact(session,
                ArtifactRequest(DefaultArtifact(gradleCoordsToMaven(it.name)), repositories, ""))
            when {
                artifact.isResolved -> artifact.artifact.file
                else -> {
                    val e = IllegalStateException("Unable to resolve ${it.name}.")
                    artifact.exceptions.forEach(e::addSuppressed)
                    throw e
                }
            }
        }.toSet()

        Files.deleteIfExists(output)
        Files.createDirectories(output.parent)

        Files.newBufferedWriter(output).use { writer ->
            files.forEach { f -> writer.append("-e=").append(f.absolutePath).append('\n') }
        }

        return output
    }
}