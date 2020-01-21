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

import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.util.varNullable
import javax.inject.Inject

class FindPopupController @Inject constructor() {

    @FXML
    lateinit var textField: TextField
    @FXML
    private lateinit var nextButton: Button
    @FXML
    private lateinit var previousButton: Button
    @FXML
    private lateinit var searchTracker: Label

    private val searchHelperProperty = object : SimpleObjectProperty<SearchHelper<*>>(this, "searchHelper") {
        override fun invalidated() {
            val sh = get()
            // Bind search term
            sh.searchTermProperty.bindBidirectional(textField.textProperty())
            val notSearching = sh.searchingBinding.not()
            previousButton.disableProperty().bind(notSearching)
            nextButton.disableProperty().bind(notSearching)

            searchTracker.textProperty().bind(sh.searchTrackerTextBinding)
        }
    }
    var searchHelper: SearchHelper<*>? by searchHelperProperty.varNullable

    @FXML
    fun initialize() {
        // Search whenever text is typed
        textField.textProperty().addListener { _, old, new ->
            if (old != new) {
                textField.onAction.handle(ActionEvent(textField, textField))
            }
        }
    }

    @FXML
    fun startSearch() {
        GlobalScope.launch(Dispatchers.JavaFx) {
            searchHelper!!.onSearchStart()
        }
    }

    @FXML
    fun searchNext() =
        GlobalScope.launch(Dispatchers.JavaFx) { searchHelper!!.onSearchNext() }

    @FXML
    fun searchPrevious() =
        GlobalScope.launch(Dispatchers.JavaFx) { searchHelper!!.onSearchPrevious() }

    @FXML
    fun close() =
        GlobalScope.launch(Dispatchers.JavaFx) { searchHelper!!.onSearchCanceled() }

}