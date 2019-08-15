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

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.property.ReadOnlyProperty
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import javafx.util.Callback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.FxmlFiles
import me.kenzierocks.mcpide.MCPIDE
import me.kenzierocks.mcpide.ManifestVersion
import me.kenzierocks.mcpide.Model
import me.kenzierocks.mcpide.Resources
import me.kenzierocks.mcpide.View
import me.kenzierocks.mcpide.comms.AskDecompileSetup
import me.kenzierocks.mcpide.comms.AskInitialMappings
import me.kenzierocks.mcpide.comms.DecompileMinecraft
import me.kenzierocks.mcpide.comms.ExportMappings
import me.kenzierocks.mcpide.comms.LoadProject
import me.kenzierocks.mcpide.comms.ModelMessage
import me.kenzierocks.mcpide.comms.OpenContent
import me.kenzierocks.mcpide.comms.OpenFile
import me.kenzierocks.mcpide.comms.OpenInFileTree
import me.kenzierocks.mcpide.comms.SetInitialMappings
import me.kenzierocks.mcpide.comms.StatusUpdate
import me.kenzierocks.mcpide.comms.ViewComms
import me.kenzierocks.mcpide.comms.ViewMessage
import me.kenzierocks.mcpide.exhaustive
import me.kenzierocks.mcpide.fx.JavaEditorArea
import me.kenzierocks.mcpide.fx.JavaEditorAreaCreator
import me.kenzierocks.mcpide.resolver.MavenAccess
import me.kenzierocks.mcpide.util.setPrefSizeFromContent
import me.kenzierocks.mcpide.util.showAndSuspend
import mu.KotlinLogging
import org.fxmisc.flowless.VirtualizedScrollPane
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject

class MainController @Inject constructor(
    private val app: MCPIDE,
    private val xmlMapper: XmlMapper,
    private val mavenAccess: MavenAccess,
    private val viewComms: ViewComms,
    @Model
    private val workerScope: CoroutineScope,
    @View
    private val viewScope: CoroutineScope,
    private val fxmlFiles: FxmlFiles,
    private val resources: Resources,
    private val javaEditorAreaCreator: JavaEditorAreaCreator
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
    @FXML
    private lateinit var statusLabel: Label

    private val about: String = """MCPIDE Version ${ManifestVersion.getProjectVersion()}
                                  |Copyright © 2019 Kenzie Togami""".trimMargin()
    private var defaultDirectory: Path = Path.of(System.getProperty("user.home"))

    fun startEventLoop() {
        viewScope.launch {
            sendMessage(LoadProject(Paths.get("/home/octy/Documents/mcp_reversing")))
            sendMessage(OpenFile(Paths.get("./logs/Test.java")))
            while (!viewComms.viewChannel.isClosedForReceive) {
                val msg = viewComms.viewChannel.receive()
                handleMessage(msg)
            }
        }
    }

    @FXML
    fun initialize() {
        fileTree.isEditable = false
        fileTree.cellFactory = Callback {
            PathCell(
                resources.fileIcon,
                resources.folderIcon,
                resources.folderOpenIcon
            ).also {
                it.setOnMouseClicked { e ->
                    if (e.clickCount == 2 && isValidPath(it.item)) {
                        sendMessage(OpenFile(it.item))
                    }
                }
            }
        }

        if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
            rewriteMenusForMacOs()
        }
    }

    private fun isValidPath(path: Path?): Boolean {
        return path != null && Files.exists(path) && !Files.isDirectory(path)
    }

    private suspend fun handleMessage(viewMessage: ViewMessage) {
        exhaustive(when (viewMessage) {
            is OpenInFileTree -> {
                val dirTree = workerScope.async { expandDirectory(viewMessage.directory) }
                fileTree.root = TreeItem(Paths.get("Loading project files..."))
                fileTree.root = dirTree.await()
            }
            is OpenContent -> openFile(viewMessage.source, viewMessage.content)
            is AskDecompileSetup -> askDecompileSetup()
            is AskInitialMappings -> askInitialMappings()
            is StatusUpdate -> updateStatus(viewMessage)
        })
    }

    private fun expandDirectory(directory: Path): TreeItem<Path> {
        var currentParentNode = TreeItem(directory)

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

        return currentParentNode
    }

    private suspend fun openFile(path: Path, content: String) {
        val title = path.fileName.toString()
        val existing = textView.tabs.firstOrNull { it.text == title }
        if (existing != null && openInExisting(existing, content)) {
            return
        }
        val editor = javaEditorAreaCreator.create(path)
        editor.replaceText("Loading...")
        editor.updateText(content)
        val tab = Tab(title, VirtualizedScrollPane(editor,
            ScrollPane.ScrollBarPolicy.ALWAYS, ScrollPane.ScrollBarPolicy.ALWAYS))
        textView.tabs.add(tab)
        textView.selectionModel.select(tab)
    }

    private suspend fun openInExisting(existing: Tab, content: String) : Boolean {
        val editor = existing.editorArea ?: return false
        editor.updateText(content)
        textView.selectionModel.select(existing)
        return true
    }

    private fun updateStatus(msg: StatusUpdate) {
        // for now, just overwrite
        statusLabel.text = msg.status
            .takeIf { it.isNotBlank() }?.let { status -> "${msg.category}: $status" } ?: ""
    }

    private fun sendMessage(modelMessage: ModelMessage) {
        viewComms.modelChannel.sendBlocking(modelMessage)
    }

    private suspend fun askDecompileSetup() {
        val file = openFileAskDialog(
            "MCP Config",
            "Decompile Setup",
            listOf(
                mavenFileSource(
                    "Forge & Mojang Maven",
                    "de.oceanlabs.mcp", "mcp_config",
                    dialogTitle = "MCP Config Release Selection",
                    header = "Choose an MCP Config release"
                ),
                LocalFileSource(
                    "Choose an MCP Config ZIP",
                    setOf(ExtensionFilter("ZIP Files", setOf("*.zip")))
                )
            )
        ) ?: return
        sendMessage(DecompileMinecraft(file))
    }

    private suspend fun askInitialMappings() {
        val file = openFileAskDialog(
            "MCP Names",
            "Initial Mappings",
            listOf(
                mavenFileSource(
                    "Forge & Mojang Maven",
                    "de.oceanlabs.mcp", "mcp_snapshot",
                    dialogTitle = "MCP Names Release Selection",
                    header = "Choose an MCP Names release"
                ),
                LocalFileSource(
                    "Choose an MCP Names ZIP",
                    setOf(ExtensionFilter("ZIP Files", setOf("*.zip")))
                )
            )
        ) ?: return
        sendMessage(SetInitialMappings(file))
    }

    private fun mavenFileSource(
        description: String, group: String, name: String,
        dialogTitle: String, header: String
    ): FileSource {
        return MavenSource(
            description, group, name, dialogTitle, header, xmlMapper, mavenAccess, workerScope
        )
    }

    /**
     * Common code for file ask dialogs.
     */
    private suspend fun openFileAskDialog(
        type: String,
        title: String,
        fileSources: Iterable<FileSource>
    ): Path? {
        val dialog = Alert(Alert.AlertType.NONE)
        dialog.initOwner(app.stage)
        dialog.initModality(Modality.APPLICATION_MODAL)
        dialog.isResizable = true
        dialog.title = title
        dialog.buttonTypes.addAll(ButtonType.CANCEL, ButtonType.FINISH)
        val (parent, controller) = fxmlFiles.fileAskDialog()
        controller.addFileSources(fileSources)
        controller.fileType = type
        dialog.dialogPane.content = parent
        dialog.dialogPane.setPrefSizeFromContent()
        while (controller.path == null) {
            when (dialog.showAndSuspend()) {
                null, ButtonType.CANCEL -> return null
            }
        }
        return controller.path
    }

    @FXML
    fun loadDirectory() {
        val fileSelect = DirectoryChooser()
        fileSelect.title = "Select a Project"
        fileSelect.initialDirectory = defaultDirectory.toFile()
        val selected: File = fileSelect.showDialog(app.stage) ?: return
        if (selected != fileSelect.initialDirectory) {
            defaultDirectory = selected.toPath()
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

    @FXML
    fun startRename() {
        val currentArea = (textView.selectionModel.selectedItem?.editorArea)
            ?: return
        viewScope.launch {
            currentArea.startRename()
        }
    }

    private fun rewriteMenusForMacOs() {
        // macOS gets funky menus!
        menuBar.isUseSystemMenuBar = true
        quitMenuItem.isVisible = false
    }

    @Suppress("UsePropertyAccessSyntax")
    private val Tab.editorArea: JavaEditorArea?
            get() = (content as? VirtualizedScrollPane<*>)?.getContent() as? JavaEditorArea
}

class PathCell(
    private val imageFile: Image,
    private val imageFolder: Image,
    private val imageFolderOpen: Image
) : TreeCell<Path>() {

    init {
        val updateListener = InvalidationListener {
            updateOpen(((it as ReadOnlyProperty<*>).bean as TreeItem<*>).isExpanded)
        }
        treeItemProperty().addListener { _, old, new ->
            old?.expandedProperty()?.removeListener(updateListener)
            new?.expandedProperty()?.let { prop ->
                updateListener.invalidated(prop)
                prop.addListener(updateListener)
            }
        }
    }

    private fun updateOpen(open: Boolean) {
        val g = ((graphic as? ImageView) ?: return)
        if (open && g.image === imageFolder) {
            g.image = imageFolderOpen
        } else if (!open && g.image === imageFolderOpen) {
            g.image = imageFolder
        }
    }

    override fun updateItem(item: Path?, empty: Boolean) {
        super.updateItem(item, empty)
        if (empty || item == null) {
            text = null
            graphic = null
        } else {
            text = item.fileName?.toString() ?: fsRootName(item)
            val imageView = ImageView(when {
                Files.isDirectory(item) -> imageFolder
                else -> imageFile
            })
            imageView.fitHeight = 16.0
            imageView.fitWidth = 16.0
            graphic = imageView
            treeItem?.let { updateOpen(it.isExpanded) }
        }
    }

    private fun fsRootName(path: Path): String {
        return when (path.fileSystem.provider().scheme) {
            // jar:file://...!/
            "zip", "jar" -> path.toUri().rawSchemeSpecificPart
                .removePrefix("file://").removeSuffix("!/")
            else -> "/"
        }
    }
}