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

package me.kenzierocks.mcpide

import dagger.BindsInstance
import dagger.Component
import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import me.kenzierocks.mcpide.resolver.RepositorySystemModule
import me.kenzierocks.mcpide.util.LineConsumer
import me.kenzierocks.mcpide.util.LineOutputStream
import mu.KLogger
import mu.KotlinLogging
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import javax.inject.Singleton
import kotlin.system.exitProcess

class MCPIDE : Application() {

    private val logger = KotlinLogging.logger {  }

    private val component: MCPIDEComponent = DaggerMCPIDEComponent.builder()
        .appInstance(this)
        .coroutineSupportModule(CoroutineSupportModule)
        .commsModule(CommsModule)
        .viewModule(ViewModule)
        .modelModule(ModelModule)
        .httpModule(HttpModule)
        .csvModule(CsvModule)
        .jsonModule(JsonModule)
        .xmlModule(XmlModule)
        .fxModule(FxModule)
        .mavenModule(RepositorySystemModule)
        .build()

    lateinit var stage: Stage
        private set

    override fun start(primaryStage: Stage) {
        logger.info { "Setting up primary stage" }
        this.stage = primaryStage
        val (parent, controller) = component.fxmlFiles.main()
        stage.scene = Scene(parent)
        stage.title = "MCPIDE"
        stage.show()
        stage.centerOnScreen()
        stage.isMaximized = true
        logger.info { "Primary stage opened." }
        controller.startEventLoop()
        component.modelProcessing.start()
        logger.info { "Started event loops." }
    }
}

@[Singleton Component(
    modules = [
        CoroutineSupportModule::class,
        CommsModule::class,
        ViewModule::class,
        ModelModule::class,
        HttpModule::class,
        CsvModule::class,
        JsonModule::class,
        XmlModule::class,
        FxModule::class,
        RepositorySystemModule::class
    ]
)]
interface MCPIDEComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun appInstance(mcpide: MCPIDE): Builder

        fun coroutineSupportModule(module: CoroutineSupportModule): Builder
        fun commsModule(module: CommsModule): Builder
        fun viewModule(module: ViewModule): Builder
        fun modelModule(module: ModelModule): Builder
        fun httpModule(module: HttpModule): Builder
        fun csvModule(module: CsvModule): Builder
        fun jsonModule(module: JsonModule): Builder
        fun xmlModule(module: XmlModule): Builder
        fun fxModule(module: FxModule): Builder
        fun mavenModule(module: RepositorySystemModule): Builder

        fun build(): MCPIDEComponent
    }

    val modelProcessing: ModelProcessing
    val fxmlFiles: FxmlFiles
}

fun main(args: Array<String>) {
    val logger = KotlinLogging.logger { }
    try {
        logger.info { "Starting MCPIDE, version ${ManifestVersion.getProjectVersion()}" }
        System.setErr(getLoggingPrintStream { it::error })

        Application.launch(MCPIDE::class.java, *args)
    } catch (e: Throwable) {
        logger.error(e) { "Fatal exception occurred." }
        exitProcess(1)
    }
}

private fun getLoggingPrintStream(loggerMethod: (KLogger) -> LineConsumer) =
    PrintStream(LineOutputStream(loggerMethod(KotlinLogging.logger("SYSTEM"))), true, StandardCharsets.UTF_8)
