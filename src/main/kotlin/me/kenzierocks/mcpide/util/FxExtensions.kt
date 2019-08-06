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

package me.kenzierocks.mcpide.util

import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.scene.control.Dialog
import javafx.scene.control.DialogPane
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Set the preferred size of the dialog pane to the preferred size of [DialogPane.content].
 */
fun DialogPane.setPrefSizeFromContent() {
    scene.window.sizeToScene()
    // we need to do this again a bit later to properly size things.
    scene.window.onShown = EventHandler {
        CoroutineScope(Dispatchers.Main + CoroutineName("PrefSizeUpdate")).launch {
            delay(100)
            scene.window.sizeToScene()
        }
    }
}

/**
 * [Dialog.showAndWait], but instead of using a nested event-loop, suspends.
 */
suspend fun <R> Dialog<R>.showAndSuspend(): R? {
    show()
    return suspendCoroutine { cont ->
        resultProperty().addListener(object : ChangeListener<R> {
            override fun changed(observable: ObservableValue<out R>?, oldValue: R, newValue: R) {
                cont.resume(newValue)
                resultProperty().removeListener(this)
            }
        })
    }
}
