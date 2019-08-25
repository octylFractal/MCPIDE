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

package me.kenzierocks.mcpide.controller

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.stage.WindowEvent
import javafx.util.StringConverter
import me.kenzierocks.mcpide.util.getValue
import me.kenzierocks.mcpide.util.setValue
import org.fxmisc.wellbehaved.event.EventPattern.keyPressed
import org.fxmisc.wellbehaved.event.InputMap.consume
import org.fxmisc.wellbehaved.event.Nodes
import javax.inject.Inject

class FindPopupController @Inject constructor() {

    @FXML
    private lateinit var root: Parent
    @FXML
    lateinit var textField: TextField
    @FXML
    private lateinit var nextButton: Button
    @FXML
    private lateinit var previousButton: Button
    @FXML
    private lateinit var searchTracker: Label

    private val searchingProperty = SimpleBooleanProperty(this, "searching", false)
    var searching: Boolean by searchingProperty

    val searchTermProperty = SimpleStringProperty(this, "searchTerm", "")
    var searchTerm: String by searchTermProperty

    val currentSearchIndexProperty = SimpleIntegerProperty(this, "currentSearchIndex", -1)
    var currentSearchIndex: Int by currentSearchIndexProperty

    val maxSearchIndexProperty = SimpleIntegerProperty(this, "maxSearchIndex", -1)
    var maxSearchIndex: Int by maxSearchIndexProperty

    /**
     * Called when the user attempts to start searching.
     *
     * Should return `true` if the search is started, or `false` if the
     * handler does not accept the input to start the search.
     */
    var onStartSearch: (FindPopupController) -> Boolean = { false }
    var onSearchNext: (FindPopupController) -> Unit = {}
    var onSearchPrevious: (FindPopupController) -> Unit = {}
    var onCloseRequested: (FindPopupController) -> Unit = {}

    @FXML
    fun initialize() {
        // Bind search term, ensuring trimming from user input
        searchTermProperty.bindBidirectional(textField.textProperty(), object : StringConverter<String>() {
            override fun toString(tfStr: String) = tfStr.trim()
            override fun fromString(stStr: String) = stStr
        })
        previousButton.disableProperty().bind(searchingProperty.not())
        nextButton.disableProperty().bind(searchingProperty.not())
        searchTracker.textProperty().bind(Bindings.createStringBinding({
            when {
                currentSearchIndex < 0 || maxSearchIndex < 0 || currentSearchIndex > maxSearchIndex -> ""
                else -> "$currentSearchIndex/$maxSearchIndex"
            }
        }, arrayOf(currentSearchIndexProperty, maxSearchIndexProperty)))

        // Search whenever text is typed
        searchTermProperty.addListener { _, old, new ->
            if (old != new) {
                textField.onAction.handle(ActionEvent(textField, textField))
            }
        }
    }

    @FXML
    fun startSearch() {
        searching = onStartSearch(this)
    }

    @FXML
    fun searchNext() = onSearchNext(this)

    @FXML
    fun searchPrevious() = onSearchPrevious(this)

    @FXML
    fun close() = onCloseRequested(this)

}