package me.kenzierocks.mcpide.fx

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import dagger.Component
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.runBlocking
import me.kenzierocks.mcpide.inject.CoroutineSupportModule
import me.kenzierocks.mcpide.inject.HttpModule
import me.kenzierocks.mcpide.inject.JavaParserModule
import me.kenzierocks.mcpide.inject.JsonModule
import me.kenzierocks.mcpide.inject.MavenAccess
import me.kenzierocks.mcpide.inject.RepositorySystemModule
import me.kenzierocks.mcpide.mcp.McpConfig
import me.kenzierocks.mcpide.mcp.McpRunner
import me.kenzierocks.mcpide.mcp.McpRunnerCreator
import me.kenzierocks.mcpide.mcp.McpTypeSolver
import me.kenzierocks.mcpide.util.JavaParserTypeSolver
import me.kenzierocks.mcpide.util.computeOutput
import me.kenzierocks.mcpide.util.gradleCoordsToMaven
import org.eclipse.aether.artifact.DefaultArtifact
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream
import javax.inject.Singleton

/**
 * Runs [SpansScope] logic over every file
 * in the MC jar. Just to be sure.
 */
//@EnabledIfSystemProperty(named = "test.ast.spans.full", matches = "1")
class AstSpansFullTest {

    companion object {

        @Component(
            modules = [
                RepositorySystemModule::class,
                HttpModule::class,
                CoroutineSupportModule::class,
                JsonModule::class,
                JavaParserModule::class
            ]
        )
        @Singleton
        interface TestComponent {
            val mavenAccess: MavenAccess
            val mcpTypeSolver: McpTypeSolver
            val runnerCreator: McpRunnerCreator
            val astSpansCreator: AstSpansCreator
            val objectMapper: ObjectMapper
        }

        private lateinit var testComponent: TestComponent
        private lateinit var astSpans: AstSpans
        private lateinit var mcFs: FileSystem

        @JvmStatic
        @BeforeAll
        fun setUp() {
            testComponent = DaggerAstSpansFullTest_Companion_TestComponent.builder()
                .coroutineSupportModule(CoroutineSupportModule)
                .httpModule(HttpModule)
                .javaParserModule(JavaParserModule)
                .jsonModule(JsonModule)
                .repositorySystemModule(RepositorySystemModule)
                .build()

            val zip = testComponent.mavenAccess.resolveArtifactOrFail(
                DefaultArtifact(gradleCoordsToMaven("de.oceanlabs.mcp:mcp_config:1.14.4-20190719.225934@zip"))
            )
            val root = Path.of("./build/tmp/mcp")

            val zipFs = FileSystems.newFileSystem(zip, null)
            val mcpConfig = Files.newInputStream(zipFs.getPath("config.json")).use { input ->
                testComponent.objectMapper.readValue<McpConfig>(input)
            }
            val mcpRunner = testComponent.runnerCreator.create(
                zip, mcpConfig, "joined", root
            )

            val messageActor = GlobalScope.actor<String>(capacity = CONFLATED) {
                for (msg in channel) {
                    println(msg)
                }
            }

            val mcJar = readMinecraftJar(root, zip, messageActor, mcpRunner)
            val libList = readLibList(root, zip, messageActor, mcpRunner)

            mcFs = FileSystems.newFileSystem(mcJar, null)
            val typeSolver = runBlocking {
                testComponent.mcpTypeSolver.buildFromCustom(
                    mcpRunner, mcJar, libList
                )
            }
            astSpans = testComponent.astSpansCreator.create(
                mcFs.getPath("/"),
                JavaParserFacade.get(typeSolver)
            )
        }

        private fun readMinecraftJar(root: Path, zip: Path, messageActor: SendChannel<String>, mcpRunner: McpRunner): Path {
            val mcJar = root.resolve("minecraft.jar")
            computeOutput(zip, mcJar) {
                runBlocking(Dispatchers.Default) {
                    messageActor.send("Test Decompile: Starting")
                    Files.copy(
                        mcpRunner.run { messageActor.send("\rTest Decompile: $it") },
                        mcJar, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            return mcJar
        }

        private fun readLibList(root: Path, zip: Path, messageActor: SendChannel<String>, mcpRunner: McpRunner): Path {
            val libList = root.resolve("libraries.txt")
            computeOutput(zip, libList) {
                Files.copy(runBlocking(Dispatchers.Default) {
                    messageActor.send("Test Libraries: Starting")
                    mcpRunner.run(stop = "listLibraries") { messageActor.send("\rTest Libraries: $it") }
                }, libList, StandardCopyOption.REPLACE_EXISTING)
            }
            return libList
        }

        private val keepProviding = AtomicBoolean(true)

        @JvmStatic
        fun everyFile(): Stream<Path> {
            return Files.walk(mcFs.getPath("/"))
                .filter { it.toString().endsWith(".java") }
                .takeWhile { keepProviding.get() }
        }
    }

    @ParameterizedTest
    @MethodSource("everyFile")
    fun passesEveryFile(path: Path) {
        try {
            val result = astSpans.highlightText(Files.readString(path))
            println("### $path")
            println(result.newText)
        } catch (e: Exception) {
            keepProviding.set(false)
            throw e
        }
    }
}