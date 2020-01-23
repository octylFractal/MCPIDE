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

package me.kenzierocks.mcpide.inject

import dagger.Module
import dagger.Provides
import javafx.fxml.FXMLLoader
import javafx.util.Callback
import me.kenzierocks.mcpide.controller.ExportableMappingsController
import me.kenzierocks.mcpide.controller.FileAskDialogController
import me.kenzierocks.mcpide.controller.FindInPathController
import me.kenzierocks.mcpide.controller.FindPopupController
import me.kenzierocks.mcpide.controller.MainController
import java.nio.charset.StandardCharsets
import javax.inject.Provider
import javax.inject.Singleton


interface ControllerFactory : Callback<Class<*>, Any>

private inline fun <reified T> controllerBind(source: Provider<T>): Pair<Class<*>, () -> T> =
    T::class.java to source::get

private class MapBasedControllerFactory(
    private val controllers: Map<Class<*>, () -> Any>
) : ControllerFactory {
    override fun call(cls: Class<*>) =
        (controllers[cls] ?: throw IllegalStateException("No controller for class ${cls.name}"))()
}

private fun mapBasedControllerFactory(vararg controllerBinds: Pair<Class<*>, () -> Any>): MapBasedControllerFactory {
    return MapBasedControllerFactory(mapOf(*controllerBinds))
}

private fun newFxmlLoader(controllerFactory: ControllerFactory) =
    FXMLLoader(null, null, null, controllerFactory, StandardCharsets.UTF_8)

@Module
object FxModule {
    @[Provides Singleton]
    fun provideControllerFactory(
        mainController: Provider<MainController>,
        fileAskDialogController: Provider<FileAskDialogController>,
        exportableMappingsController: Provider<ExportableMappingsController>,
        findPopupController: Provider<FindPopupController>
    ): ControllerFactory {
        return mapBasedControllerFactory(
            controllerBind(mainController),
            controllerBind(fileAskDialogController),
            controllerBind(exportableMappingsController),
            controllerBind(findPopupController)
        )
    }

    @Provides
    fun provideFxmlLoader(controllerFactory: ControllerFactory) = newFxmlLoader(controllerFactory)

}

@Module
object ProjectFxModule {
    @[Provides ProjectScope ProjectQ]
    fun provideControllerFactory(
        findInPathController: Provider<FindInPathController>
    ): ControllerFactory {
        return mapBasedControllerFactory(
            controllerBind(findInPathController)
        )
    }

    @[Provides ProjectQ]
    fun provideFxmlLoader(@ProjectQ controllerFactory: ControllerFactory) = newFxmlLoader(controllerFactory)

}
