package me.kenzierocks.mcpide

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import javafx.fxml.FXMLLoader
import javafx.util.Callback
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.kenzierocks.mcpide.comms.comms
import me.kenzierocks.mcpide.controller.MainController
import me.kenzierocks.mcpide.controller.ProjectInitController
import me.kenzierocks.mcpide.data.FileCache
import mu.KotlinLogging
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.coroutines.CoroutineContext

private val COMMS = comms()
val LOGGER = KotlinLogging.logger("unhandled-exceptions")
val CO_EXCEPTION_HANDLER: (CoroutineContext, Throwable) -> Unit = { ctx, e ->
    LOGGER.warn(e) { "Unhandled exception in ${ctx[CoroutineName]}" }
}

val viewModule = module {
    single(View) {
        CoroutineScope(Dispatchers.Main
            + CoroutineName("View")
            + CoroutineExceptionHandler(CO_EXCEPTION_HANDLER)
            + SupervisorJob())
    }
    single { COMMS.first }
    single { MainController(get(), get(), workerScope = get(App), viewScope = get(View), fxmlFiles = get()) }
    single { ViewEventLoop(get(View), get(), get()) }
}

val modelModule = module {
    single { COMMS.second }
    single { ModelProcessing(get(App), get()) }
}

val httpModule = module {
    single {
        OkHttpClient.Builder()
            .cache(Cache(get<FileCache>().okHttpCacheDirectory.toFile(), 10_000_000))
            .build()
    }
}

val jacksonModule = module {
    single {
        XmlMapper().apply {
            findAndRegisterModules()
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }
    single {
        ObjectMapper().apply {
            findAndRegisterModules()
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }
}

private inline fun <reified T> Scope.controllerEntry(): Pair<Class<*>, T> {
    return T::class.java to get(T::class, null, null)
}

private inline fun <reified T> Scope.controllerCreator(
    crossinline block: Scope.() -> T
): Pair<Class<*>, () -> T> {
    return T::class.java to { this.block() }
}

val appModule = module {
    single(App) {
        CoroutineScope(Dispatchers.Default
            + CoroutineName("AppWorker")
            + CoroutineExceptionHandler(CO_EXCEPTION_HANDLER)
            + SupervisorJob())
    }
    single<Callback<Class<*>, Any>>(ControllerFactory) {
        val staticControllers = mapOf(
            controllerEntry<MainController>()
        )
        val freshControllers = mapOf(
            controllerCreator {
                ProjectInitController(
                    get(), get(), get(), get(), get(),
                    workerScope = get(App), viewScope = get(View)
                )
            }
        )
        return@single Callback {
            staticControllers[it]
                ?: freshControllers[it]?.invoke()
                ?: throw IllegalStateException("No controller for class $it")
        }
    }
    single<(URL) -> FXMLLoader>(FxmlLoader) {
        return@single {
            FXMLLoader(it, null, null, get(ControllerFactory), StandardCharsets.UTF_8)
        }
    }
    single { FxmlFiles(get(FxmlLoader)) }
    single {
        val configDir = Paths.get(System.getenv("XDG_CONFIG_DIRECTORY")
            ?: "${System.getProperty("user.home", ".")}/.config")
        val mcpDir = configDir.resolve("mcpide")
        Files.createDirectories(mcpDir)
        FileCache(mcpDir)
    }
}
