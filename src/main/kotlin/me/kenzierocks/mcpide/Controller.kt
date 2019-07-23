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

import com.beust.klaxon.JsonArray
import com.beust.klaxon.Parser
import com.google.common.collect.ImmutableList
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.binding.ObjectBinding
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.css.Styleable
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.TabPane
import javafx.scene.control.TextField
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeView
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCharacterCombination
import javafx.scene.input.KeyCombination
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.util.Callback
import me.kenzierocks.mcpide.fx.MappingList
import me.kenzierocks.mcpide.fx.MultiList
import me.kenzierocks.mcpide.fx.RenameListEntry
import me.kenzierocks.mcpide.fx.map
import me.kenzierocks.mcpide.pathext.div
import me.kenzierocks.mcpide.pathext.touch
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


private val idPrefixComputeFunction = lru<Class<*>, String>(10, {
    var upper = false
    val b = StringBuilder()
    for (char in it.simpleName) {
        if (char.isUpperCase()) {
            if (!upper && b.isNotEmpty()) {
                b.append('-')
            }
            upper = true
            b.append(char.toLowerCase())
        } else {
            upper = false
            b.append(char)
        }
    }
    return@lru b.toString()
})
private val unprefixedIdComputeFunction = lru<Pair<String, String>?, String?>(100, {
    val (id, prefix) = it ?: return@lru null
    return@lru id.replace(prefix + "-", "")
})
private val Styleable.idPrefix: String get() = idPrefixComputeFunction(javaClass)
private val Styleable.unprefixId: String? get() = unprefixedIdComputeFunction(id?.to(idPrefix))
private fun Styleable.unprefixIdEquals(unprefixedId: String): Boolean {
    return this.unprefixId == unprefixedId
}

class Controller {

    private val config = Config.GLOBAL
    private val openProjectKey = config observable "open_project"
    private val openProjectAsPath = openProjectKey.map { Paths.get(it).toRealPath() }

    @FXML
    private lateinit var menuBar: MenuBar
    @FXML
    private lateinit var quitMenuItem: MenuItem
    @FXML
    private lateinit var fileTree: TreeView<Path>
    @FXML
    private lateinit var textView: TabPane
    @FXML
    private lateinit var renameList: ListView<RenameListEntry>
    @FXML
    private lateinit var searchRenames: TextField
    @FXML
    private lateinit var recentExportsMenu: Menu
    @FXML
    private lateinit var recentExportsMenuItem: MenuItem
    private val recentExports: ObservableList<Path> = FXCollections.observableArrayList()
    private val idNumbers = (0..Integer.MAX_VALUE).iterator()
    private val recentExportsMenuItemList = MappingList(recentExports,
        { path ->
            val mi = MenuItem(openProjectAsPath.value.relativize(path.toAbsolutePath()).toString())
            mi.onAction = EventHandler { export(it) }
            mi.id = mi.text.filter(Char::isJavaIdentifierPart) + idNumbers.nextInt()
            mi
        })

    init {
        val META_PLUS_E = KeyCharacterCombination("E", KeyCombination.META_DOWN)
        recentExportsMenuItemList.addListener(ListChangeListener { change ->
            while (change.next()) {
                if (change.wasAdded()) {
                    if (change.from == 0) {
                        change.addedSubList[0].accelerator = META_PLUS_E
                        change.list.elementAtOrNull(change.to)?.accelerator = null
                    }
                }
            }
        })
        openProjectKey.addListener(InvalidationListener {
            recentExportsMenuItemList.invalidateCaches()
        })
    }

    private val about: String = """MCPIDE Version ${ManifestVersion.getProjectVersion()}
                                  |Copyright © 2016 Kenzie Togami""".trimMargin()
    private val defaultDirectory: String = System.getProperty("user.home")
    private val recentExportsFile = config.file.parent / "recentExports.json"
    private lateinit var core: Core
    private lateinit var initialDirectoryExport: ObjectBinding<ConfigKey?>

    @FXML
    fun initialize() {
        core = Core(this)
        initialDirectoryExport = core.configProperty.observable("initial_directory@export")
        val rl = getRenameList()
        rl.cellFactory = Callback {
            RLECell()
        }
        val ft = getFileTree()
        ft.cellFactory = Callback {
            PathCell()
        }

        searchRenames.textProperty().addListener { _, _, new ->
            core.filterRenames(new)
        }

        // Recent exports menu has three parts
        // The before (the label menu item, recentExportsMenuItem)
        val relBefore = FXCollections.unmodifiableObservableList(
            FXCollections.observableList(listOf(recentExportsMenuItem))
        )
        // The after (anything else after that)
        val recentExportsIndex = recentExportsMenu.items.indexOf(recentExportsMenuItem)
        val relAfter = FXCollections.observableList(
            ImmutableList.copyOf(
                recentExportsMenu.items.subList(recentExportsIndex + 1, recentExportsMenu.items.size)
            )
        )
        // And the middle (the dynamically mapped list) (declared above)

        // Combine into a single, auto-updating list
        val recentExportsMenuList = MultiList(relBefore, recentExportsMenuItemList, relAfter)
        Bindings.bindContent(recentExportsMenu.items, recentExportsMenuList)

        recentExportsFile.touch()
        recentExports += (Files.newInputStream(recentExportsFile).use {
            try {
                Parser.default().parse(it)
            } catch (e: RuntimeException) {
                // Pass: this is a Klaxon error
                RuntimeException("warning: unable to load recent export locations", e).printStackTrace()
                JsonArray<Any>()
            }
        } as JsonArray<*>).map { Paths.get(it.toString()) }
        recentExports.addListener(InvalidationListener {
            Files.write(recentExportsFile, JsonArray(recentExports.map(Any::toString)).toJsonString(true).toByteArray())
        })

        openProjectKey.value?.let { open ->
            core.loadProject(Paths.get(open))
        }

        if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
            rewriteMenusForMacOs()
        }
    }

    fun getFileTree(): TreeView<Path> = fileTree
    fun getTextView(): TabPane = textView
    fun getRenameList(): ListView<RenameListEntry> = renameList

    @FXML
    fun loadDirectory() {
        val fileSelect = DirectoryChooser()
        fileSelect.title = "Select a Project"
        fileSelect.initialDirectory = File(config["initial_directory@project"] ?: defaultDirectory)
        val selected: File = fileSelect.showDialog(MCPIDE.INSTANCE.stage) ?: return
        if (selected.parentFile != fileSelect.initialDirectory) {
            config["initial_directory@project"] = selected.parent
        }
        config["open_project"] = selected.absolutePath
        core.loadProject(selected.toPath())
    }

    @FXML
    fun export(event: ActionEvent) {
        val menuItem = event.source as? MenuItem
        if (menuItem == null) {
            System.err.println("Source was unexpectedly ${event.source?.javaClass}!")
            return
        }
        if (menuItem.unprefixIdEquals("to")) {
            val fileSelect = FileChooser()
            fileSelect.title = "Select Export Location"
            // Use project's config for export locations
            fileSelect.initialDirectory = File(initialDirectoryExport.value?.value ?: defaultDirectory)
            fileSelect.extensionFilters.addAll(
                FileChooser.ExtensionFilter("Text Files", "*.txt"),
                FileChooser.ExtensionFilter("All Files", "*.*")
            )
            val selected = (fileSelect.showSaveDialog(MCPIDE.INSTANCE.stage) ?: return).toPath()
            if (selected.parent.toFile() != fileSelect.initialDirectory) {
                initialDirectoryExport.value?.value = selected.parent.toString()
            }
            val open_project = openProjectAsPath.value
            recentExports.add(0, if (selected.startsWith(open_project)) {
                open_project.relativize(selected)
            } else {
                selected.toAbsolutePath()
            })
            exportTo(selected)
        } else {
            val index = recentExportsMenuItemList.indexOf(menuItem)
            if (index == -1) {
                System.err.println("Error: $menuItem not found in $recentExportsMenuItemList")
            }
            val file = recentExports[index]
            exportTo(file)
            recentExports.add(0, recentExports.removeAt(index))
        }
    }

    private fun exportTo(file: Path) {
        file.toFile().bufferedWriter().use { w ->
            val pw = PrintWriter(w)
            core.getReplacements().forEachReplacementType { k, v, replacementType ->
                if (replacementType == ReplacementType.CUSTOM) {
                    return@forEachReplacementType
                }
                val pfx = replacementType.namePrefixUnderscore
                val fixedK =
                    if (!k.startsWith(pfx)) pfx + k
                    else k
                pw.println("!${replacementType.botCommand} $fixedK $v")
            }
            pw.flush()
        }
        System.err.println("Wrote exports to ${file.toAbsolutePath()}")
    }

    @FXML
    fun addRename() {
        core.addRenameListEntry()
    }

    @FXML
    fun quit() {
        Platform.exit()
    }

    @FXML
    fun about() {
        val about = Dialog<Void>()
        about.title = "About " + MCPIDE.TITLE
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

class RLECell : ListCell<RenameListEntry>() {
    override fun updateItem(item: RenameListEntry?, empty: Boolean) {
        super.updateItem(item, empty)

        graphic = if (empty || item == null) null else item.root
    }
}

class PathCell : TreeCell<Path>() {
    companion object {
        private fun image(path: String): Image {
            return Image(MCPIDE.getResource(path).toString(), 16.0, 16.0, true, true)
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