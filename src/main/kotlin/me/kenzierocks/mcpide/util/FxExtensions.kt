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

import com.google.common.base.Throwables
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Dialog
import javafx.scene.control.DialogPane
import javafx.scene.control.TextArea
import javafx.scene.layout.Border
import javafx.scene.layout.Region
import javafx.scene.shape.SVGPath
import javafx.scene.text.Font
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
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

/**
 * Open a dialog showing the error to the user, and suspend until it is closed.
 */
suspend fun Throwable.openErrorDialog(title: String = "Error",
                                      header: String = "An error occurred in MCPIDE") {
    withContext(Dispatchers.JavaFx) {
        val dialog = Alert(Alert.AlertType.ERROR)
        dialog.title = title
        val label = TextArea(Throwables.getStackTraceAsString(this@openErrorDialog))
        label.font = Font.font("Monospaced", 16.0)
        label.prefColumnCount = (label.paragraphs.map { it.length }.max() ?: 80) + 16
        label.prefRowCount = label.paragraphs.size + 5
        label.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE)
        label.isEditable = false
        label.border = Border.EMPTY
        dialog.dialogPane.content = label
        dialog.headerText = header
        dialog.isResizable = true
        dialog.dialogPane.setPrefSizeFromContent()
        dialog.showAndSuspend()
    }
}
