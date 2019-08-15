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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dagger.Module
import dagger.Provides
import javafx.fxml.FXMLLoader
import javafx.util.Callback
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.runBlocking
import me.kenzierocks.mcpide.comms.ModelComms
import me.kenzierocks.mcpide.comms.ModelMessage
import me.kenzierocks.mcpide.comms.PublishComms
import me.kenzierocks.mcpide.comms.ViewComms
import me.kenzierocks.mcpide.comms.ViewMessage
import me.kenzierocks.mcpide.controller.FileAskDialogController
import me.kenzierocks.mcpide.controller.MainController
import me.kenzierocks.mcpide.data.FileCache
import me.kenzierocks.mcpide.util.openErrorDialog
import mu.KotlinLogging
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.nio.charset.StandardCharsets
import javax.inject.Provider
import javax.inject.Singleton

@Module
object CoroutineSupportModule {
    @[Provides Singleton]
    fun provideCoroutineExceptionHandler(): CoroutineExceptionHandler {
        val logger = KotlinLogging.logger("unhandled-exceptions")
        return CoroutineExceptionHandler { ctx, e ->
            logger.warn(e) { "Unhandled exception in ${ctx[CoroutineName]}" }
            runBlocking { e.openErrorDialog() }
        }
    }
}

@Module
object CommsModule {

    private val modelChannel = Channel<ModelMessage>(100)
    private val viewChannel = Channel<ViewMessage>(100)

    @[Provides Singleton]
    fun provideViewComms() = ViewComms(modelChannel, viewChannel)

    @[Provides Singleton]
    fun provideModelComms() = ModelComms(modelChannel, viewChannel)

    @[Provides Singleton]
    fun providePublishComms() = PublishComms(modelChannel, viewChannel)
}

@Module
object ViewModule {
    @[Provides Singleton View]
    fun provideViewScope(coroutineExceptionHandler: CoroutineExceptionHandler) =
        CoroutineScope(Dispatchers.JavaFx
            + CoroutineName("View")
            + coroutineExceptionHandler
            + SupervisorJob())
}

@Module
object ModelModule {
    @[Provides Singleton Model]
    fun provideModelScope(coroutineExceptionHandler: CoroutineExceptionHandler) =
        CoroutineScope(Dispatchers.Default
            + CoroutineName("ModelWorker")
            + coroutineExceptionHandler
            + SupervisorJob())
}

@Module
object HttpModule {
    @[Provides Singleton]
    fun provideHttpClient(fileCache: FileCache) =
        OkHttpClient.Builder()
            .cache(Cache(fileCache.okHttpCacheDirectory.toFile(), 10_000_000))
            .build()
}

@Module
object CsvModule {
    @[Provides Singleton]
    fun provideCsvMapper() = CsvMapper().apply { findAndRegisterModules() }

    @[Provides Singleton Srg]
    fun provideSrgSchema(mapper: CsvMapper): CsvSchema =
        mapper.schemaFor(jacksonTypeRef<SrgMapping>())
            .withSkipFirstDataRow(true)

    @[Provides Singleton Srg]
    fun provideSrgWriter(mapper: CsvMapper, @Srg schema: CsvSchema): ObjectWriter =
        mapper.writer(schema)

    @[Provides Singleton Srg]
    fun provideSrgReader(mapper: CsvMapper, @Srg schema: CsvSchema): ObjectReader =
        mapper.readerFor(jacksonTypeRef<SrgMapping>()).with(schema)
}

@Module
object JsonModule {
    @[Provides Singleton]
    fun provideJsonMapper() =
        ObjectMapper().apply {
            findAndRegisterModules()
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
}

@Module
object XmlModule {
    @[Provides Singleton]
    fun provideXmlMapper() =
        XmlMapper().apply {
            findAndRegisterModules()
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
}

interface ControllerFactory : Callback<Class<*>, Any>

private inline fun <reified T> controllerBind(source: Provider<T>): Pair<Class<*>, () -> T> =
    T::class.java to source::get

@Module
object FxModule {
    @[Provides Singleton]
    fun provideControllerFactory(
        mainController: Provider<MainController>,
        fileAskDialogController: Provider<FileAskDialogController>
    ): ControllerFactory {
        val controllers = mapOf(
            controllerBind(mainController),
            controllerBind(fileAskDialogController)
        )
        return object : ControllerFactory {
            override fun call(cls: Class<*>) =
                (controllers[cls] ?: throw IllegalStateException("No controller for class ${cls.name}"))()
        }
    }

    @Provides
    fun provideFxmlLoader(controllerFactory: ControllerFactory) =
        FXMLLoader(null, null, null, controllerFactory, StandardCharsets.UTF_8)

}
