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

import com.github.javaparser.StringProvider
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.util.createLineOffsets
import me.kenzierocks.mcpide.util.produceTokens
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.LineNumberFactory
import org.fxmisc.richtext.model.PlainTextChange
import org.fxmisc.richtext.model.StyleSpan
import org.fxmisc.richtext.model.StyleSpans
import org.fxmisc.richtext.model.StyleSpansBuilder
import java.nio.file.Path

class JavaEditorArea(var path: Path) : CodeArea() {
    init {
        isEditable = false
        paragraphGraphicFactory = LineNumberFactory.get(this)
        val flowChannel = Channel<PlainTextChange>(1)
        val sub = multiPlainChanges()
            .withDefaultEvent(listOf())
            .subscribe { it.forEach(flowChannel::sendBlocking) }
        flowChannel.invokeOnClose { sub.unsubscribe() }
        // When no edits for 500ms, compute highlighting
        // If new edits while highlighting, toss out highlight result
        val highlightFlow = flowChannel.consumeAsFlow()
            .debounce(500)
            .mapLatest { text to computeHighlighting(this.text) }
            .flowOn(Dispatchers.Default + CoroutineName("Highlighting"))
        CoroutineScope(Dispatchers.JavaFx + CoroutineName("HighlightApplication")).launch {
            highlightFlow.collect { (text, spans) ->
                // Verify spans are valid, then apply.
                if (spans != null && this@JavaEditorArea.text == text) {
                    setStyleSpans(0, spans)
                }
            }
        }
    }

    private suspend fun computeHighlighting(text: String): StyleSpans<Collection<String>>? {
        val offsets = createLineOffsets(text)
        return coroutineScope {
            val tokens = produceTokens(StringProvider(text))

            val builder = StyleSpansBuilder<Collection<String>>()
            var lastSpan = 0
            while (!tokens.isClosedForReceive) {
                val token = tokens.receiveOrNull() ?: break
                styleFor(token.kind).let { style ->
                    val start = offsets.computeTextIndex(token.beginLine, token.beginColumn)
                    val end = offsets.computeTextIndex(token.endLine, token.endColumn) + 1
                    builder.add(setOf("default-text"), start - lastSpan)
                    builder.add(setOf(style), end - start)
                    lastSpan = end
                }
            }
            builder.add(setOf("default-text"), text.length - lastSpan)
            builder.create()
        }
    }

    fun setText(text: String) {
        replaceText(text)
        setStyleSpans(0, StyleSpans.singleton(StyleSpan(setOf("default-text"), text.length)))
    }

}
