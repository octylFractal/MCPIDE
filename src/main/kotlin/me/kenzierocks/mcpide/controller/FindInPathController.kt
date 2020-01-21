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
import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.control.Toggle
import javafx.scene.control.ToggleGroup
import javafx.stage.WindowEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.util.varNullable
import java.nio.file.Path
import javax.inject.Inject

class FindInPathController @Inject constructor() {

    @FXML
    lateinit var textField: TextField
    @FXML
    private lateinit var searchTracker: Label
    @FXML
    private lateinit var source: ToggleGroup
    @FXML
    private lateinit var sourceFromProject: Toggle
    @FXML
    private lateinit var sourceFromDir: Toggle
    @FXML
    private lateinit var sourceDirectory: TextField
    @FXML
    private lateinit var items: ListView<SearchResult>

    private val rootPathProperty = SimpleObjectProperty<Path>(this, "rootPath")
    var rootPath by rootPathProperty.varNullable

    private val searchDirectoryBinding by lazy {
        Bindings.createObjectBinding({
            rootPath?.let { root ->
                when (source.selectedToggle) {
                    sourceFromDir -> root.resolve(sourceDirectory.text)
                    sourceFromProject -> root
                    else -> null
                }
            }
        }, arrayOf(rootPathProperty, source.selectedToggleProperty(), sourceDirectory.textProperty()))
    }

    private val searchHelperProperty by lazy {
        Bindings.createObjectBinding({
            searchDirectoryBinding.get()?.let { searchDir ->
                object : AsyncPathSearchHelper(searchDir) {
                    override suspend fun cancel() {
                        textField.scene?.window?.let { window ->
                            window.onCloseRequest.handle(WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST))
                        }
                    }
                }
            }
        }, arrayOf(searchDirectoryBinding)).also { bind ->
            bind.addListener { _, old, new ->
                old?.searchTermProperty?.unbindBidirectional(textField.textProperty())
                if (new != null) {
                    // Bind search term
                    new.searchTermProperty.bindBidirectional(textField.textProperty())

                    searchTracker.textProperty().bind(new.searchTrackerTextBinding)

                    items.itemsProperty().bind(new.listProperty)
                }
            }
        }
    }

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
            searchHelperProperty.get()!!.onSearchStart()
        }
    }

}
