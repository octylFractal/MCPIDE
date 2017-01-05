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

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.binding.Bindings
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.text.Text
import javafx.stage.Popup
import javafx.util.Duration
import me.kenzierocks.mcpide.Core
import me.kenzierocks.mcpide.MCPIDE
import me.kenzierocks.mcpide.ReplacementType

/**
 * Based heavily upon [Tooltip].
 */
class RenameTooltip {

    companion object {
        private val POPUP_TIME = 1000L.toDouble()
        private val RESET_TIME = 500L.toDouble()
        private val KEY_REPL_TYPE = RenameTooltip::class.qualifiedName!! + ".replacementType"

        private val BEHAVIOUR = Behavior(POPUP_TIME, RESET_TIME)

        fun install(node: Text, replacementType: ReplacementType) {
            BEHAVIOUR.install(node, replacementType)
        }

        fun uninstall(node: Text) {
            BEHAVIOUR.uninstall(node)
        }
    }

    // mimics Tooltip.TooltipBehavior
    private class Behavior(popupTime: Double, resetTime: Double) {

        private data class ActiveData(val node: Text, val popup: Popup)

        // show timer is started on node enter
        // when show timer completes, show current popup
        private val showTimer = Timeline(KeyFrame(Duration(popupTime)))
        // hide timer is started on node exit
        // when hide timer completes, hide current popup
        private val hideTimer = Timeline(KeyFrame(Duration(resetTime)))

        private var lastMouseX: Double = 0.0
        private var lastMouseY: Double = 0.0
        // updates lastMouseX/Y on MOUSE_MOVED
        private val MOUSE_UPDATER = EventHandler { event: MouseEvent ->
            lastMouseX = event.screenX
            lastMouseY = event.screenY
        }

        private var activeNode: Text? = null
        private var showingData: ActiveData? = null

        private val ENTER_NODE = EventHandler { event: MouseEvent ->
            if (showTimer.status != Animation.Status.RUNNING) {
                activeNode = event.target as Text
                if (hideTimer.status == Animation.Status.RUNNING) {
                    // if we're waiting to hide, cancel to keep showing
                    hideTimer.stop()
                } else {
                    showTimer.playFromStart()
                }
            }
        }
        private val EXIT_NODE = EventHandler { event: MouseEvent ->
            if (showTimer.status == Animation.Status.RUNNING) {
                // in process of showing, so just cancel it
                showTimer.stop()
            } else if (showingData?.popup?.isShowing ?: false) {
                // start countdown if showing
                hideTimer.playFromStart()
            }
        }

        init {
            showTimer.onFinished = EventHandler {
                val data = ActiveData(activeNode!!, Popup())
                val node = data.node
                val popup = data.popup
                val replacementType = node.properties[KEY_REPL_TYPE] as ReplacementType

                val button = Button("Add rename")
                button.onAction = EventHandler {
                    Core.getCurrentCore().addRenameListEntry(node.text.to(""), replacementType)
                }
                popup.content.add(button)

                // shouldClose = !(node.isHover || button.isHover)
                val shouldCloseBinding = Bindings.not(node.hoverProperty().or(button.hoverProperty()))
                shouldCloseBinding.addListener { observableValue, old, shouldClose ->
                    if (shouldClose) {
                        popup.hide()
                    }
                }

                val x = lastMouseX
                val y = lastMouseY
                popup.show(MCPIDE.INSTANCE.stage, x, y)
            }
            hideTimer.onFinished = EventHandler {
                showingData!!.popup.hide()
                showingData = null
            }
        }

        fun install(node: Text, replacementType: ReplacementType) {
            node.addEventHandler(MouseEvent.MOUSE_ENTERED, ENTER_NODE)
            node.addEventHandler(MouseEvent.MOUSE_MOVED, MOUSE_UPDATER)
            node.addEventHandler(MouseEvent.MOUSE_EXITED, EXIT_NODE)

            node.properties[KEY_REPL_TYPE] = replacementType
        }

        fun uninstall(node: Text) {
            node.removeEventHandler(MouseEvent.MOUSE_ENTERED, ENTER_NODE)
            node.removeEventHandler(MouseEvent.MOUSE_MOVED, MOUSE_UPDATER)
            node.removeEventHandler(MouseEvent.MOUSE_EXITED, EXIT_NODE)

            node.properties.remove(KEY_REPL_TYPE)
        }
    }

}