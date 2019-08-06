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
import me.kenzierocks.mcpide.resolver.resolverModule
import me.kenzierocks.mcpide.util.KLogLogger
import me.kenzierocks.mcpide.util.LineConsumer
import me.kenzierocks.mcpide.util.LineOutputStream
import mu.KLogger
import mu.KotlinLogging
import org.koin.core.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.inject
import org.koin.dsl.module
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

private lateinit var APP_INSTANCE: MCPIDE

class MCPIDE : Application(), KoinComponent {

    init {
        APP_INSTANCE = this
    }

    private val viewEventLoop by inject<ViewEventLoop>()
    private val modelProcessing by inject<ModelProcessing>()
    private val fxmlFiles by inject<FxmlFiles>()

    lateinit var stage: Stage
        private set

    override fun start(primaryStage: Stage) {
        this.stage = primaryStage
        stage.scene = Scene(fxmlFiles.main().parent)
        stage.title = "MCPIDE"
        stage.show()
        stage.centerOnScreen()
        stage.isMaximized = true
        viewEventLoop.start()
        modelProcessing.start()
    }
}

private val appInstanceModule = module {
    single {
        when {
            ::APP_INSTANCE.isInitialized -> APP_INSTANCE
            else -> throw IllegalStateException("No app initialized yet!")
        }
    }
}

fun main(args: Array<String>) {
    val logger = KotlinLogging.logger { }
    try {
        val koin = startKoin {
            logger(KLogLogger(KotlinLogging.logger("Koin")))

            modules(listOf(
                appModule, viewModule, modelModule, httpModule, jacksonModule, appInstanceModule,
                resolverModule
            ))
        }

        System.setErr(getLoggingPrintStream { it::error })

        Application.launch(MCPIDE::class.java, *args)
    } catch (e: Throwable) {
        logger.error(e) { "Fatal exception occurred." }
        exitProcess(1)
    }
}

private fun getLoggingPrintStream(loggerMethod: (KLogger) -> LineConsumer) =
    PrintStream(LineOutputStream(loggerMethod(KotlinLogging.logger("SYSTEM"))), true, StandardCharsets.UTF_8)
