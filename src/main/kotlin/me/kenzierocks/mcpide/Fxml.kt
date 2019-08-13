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

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import me.kenzierocks.mcpide.controller.FileAskDialogController
import me.kenzierocks.mcpide.controller.MainController
import java.net.URL
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class LoadedParent<T : Parent, C>(val parent: T, val controller: C)

@Singleton
class FxmlFiles @Inject constructor(
    private val fxmlLoader: Provider<FXMLLoader>
) {
    private inline fun <reified T : Parent, reified C> load(location: String): LoadedParent<T, C> {
        val loader = fxmlLoader.get()
        loader.location = FxmlRefClass.relativeUrl(location)
        // Enforce generics now, to prevent CCE later
        val parent: T = T::class.java.cast(loader.load())
        val controller: C = C::class.java.cast(loader.getController())
        return LoadedParent(parent, controller)
    }

    fun main() = load<Parent, MainController>("Main.fxml")
    fun fileAskDialog() = load<Parent, FileAskDialogController>("FileAskDialog.fxml")
}

private object FxmlRefClass {
    fun relativeUrl(location: String): URL {
        return javaClass.getResource(location) ?: throw IllegalArgumentException("No resource at $location")
    }
}
