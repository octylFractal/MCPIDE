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

package me.kenzierocks.mcpide.fx

import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import dagger.BindsInstance
import dagger.Component
import dagger.Subcomponent
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
import me.kenzierocks.mcpide.inject.ProjectScope
import me.kenzierocks.mcpide.inject.RepositorySystemModule
import me.kenzierocks.mcpide.mcp.McpRunner
import me.kenzierocks.mcpide.mcp.McpRunnerCreator
import me.kenzierocks.mcpide.mcp.McpTypeSolver
import me.kenzierocks.mcpide.util.computeOutput
import me.kenzierocks.mcpide.util.gradleCoordsToMaven
import org.eclipse.aether.artifact.DefaultArtifact
import org.fxmisc.richtext.model.ReadOnlyStyledDocument
import org.fxmisc.richtext.model.SegmentOps
import org.fxmisc.richtext.model.SimpleEditableStyledDocument
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
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
 * Runs [AstSpanMarker] logic over every file
 * in the MC jar. Just to be sure.
 */
class AstSpanMarkerFullTest {

    companion object {

        private const val ENABLE_PROP = "test.ast.spans.full"

        @Component(
            modules = [
                RepositorySystemModule::class,
                HttpModule::class,
                CoroutineSupportModule::class,
                JsonModule::class,
                TestProjectComponent.Module::class
            ]
        )
        @Singleton
        interface TestComponent {
            val mavenAccess: MavenAccess
            val mcpTypeSolver: McpTypeSolver
            val runnerCreator: McpRunnerCreator
            val projectComponentBuilder: TestProjectComponent.Builder
        }

        @Subcomponent(
            modules = [JavaParserModule::class]
        )
        @ProjectScope
        interface TestProjectComponent {
            @dagger.Module(subcomponents = [TestProjectComponent::class])
            companion object Module

            @Subcomponent.Builder
            interface Builder {
                @BindsInstance
                fun typeSolver(typeSolver: TypeSolver): Builder

                fun javaParserModule(javaParserModule: JavaParserModule): Builder

                fun build(): TestProjectComponent
            }

            val astSpanMarkerCreator: AstSpanMarkerCreator
        }

        private lateinit var testComponent: TestComponent
        private lateinit var testProjectComponent: TestProjectComponent
        private lateinit var mcFs: FileSystem

        @JvmStatic
        @BeforeAll
        fun setUp() {
            if (System.getProperty(ENABLE_PROP) != "1") {
                return
            }
            testComponent = DaggerAstSpanMarkerFullTest_Companion_TestComponent.builder()
                .coroutineSupportModule(CoroutineSupportModule)
                .httpModule(HttpModule)
                .jsonModule(JsonModule)
                .repositorySystemModule(RepositorySystemModule)
                .build()

            val zip = testComponent.mavenAccess.resolveArtifactOrFail(
                DefaultArtifact(gradleCoordsToMaven("de.oceanlabs.mcp:mcp_config:1.14.4-20190719.225934@zip"))
            )
            val root = Path.of("./build/tmp/mcp")

            val mcpRunner = testComponent.runnerCreator.create(
                zip, "joined", root
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

            testProjectComponent = testComponent.projectComponentBuilder
                .typeSolver(typeSolver)
                .javaParserModule(JavaParserModule)
                .build()
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
    @EnabledIfSystemProperty(named = ENABLE_PROP, matches = "1")
    fun passesEveryFile(path: Path) {
        try {
            val doc: JeaDoc = SimpleEditableStyledDocument(
                setOf(), DEFAULT_MAP_STYLE
            )
            doc.replace(0, 0, ReadOnlyStyledDocument.fromString(
                Files.readString(path), setOf(), DEFAULT_MAP_STYLE, SegmentOps.styledTextOps()
            ))

            val astSpans = testProjectComponent.astSpanMarkerCreator.create(
                mcFs.getPath("/"), doc
            )
            astSpans.markAst()
        } catch (e: Exception) {
            assumeTrue(keepProviding.getAndSet(false))
            throw e
        }
    }
}