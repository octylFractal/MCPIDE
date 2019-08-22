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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.comms.GetMinecraftJarRoot
import me.kenzierocks.mcpide.comms.PublishComms
import me.kenzierocks.mcpide.comms.Rename
import me.kenzierocks.mcpide.comms.RetrieveMappings
import me.kenzierocks.mcpide.comms.StatusUpdate
import me.kenzierocks.mcpide.comms.sendForResponse
import me.kenzierocks.mcpide.inject.ProjectScope
import me.kenzierocks.mcpide.util.confirmSimple
import me.kenzierocks.mcpide.util.openErrorDialog
import me.kenzierocks.mcpide.util.suspendUntilEqual
import mu.KotlinLogging
import net.octyl.aptcreator.GenerateCreator
import net.octyl.aptcreator.Provided
import org.fxmisc.richtext.LineNumberFactory
import org.fxmisc.richtext.model.ReadOnlyStyledDocument
import org.fxmisc.richtext.model.SimpleEditableStyledDocument
import org.fxmisc.richtext.model.StyleSpansBuilder
import java.nio.file.Path

@[GenerateCreator GenerateCreator.CopyAnnotations]
@ProjectScope
class JavaEditorArea(
    var path: Path,
    @Provided
    private val publishComms: PublishComms,
    @Provided
    private val astSpanMarkerCreator: AstSpanMarkerCreator
) : MappingTextArea() {
    private val logger = KotlinLogging.logger { }
    private val updatesChannel = Channel<String>(Channel.BUFFERED)

    init {
        isEditable = false
        paragraphGraphicFactory = LineNumberFactory.get(this)
        val highlightFlow = updatesChannel.consumeAsFlow()
            // If new edits while highlighting, toss out highlight result
            .mapLatest {
                try {
                    computeHighlighting(it)
                } catch (e: Exception) {
                    logger.warn(e) { "Highlighting error" }
                    e.openErrorDialog(
                        title = "Highlighting Error",
                        header = "An error occurred while computing highlighting"
                    )
                    null
                }
            }
            .filterNotNull()
            .flowOn(Dispatchers.Default + CoroutineName("Highlighting"))
        CoroutineScope(Dispatchers.JavaFx + CoroutineName("HighlightApplication")).launch {
            highlightFlow.collect { highlighting -> replace(highlighting) }
        }
    }

    private suspend fun computeHighlighting(text: String): JeaDoc {
        publishComms.viewChannel.send(StatusUpdate("Highlighting", "In Progress..."))
        try {
            val mappings = publishComms.modelChannel.sendForResponse(RetrieveMappings)
            val jarRoot = publishComms.modelChannel.sendForResponse(GetMinecraftJarRoot)

            return coroutineScope {
                val styles = remap(text, mappings)

                // Create new document with appropriate styles
                val doc = SimpleEditableStyledDocument(
                    initialParagraphStyle, initialTextStyle
                )
                doc.replace(0, 0, ReadOnlyStyledDocument.fromString(
                    styles.joinToString("") { it.text }, initialParagraphStyle, initialTextStyle, segOps
                ))
                val styleSpans = styles
                    .fold(StyleSpansBuilder<MapStyle>()) { acc, next -> acc.add(next, next.text.length) }
                    .create()
                doc.setStyleSpans(0, styleSpans)

                val symSol = astSpanMarkerCreator.create(jarRoot, doc)
                symSol.markAst()

                doc
            }
        } finally {
            publishComms.viewChannel.send(StatusUpdate("Highlighting", ""))
        }
    }

    suspend fun updateText(text: String) {
        updatesChannel.send(text)
    }

    suspend fun startRename() {
        val sel = trySpecialSelectWord() ?: return
        val srgName = getStyleSpans(sel.first, sel.last)
            .single().style.srgName ?: return
        caretSelectionBind.selectRange(sel.first, sel.last)
        val renameDialog = RenameDialog.create()
        val selBounds = selectionBounds.orElse(null) ?: throw IllegalStateException("Expected bounds")
        renameDialog.popup.show(this, selBounds.minX, selBounds.maxY)
        renameDialog.textField.requestFocus()
        renameDialog.popup.showingProperty().suspendUntilEqual(false)
        val text = renameDialog.textField.text
        if (text.isEmpty() || !text.all { it.isJavaIdentifierPart() } || !text[0].isJavaIdentifierStart()) {
            return
        }
        val mappings = publishComms.modelChannel.sendForResponse(RetrieveMappings)
        if (srgName in mappings.mappings && !askProceedRename()) {
            return
        }
        publishComms.modelChannel.send(Rename(srgName, text))
    }

    private suspend fun askProceedRename(): Boolean {
        return confirmSimple(
            "Overwrite Name", youWantTo = "overwrite an existing mapping"
        )
    }

    override fun selectWord() {
        when (val sel = trySpecialSelectWord()) {
            null -> super.selectWord()
            else -> caretSelectionBind.selectRange(sel.first, sel.last)
        }
    }

    private fun trySpecialSelectWord(): IntRange? {
        val afterCaret = caretPosition
        if (afterCaret !in (0 until length)) {
            // Don't really know how to handle out-of-range.
            // Let the superclass decide.
            return null
        }
        val doc = text
        if (doc[afterCaret].isJavaIdentifierPart()) {
            val start = doc.iterIds(afterCaret, -1)
            val end = doc.iterIds(afterCaret, 1) + 1
            check(start < end) { "No selection made." }
            return start..end
        }
        // try starting before the caret
        val beforeCaret = afterCaret - 1
        if (beforeCaret < 0) {
            return null
        }
        val start = doc.iterIds(beforeCaret, -1)
        // the caret is implicitly at the end, since we can't iterate forwards
        if (start < afterCaret) {
            return start..afterCaret
        }
        // Nothing found.
        return null
    }

    private fun String.iterIds(index: Int, step: Int): Int {
        var newIndex = index
        var validIndex = index
        while (newIndex in (0 until length) && this[newIndex].isJavaIdentifierPart()) {
            validIndex = newIndex
            newIndex += step
        }
        return validIndex
    }

}
