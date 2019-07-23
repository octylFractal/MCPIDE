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
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.google.common.collect.AbstractIterator
import com.google.common.collect.ImmutableList
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.WeakChangeListener
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tab
import javafx.scene.control.TreeItem
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import me.kenzierocks.mcpide.fx.FXMLFactory
import me.kenzierocks.mcpide.fx.MappingList
import me.kenzierocks.mcpide.fx.RenameListEntry
import me.kenzierocks.mcpide.fx.RenameTooltip
import me.kenzierocks.mcpide.fx.map
import me.kenzierocks.mcpide.pathext.div
import me.kenzierocks.mcpide.pathext.name
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque
import java.util.Deque
import java.util.HashMap
import java.util.LinkedList
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.collections.set

class Core(val ctrl: Controller) {

    companion object {
        private val DATA_FOLDER = ".mcpide"
        private val PARSER = Parser.default()

        private val REPLACED_HIGHLIGHT_COLOR = Color.INDIGO
        private val FIELD_HIGHLIGHT_COLOR = Color.rgb(0x8E, 0x1E, 0x69)
        private val METHOD_HIGHLIGHT_COLOR = Color.rgb(0xFF, 0x53, 0x70)
        private val PARAMETER_HIGHLIGHT_COLOR = Color.rgb(0x08, 0x08, 0xC2)

        private val IDENTIFIER_PATTERN = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*")

        private val KEY_TAB_PATH = Core::class.qualifiedName!! + ".tabPath"

        private lateinit var CURRENT_CORE: Core

        fun getCurrentCore() = CURRENT_CORE

        private val TreeItem<Path>.pathName: String get() = value.name

        // I don't think this needs to be non-recursive
        // If it ever overflows, it can be changed.
        private fun serializeRoot(node: TreeItem<Path>, includeName: Boolean = true): JsonObject {
            val array: MutableMap<String, Any?> = mutableMapOf("expanded".to(node.isExpanded))
            if (includeName) {
                array.put("path_name", node.pathName)
            }
            val obj = JsonObject(array)
            if (node.isExpanded) {
                obj["children"] = JsonObject(node.children.map {
                    it.pathName.to(serializeRoot(it, includeName = false))
                }.toMap())
            }
            return obj
        }

        /**
         * Relativize loc to root and run the path through the JSON.
         * Returns the Object at that location.
         */
        fun JsonObject.pathify(root: Path, loc: Path): JsonObject? {
            val rel = root.relativize(loc)
            var obj: JsonObject? = this
            rel.map(Path::name).filter(String::isNotBlank).forEach { s ->
                obj = obj?.obj("children")?.obj(s)
                // child objects don't have assoc path_name
                obj?.run { this["path_name"] = s }
            }
            return obj
        }

    }

    private val projectPath: ObjectProperty<Path?> = SimpleObjectProperty(this, "projectPath")

    private fun projectPathProperty(name: String, str: String): ReadOnlyObjectProperty<Path?> {
        val wrapper = ReadOnlyObjectWrapper<Path?>(this, name)
        wrapper.bind(projectPath.map { it?.resolve(str) })
        return wrapper.readOnlyProperty
    }

    // potential file values
    private val configProperties = projectPathProperty("configProperties", "config.properties")
    private val replacementProperties = projectPathProperty("replacementProperties", "replacements.properties")
    private val fileTreeJson = projectPathProperty("fileTreeJson", "filetree.json.gz")
    private val openFilesJson = projectPathProperty("openFilesJson", "openfiles.json.gz")

    private val openFiles: ObservableList<Path> =
        FXCollections.observableArrayList()
    private val tabs = MappingList(openFiles,
        mappingFunc = {
            val tab = Tab(it.name)
            tab.properties[KEY_TAB_PATH] = it
            tab.content = ScrollPane(TextFlow())
            tab.onCloseRequest = EventHandler { _ ->
                openFiles.remove(it)
            }
            tab
        }, removeTest = { tab ->
        // remove the tab manually, because it may already be gone
        // and that causes exceptions
        tab.tabPane?.tabs?.remove(tab)
        false
    })

    val configProperty = configProperties.configFileProperty(this, "config")
    private val replacementConfigProperty = replacementProperties.configFileProperty(this, "replacementConfig")

    val renameEntries: ObservableList<RenameListEntry> =
        FXCollections.observableArrayList()
    private val filteredRenameEntries = FilteredList(renameEntries)
    private var treeModListener: ChangeListener<Number>? = null
    val selRenames: MutableMap<RenameListEntry, Pair<String, String>> = HashMap()


    init {
        CURRENT_CORE = this
        // on invalid write json.gzip
        openFiles.addListener(InvalidationListener {
            if (openFilesJson.value == null) {
                // project is closed
                return@InvalidationListener
            }
            Files.newOutputStream(openFilesJson.value).use {
                GZIPOutputStream(it).use {
                    it.write(JsonArray(openFiles).toJsonString(true).toByteArray())
                }
            }
        })
        Bindings.bindContent(ctrl.getTextView().tabs, tabs)
        ctrl.getTextView().selectionModel.selectedItemProperty().addListener(InvalidationListener {
            if (projectPath.value == null) {
                // project is closed
                return@InvalidationListener
            }
            applyRenames()
        })
    }

    fun unloadProject() {
        renameEntries.clear()
        selRenames.clear()
        projectPath.value = null

        // clear tabs after closing project
        openFiles.clear()
    }

    fun loadProject(base: Path) {
        projectPath.value = base / DATA_FOLDER
        try {
            Files.createDirectory(projectPath.value)
        } catch (e: FileAlreadyExistsException) {
        }
        ctrl.getRenameList().items = filteredRenameEntries
        loadReplacements()
        loadFileTree(base)
        val ft = ctrl.getFileTree()
        treeModListener = ChangeListener { _, _, _ ->
            val data = serializeRoot(ft.root)
            GZIPOutputStream(Files.newOutputStream(fileTreeJson.value)).use {
                it.write(data.toJsonString().toByteArray(StandardCharsets.UTF_8))
            }
        }
        ft.expandedItemCountProperty().addListener(WeakChangeListener(treeModListener))
    }

    fun loadReplacements() {
        getReplacements().forEachReplacementType { k, v, replacementType ->
            addRenameListEntry(k.to(v), replacementType)
        }
    }

    fun getReplacements() = replacementConfigProperty.value!!

    fun loadFileTree(root: Path) {
        val jsonData =
            if (Files.exists(fileTreeJson.value)) {
                GZIPInputStream(Files.newInputStream(fileTreeJson.value)).use {
                    PARSER.parse(it)
                } as JsonObject
            } else {
                null
            }
        val treeStack: Deque<TreeItem<Path>> = LinkedList()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val next = TreeItem(dir)
                val json = jsonData?.pathify(root, dir)
                next.isExpanded = json?.boolean("expanded") ?: false
                if (treeStack.isNotEmpty()) {
                    treeStack.peek().children.add(next)
                }
                treeStack.push(next)
                return super.preVisitDirectory(dir, attrs)
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val item = TreeItem(file)
                val json = jsonData?.pathify(root, file)
                item.isExpanded = json?.boolean("expanded") ?: false
                treeStack.peek().children.add(item)
                return super.visitFile(file, attrs)
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                // save the root TreeItem on the stack
                if (treeStack.size > 1) {
                    treeStack.pop()
                }
                return super.postVisitDirectory(dir, exc)
            }
        })
        val ft = ctrl.getFileTree()
        ft.root = treeStack.pop()
        ft.editingItemProperty()
            .addListener { _, _, new ->
                if (new != null && Files.isRegularFile(new.value)) {
                    insertTab(new.value)
                }
                applyRenames()
            }
    }

    fun addRenameListEntry(defNames: Pair<String, String>? = null,
                           replacementType: ReplacementType = ReplacementType.CUSTOM): RenameListEntry {
        val rle = FXMLFactory.createRenameListEntry()
        rle.replacementType = replacementType
        renameEntries.add(rle)
        rle.addHandler {
            if (rle.isApplicable()) {
                val pair = rle.getLastValue() ?: Pair("", "")
                val (old, new) = pair
                if (old.isBlank() || new.isBlank()) {
                    // remove them
                    delete(rle)
                    return@addHandler
                }
                replacementsDel(selRenames[rle]?.first)

                getReplacements()[pair.first] = pair.second + REPLACEMENT_TYPE_SEPARATOR + rle.replacementType.name
                selRenames.put(rle, pair)
                applyRenames()
            }
        }
        rle.delete.onAction = EventHandler {
            val dialog = Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete this rename?")
            dialog.showAndWait().filter {
                it == ButtonType.OK
            }.ifPresent {
                delete(rle, uiRemove = true)
            }
        }
        if (defNames != null) {
            // Fake a submission with the new names
            rle.oldName.text = defNames.first
            rle.newName.text = defNames.second
            rle.submit.fire()
        }
        return rle
    }

    private fun replacementsDel(key: String?) {
        if (key == null) {
            return
        }
        getReplacements().remove(key)
    }

    private fun delete(rle: RenameListEntry, uiRemove: Boolean = false) {
        val oldValues = selRenames.remove(rle)
        replacementsDel(oldValues?.first)
        if (uiRemove) {
            renameEntries.remove(rle)
        }
        applyRenames()
    }

    fun insertTab(path: Path): Tab {
        openFiles.add(path)
        return tabs.last()
    }

    private fun getCurrentTab(): Tab? = ctrl.getTextView().selectionModel.selectedItem
    private fun getCurrentTextFlow(): TextFlow? = (getCurrentTab()?.content as? ScrollPane)?.content as? TextFlow
    private fun getCurrentPath(): Path? = getCurrentTab()?.properties?.get(KEY_TAB_PATH) as? Path

    private fun createText(s: String): Text {
        val t = Text(s)
        t.font = Font.font("monospaced", 16.0)
        return t
    }

    fun applyRenames() {
        if (getCurrentTab() == null) {
            // no rename to apply
            return
        }
        val textFlow = getCurrentTextFlow()!!
        val path = getCurrentPath()!!
        textFlow.children.clear()
        val renameMap = mapOf(*selRenames.values.toTypedArray())
        val textObj = tokenizeText(Files.newBufferedReader(path).use { it.readText() })

        val texts = ImmutableList.builder<Text>()
        val accText = StringBuilder()
        textObj.forEach { text ->
            var resultText = text
            var attachHover = false
            var textFill: Paint? = null
            var replacementType: ReplacementType? = null
            if (text in renameMap) {
                resultText = renameMap[text]!!
                textFill = REPLACED_HIGHLIGHT_COLOR
            } else if (text.startsWith(ReplacementType.FIELD.namePrefixUnderscore)) {
                textFill = FIELD_HIGHLIGHT_COLOR
                attachHover = true
                replacementType = ReplacementType.FIELD
            } else if (text.startsWith(ReplacementType.METHOD.namePrefixUnderscore)) {
                textFill = METHOD_HIGHLIGHT_COLOR
                attachHover = true
                replacementType = ReplacementType.METHOD
            } else if (text.startsWith(ReplacementType.PARAMETER.namePrefixUnderscore)) {
                textFill = PARAMETER_HIGHLIGHT_COLOR
                attachHover = true
                replacementType = ReplacementType.PARAMETER
            }
            if (textFill != null) {
                val textWrap = createText(resultText)
                textWrap.fill = textFill
                texts.add(createText(accText.toString()), textWrap)
                accText.setLength(0)

                if (attachHover) {
                    val rt = replacementType!!
                    RenameTooltip.install(textWrap, rt)
                }
            } else {
                accText.append(resultText)
            }
        }
        if (accText.isNotEmpty()) {
            texts.add(createText(accText.toString()))
        }
        textFlow.children.addAll(texts.build())
    }

    private fun tokenizeText(text: String): Iterator<String> {
        var indexAfterLastMatchEnd = 0
        val matcher = IDENTIFIER_PATTERN.matcher(text)
        return object : AbstractIterator<String>() {
            private val q = ArrayDeque<String>()

            override fun computeNext(): String? {
                if (q.isNotEmpty()) return q.removeFirst()
                if (matcher.find()) {
                    q.addLast(text.substring(indexAfterLastMatchEnd, matcher.start()))
                    q.addLast(matcher.group(0))

                    indexAfterLastMatchEnd = matcher.end()
                } else if (indexAfterLastMatchEnd < text.length) {
                    q.addLast(text.substring(indexAfterLastMatchEnd))
                    indexAfterLastMatchEnd = text.length
                }
                if (q.isEmpty()) return endOfData()
                return q.removeFirst()
            }
        }
    }

    fun filterRenames(filter: String?) {
        if (filter == null || filter.isBlank()) {
            filteredRenameEntries.predicate = null
        } else {
            filteredRenameEntries.predicate = Predicate { rle ->
                rle.oldName.text.contains(filter) || rle.newName.text.contains(filter)
            }
        }
    }

}