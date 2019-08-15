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

package me.kenzierocks.mcpide.fx

import javafx.geometry.Insets
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.Pane
import javafx.scene.paint.Paint
import javafx.stage.Popup
import me.kenzierocks.mcpide.ResourceUrl
import org.fxmisc.wellbehaved.event.EventPattern.keyPressed
import org.fxmisc.wellbehaved.event.InputHandler
import org.fxmisc.wellbehaved.event.InputMap.consume
import org.fxmisc.wellbehaved.event.InputMap.process
import org.fxmisc.wellbehaved.event.Nodes

class RenameDialog private constructor(
    val popup: Popup,
    val textField: TextField
) {

    companion object {
        fun create(): RenameDialog {
            val popup = Popup()
            val parent = Pane()
            parent.stylesheets.add(ResourceUrl("darktheme.css").toExternalForm())
            val textField = TextField().apply {
                background = Background(BackgroundFill(
                    Paint.valueOf("lightgray"),
                    CornerRadii.EMPTY, Insets.EMPTY
                ))
            }
            parent.children.add(textField)
            popup.content.add(parent)

            popup.isAutoHide = true

            Nodes.addInputMap(textField, process(keyPressed(KeyCode.ESCAPE)) {
                textField.text = ""
                InputHandler.Result.PROCEED
            })
            Nodes.addInputMap(textField, consume(keyPressed(KeyCode.ENTER)) {
                popup.hide()
            })

            return RenameDialog(popup, textField)
        }
    }

}