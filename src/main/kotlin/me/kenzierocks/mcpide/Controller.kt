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

package me.kenzierocks.mcpide

import com.github.javaparser.TokenRange
import com.google.common.base.Throwables
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.stage.DirectoryChooser
import javafx.util.Callback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kenzierocks.mcpide.comms.ExportMappings
import me.kenzierocks.mcpide.comms.LoadProject
import me.kenzierocks.mcpide.comms.ModelMessage
import me.kenzierocks.mcpide.comms.OpenInFileTree
import me.kenzierocks.mcpide.comms.UpdateMappings
import me.kenzierocks.mcpide.comms.ViewComms
import me.kenzierocks.mcpide.comms.ViewMessage
import me.kenzierocks.mcpide.comms.apply
import me.kenzierocks.mcpide.fx.JavaEditorArea
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class Controller(
    private val app: MCPIDE,
    private val viewComms: ViewComms,
    private val workerScope: CoroutineScope,
    private val viewScope: CoroutineScope
) {

    companion object {
        private val TREE_ITEM_ALPHABETICAL = Comparator.comparing<TreeItem<Path>, Path> {
            it.value
        }
    }

    private val logger = KotlinLogging.logger { }

    @FXML
    private lateinit var menuBar: MenuBar
    @FXML
    private lateinit var quitMenuItem: MenuItem
    @FXML
    private lateinit var fileTree: TreeView<Path>
    @FXML
    private lateinit var textView: TabPane

    private val about: String = """MCPIDE Version ${ManifestVersion.getProjectVersion()}
                                  |Copyright © 2016 Kenzie Togami""".trimMargin()
    private var defaultDirectory: String = System.getProperty("user.home")
    private val mappings = mutableMapOf<String, SrgMapping>()

    @FXML
    fun initialize() {
        fileTree.isEditable = false
        fileTree.cellFactory = Callback {
            PathCell().also {
                it.setOnMouseClicked { e ->
                    if (e.clickCount == 2 && it.item != null && !Files.isDirectory(it.item)) {
                        openFile(it.item)
                    }
                }
            }
        }

        if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
            rewriteMenusForMacOs()
        }
    }

    fun handleMessage(viewMessage: ViewMessage) {
        exhaustive(when (viewMessage) {
            is OpenInFileTree -> expandDirectory(viewMessage.directory)
            is UpdateMappings -> mappings.apply(viewMessage)
        })
    }

    private fun expandDirectory(directory: Path) {
        var currentParentNode = TreeItem(directory)
        fileTree.root = currentParentNode

        Files.walkFileTree(directory, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != directory) {
                    val newNode = TreeItem(dir)
                    currentParentNode.children.add(newNode)
                    currentParentNode = newNode
                }
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                currentParentNode.children.sortWith(TREE_ITEM_ALPHABETICAL)
                if (dir != directory) {
                    currentParentNode = currentParentNode.parent
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                currentParentNode.children.add(TreeItem(file))
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                logger.warn(exc) { "Failed to visit file $file" }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun openFile(path: Path) {
        // read in worker thread for UI responsiveness :)
        val contentDeferred = workerScope.async {
            readText(path)
        }
        viewScope.launch {
            val content = try {
                contentDeferred.await()
            } catch (e: Exception) {
                "Error loading content:\n" + Throwables.getStackTraceAsString(e)
            }
            val editor = JavaEditorArea()
            editor.text = content
            val tab = Tab("${path.fileName}", editor)
            textView.tabs.add(tab)
            textView.selectionModel.select(tab)
            val highlighting = highlight(content)
            highlighting.consumeEach {
                editor.styleTokenRange(it.style, it.tokenRange)
            }
        }
    }

    private data class Highlight(val style: String, val tokenRange: TokenRange)

    private fun highlight(text: String): ReceiveChannel<Highlight> {
        return workerScope.produce {

        }
    }

    private suspend fun readText(path: Path): String {
        return withContext(Dispatchers.IO) {
            try {
                Files.readString(path, StandardCharsets.UTF_8)
            } catch (e: CharacterCodingException) {
//                "Error: Binary files not supported."
                throw e
            }
        }
    }

    private fun sendMessage(modelMessage: ModelMessage) {
        viewComms.modelChannel.sendBlocking(modelMessage)
    }

    @FXML
    fun loadDirectory() {
        val fileSelect = DirectoryChooser()
        fileSelect.title = "Select a Project"
        fileSelect.initialDirectory = File(defaultDirectory)
        val selected: File = fileSelect.showDialog(app.stage) ?: return
        if (selected.parentFile != fileSelect.initialDirectory) {
            defaultDirectory = selected.parent
        }
        sendMessage(LoadProject(selected.toPath()))
    }

    @FXML
    fun export() {
        sendMessage(ExportMappings)
    }

    @FXML
    fun quit() {
        Platform.exit()
    }

    @FXML
    fun about() {
        val about = Dialog<Void>()
        about.title = "About " + app.stage.title
        about.dialogPane.buttonTypes.add(ButtonType.CLOSE)
        about.dialogPane.contentText = this.about
        about.show()
    }

    private fun rewriteMenusForMacOs() {
        // macOS gets funky menus!
        menuBar.isUseSystemMenuBar = true
        quitMenuItem.isVisible = false
    }
}

class PathCell : TreeCell<Path>() {
    companion object {
        private fun image(path: String): Image {
            return Image(PathCell::class.java.getResource(path).toString(), 16.0, 16.0, true, true)
        }

        //language=file-reference
        private val IMAGE_FILE = image("glyphicons/png/glyphicons-37-file.png")
        //language=file-reference
        private val IMAGE_FOLDER = image("glyphicons/png/glyphicons-145-folder-open.png")
    }

    override fun updateItem(item: Path?, empty: Boolean) {
        super.updateItem(item, empty)
        if (empty || item == null) {
            text = null
            graphic = null
        } else {
            text = item.fileName.toString()
            graphic = ImageView(
                if (Files.isDirectory(item)) IMAGE_FOLDER else IMAGE_FILE
            )
        }
    }
}