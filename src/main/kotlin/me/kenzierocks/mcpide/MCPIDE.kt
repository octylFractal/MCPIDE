package me.kenzierocks.mcpide

import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
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
    startKoin {
        logger(KLogLogger(KotlinLogging.logger("Koin")))

        modules(listOf(
            appModule, viewModule, modelModule, httpModule, jacksonModule, appInstanceModule
        ))
    }

    System.setErr(getLoggingPrintStream { it::error })

    Application.launch(MCPIDE::class.java, *args)
}

private fun getLoggingPrintStream(loggerMethod: (KLogger) -> LineConsumer) =
    PrintStream(LineOutputStream(loggerMethod(KotlinLogging.logger("SYSTEM"))), true, StandardCharsets.UTF_8)
