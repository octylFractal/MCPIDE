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

import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import me.kenzierocks.mcpide.inject.CommsModule
import me.kenzierocks.mcpide.inject.CoroutineSupportModule
import me.kenzierocks.mcpide.inject.CsvModule
import me.kenzierocks.mcpide.inject.DaggerMCPIDEComponent
import me.kenzierocks.mcpide.inject.FxModule
import me.kenzierocks.mcpide.inject.HttpModule
import me.kenzierocks.mcpide.inject.JavaParserModule
import me.kenzierocks.mcpide.inject.JsonModule
import me.kenzierocks.mcpide.inject.MCPIDEComponent
import me.kenzierocks.mcpide.inject.ModelModule
import me.kenzierocks.mcpide.inject.ViewModule
import me.kenzierocks.mcpide.inject.XmlModule
import me.kenzierocks.mcpide.inject.ProjectComponent
import me.kenzierocks.mcpide.inject.RepositorySystemModule
import me.kenzierocks.mcpide.util.LineConsumer
import me.kenzierocks.mcpide.util.LineOutputStream
import mu.KLogger
import mu.KotlinLogging
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

class MCPIDE : Application() {

    private val logger = KotlinLogging.logger { }

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
        .parserModule(JavaParserModule)
        .projectModule(ProjectComponent)
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
        stage.setOnCloseRequest {
            if (!controller.confirmClose()) {
                it.consume()
            }
        }
        logger.info { "Primary stage opened." }
        controller.startEventLoop()
        component.modelProcessing.start()
        logger.info { "Started event loops." }
    }
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
