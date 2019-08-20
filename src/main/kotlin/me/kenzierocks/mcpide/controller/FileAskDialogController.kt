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

import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.control.TextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.inject.View
import java.nio.file.Path
import javax.inject.Inject

class FileAskDialogController @Inject constructor(
    @View
    private val viewScope: CoroutineScope
) {

    @FXML
    private lateinit var fileTypeLabel: Label
    @FXML
    private lateinit var fileText: TextField
    @FXML
    private lateinit var selectMenu: MenuButton
    private val stage get() = fileText.scene.window

    var fileType: String
        get() = fileTypeLabel.text.substringBefore(':')
        set(value) {
            fileTypeLabel.text = "$value:"
        }

    fun addFileSources(sources: Iterable<FileSource>) {
        val menu = selectMenu
        sources.forEach { source ->
            val item = MenuItem(source.description)
            item.setOnAction {
                viewScope.launch {
                    source.retrieveFile(stage)?.let { fileText.text = it.toAbsolutePath().toString() }
                }
            }
            menu.items.add(item)
        }
    }

    val path: Path? get() = fileText.text.takeUnless { it.isEmpty() }?.let { Path.of(it) }

}