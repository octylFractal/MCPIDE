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

import com.google.common.collect.ImmutableMap
import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.VPos
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ContentDisplay
import javafx.scene.control.TextField
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.Paint
import javafx.scene.paint.RadialGradient
import javafx.scene.paint.Stop
import javafx.scene.text.TextAlignment
import javafx.stage.Popup
import javafx.stage.PopupWindow
import me.kenzierocks.mcpide.IIFE
import me.kenzierocks.mcpide.MCPIDE
import me.kenzierocks.mcpide.ReplacementType
import java.util.HashMap

object FXMLFactory {

    fun createRenameListEntry(): RenameListEntry {
        val root: Region = MCPIDE.loadParent("RenameListEntry.fxml")
        val oldName: TextField = root.lookupCast("#old-name")
        val newName: TextField = root.lookupCast("#new-name")
        val submit: Button = root.lookupCast("#submit")
        val revert: Button = root.lookupCast("#revert")
        val delete: Button = root.lookupCast("#delete")
        val typeChoice: Button = root.lookupCast("#type-choice")
        return RenameListEntry(root, oldName, newName, submit, revert, delete, typeChoice)
    }

}

class RenameListEntry(val root: Region,
                      val oldName: TextField,
                      val newName: TextField,
                      val submit: Button,
                      val revert: Button,
                      val delete: Button,
                      val typeChoice: Button) {

    private var handlerIds = 0
    private val handlers = HashMap<Int, (RenameListEntry) -> Unit>()
    private val lastValue: ObjectProperty<Pair<String, String>?>
        = SimpleObjectProperty(this, "lastValue", null)
    private val applicable: BooleanProperty
        = SimpleBooleanProperty(this, "applicable", false)
    private val replacementTypeProperty: ObjectProperty<ReplacementType>
        = SimpleObjectProperty(this, "replacementTypeProperty", ReplacementType.PARAMETER)
    var replacementType: ReplacementType
        get() = replacementTypeProperty.value
        set(value) = replacementTypeProperty.set(value)

    init {
        submit.onAction = EventHandler { event ->
            lastValue.value = Pair(oldName.text, newName.text)
            applicable.value = true
            evt()
        }
        submit.disableProperty().bind(applicable)
        revert.disableProperty().bind(lastValue.isNull.or(applicable))
        revert.onAction = EventHandler { event ->
            lastValue.value?.let {
                oldName.text = it.first
                newName.text = it.second
                applicable.value = true
            }
        }
        oldName.textProperty().addListener { obs ->
            applicable.value = false
            evt()
        }
        newName.textProperty().addListener { obs ->
            applicable.value = false
            evt()
        }

        configureReplacementTypeButton(replacementTypeProperty, typeChoice)
        replacementTypeProperty.addListener(InvalidationListener { evt() })
    }

    private fun evt() {
        handlers.forEach { i, callback ->
            callback(this)
        }
    }

    fun isApplicable() = applicable.get()
    fun getLastValue() = lastValue.get()

    fun getOldName(): String = oldName.text
    fun getNewName(): String = newName.text
    fun addHandler(callback: (RenameListEntry) -> Unit): Int {
        handlers.put(handlerIds++, callback)
        return handlerIds - 1
    }

}

private val SIZE = 25.0
private val PAINTS: Map<ReplacementType, Paint> = IIFE {
    val map = ImmutableMap.builder<ReplacementType, Paint>()

    fun insertGradient(rt: ReplacementType, color1: String, color2: String) {
        map.put(rt, RadialGradient(0.0, 0.0, 0.5, 0.5, 1.0, true, CycleMethod.NO_CYCLE,
            Stop(0.0, Color.web(color1)), Stop(0.5, Color.web(color2))))
    }

    insertGradient(ReplacementType.FIELD, "#FF0000", "#FF9000")
    insertGradient(ReplacementType.METHOD, "#3B00FF", "#ED00FF")
    insertGradient(ReplacementType.PARAMETER, "#00FF5B", "#00FDFF")
    insertGradient(ReplacementType.CUSTOM, "#0017FF", "#03CF15")

    map.build()
}

private fun ReplacementType.newNode(): Node {
    val canvas = Canvas(SIZE, SIZE)
    val ctx = canvas.graphicsContext2D
    ctx.fill = PAINTS[this]
    ctx.fillRect(0.0, 0.0, canvas.width, canvas.height)

    ctx.fill = Color.BLACK
    ctx.font = ctx.font.withSize(SIZE - 5.0)
    ctx.textAlign = TextAlignment.CENTER
    ctx.textBaseline = VPos.CENTER
    ctx.fillText(this.name[0].toString(), canvas.width / 2, canvas.height / 2)
    return canvas
}

private fun configureReplacementTypeButton(replType: ObjectProperty<ReplacementType>, btn: Button) {
    val popup = newReplacementTypePopup(replType)
    btn.graphicProperty().bind(replType.map { it.newNode() })
    btn.padding = Insets(2.0)

    btn.onAction = EventHandler { evt ->
        if (popup.isShowing) {
            popup.hide()
            return@EventHandler
        }
        val (x, y) = btn.globalLayoutCoords
        popup.show(btn, x - 7, y + btn.height - 5)
    }
}

fun newReplacementTypePopup(replType: ObjectProperty<ReplacementType>): PopupWindow {
    val popup = Popup()
    val parent = VBox(0.0)
    parent.children.addAll(ReplacementType.values().map { rt ->
        val iv = rt.newNode()
        val btn = Button("", iv)
        btn.contentDisplay = ContentDisplay.GRAPHIC_ONLY
        btn.padding = Insets(2.0)
        btn.onAction = EventHandler {
            replType.value = rt
            popup.hide()
        }
        btn
    })

    popup.isAutoHide = true
    popup.content.add(parent)
    return popup
}
