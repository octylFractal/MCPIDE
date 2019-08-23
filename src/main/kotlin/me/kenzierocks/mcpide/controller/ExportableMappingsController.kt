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

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.input.KeyCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.SrgMapping
import me.kenzierocks.mcpide.inject.View
import me.kenzierocks.mcpide.comms.InternalizeRenames
import me.kenzierocks.mcpide.comms.RemoveRenames
import me.kenzierocks.mcpide.comms.ViewComms
import me.kenzierocks.mcpide.util.confirmSimple
import org.fxmisc.wellbehaved.event.EventPattern.keyPressed
import org.fxmisc.wellbehaved.event.InputMap.consume
import org.fxmisc.wellbehaved.event.Nodes
import javax.inject.Inject

class ExportableMappingsController @Inject constructor(
    private val viewComms: ViewComms,
    @View
    private val viewScope: CoroutineScope
) {

    @FXML
    private lateinit var table: TableView<SrgMapping>
    @FXML
    private lateinit var colSrgName: TableColumn<SrgMapping, String>
    @FXML
    private lateinit var colNewName: TableColumn<SrgMapping, String>
    @FXML
    private lateinit var colDesc: TableColumn<SrgMapping, String>

    @FXML
    fun initialize() {
        Nodes.addInputMap(table, consume(keyPressed(KeyCode.DELETE)) { deleteSelected() })
        Nodes.addInputMap(table, consume(keyPressed(KeyCode.BACK_SPACE)) { deleteSelected() })
        table.selectionModel.selectionMode = SelectionMode.MULTIPLE
        colSrgName.cellValueFactory = PropertyValueFactory("srgName")
        colNewName.cellValueFactory = PropertyValueFactory("newName")
        colDesc.cellValueFactory = PropertyValueFactory("desc")
    }

    // use lazy since it won't be readable on <init>
    val items: ObservableList<SrgMapping> by lazy { table.items }

    private enum class Action(
        val actionTitle: String,
        val actionQuestion: String
    ) {
        DELETE("Name Deletion", "delete"),
        INTERNALIZE("Internalization", "internalize");

        // Use this semi-anti-pattern, removal dependent on kapt fixes:
        // https://youtrack.jetbrains.com/issue/KT-33052
        suspend fun ExportableMappingsController.perform(items: List<SrgMapping>) {
            return when (this@Action) {
                DELETE -> {
                    table.items.removeAll(items)
                    viewComms.modelChannel.send(RemoveRenames(
                        items.map { it.srgName }.toSet()
                    ))
                }
                INTERNALIZE -> {
                    table.items.removeAll(items)
                    viewComms.modelChannel.send(InternalizeRenames(
                        items.map { it.srgName }.toSet()
                    ))
                }
            }
        }
    }

    private fun Action.confirmAndPerform() {
        viewScope.launch {
            val items = table.selectionModel.selectedItems.toList()
            if (items.isNotEmpty() && confirmAction(items)) {
                perform(items)
            }
        }
    }

    private suspend fun Action.confirmAction(
        items: List<SrgMapping>
    ): Boolean {
        return confirmSimple(
            actionTitle, youWantTo = "$actionQuestion these names"
        ) {
            dialogPane.content = ListView(FXCollections.observableList(
                items.map { "${it.srgName}->${it.newName}" }
            ))
        }
    }

    @FXML
    fun deleteSelected() {
        Action.DELETE.confirmAndPerform()
    }

    @FXML
    fun internalizeSelected() {
        Action.INTERNALIZE.confirmAndPerform()
    }

}