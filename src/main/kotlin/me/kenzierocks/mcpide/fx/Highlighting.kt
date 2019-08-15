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

import com.github.javaparser.GeneratedJavaParserConstants
import com.github.javaparser.GeneratedJavaParserConstants.STRING_LITERAL
import com.github.javaparser.JavaToken
import com.github.javaparser.JavaToken.Category.COMMENT
import com.github.javaparser.JavaToken.Category.EOL
import com.github.javaparser.JavaToken.Category.IDENTIFIER
import com.github.javaparser.JavaToken.Category.KEYWORD
import com.github.javaparser.JavaToken.Category.LITERAL
import com.github.javaparser.JavaToken.Category.OPERATOR
import com.github.javaparser.JavaToken.Category.SEPARATOR
import com.github.javaparser.JavaToken.Category.WHITESPACE_NO_EOL
import com.github.javaparser.StringProvider
import com.github.javaparser.Token
import com.github.javaparser.TokenTypes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.withIndex
import me.kenzierocks.mcpide.SRG_REGEX
import me.kenzierocks.mcpide.SrgMapping
import me.kenzierocks.mcpide.detectSrgType
import me.kenzierocks.mcpide.util.createLineOffsets
import me.kenzierocks.mcpide.util.produceTokens
import me.kenzierocks.mcpide.util.toExtendedString
import mu.KotlinLogging
import org.fxmisc.richtext.model.StyleSpans
import org.fxmisc.richtext.model.StyleSpansBuilder
import java.util.SortedMap
import java.util.TreeMap

private val logger = KotlinLogging.logger { }

class Highlighting(
    val newText: String,
    val spans: StyleSpans<MapStyle>
)

suspend fun provideHighlighting(unmappedText: String, mappings: Map<String, SrgMapping>): Highlighting? {
    return coroutineScope {
        val mapped = provideMapped(unmappedText, mappings)
        val tokens = produceTokens(StringProvider(mapped.text))
        val offsets = createLineOffsets(mapped.text)

        val builder = StyleSpansBuilder<MapStyle>()
        var lastSpan = 0
        var lastToken: Token? = null
        var tokenCount = 0
        for (token in tokens) {
            val prevToken = lastToken
            lastToken = token
            if (token.kind == JavaToken.Kind.GT.kind
                && prevToken?.kind == JavaToken.Kind.GT.kind
                && prevToken.image == ">>") {
                // JavaParser oddity. Ignore this token.
                continue
            }
            val start = offsets.computeTextIndex(token.beginLine, token.beginColumn)
            val end = offsets.computeTextIndex(token.endLine, token.endColumn) + 1
            val gap = start - lastSpan
            if (gap < 0) {
                // this is odd. discard this token.
                logger.warn {
                    "Discarding incorrectly positioned token: " +
                        "${token.toExtendedString()}, last=${prevToken?.toExtendedString()}"
                }
                continue
            }
            styleFor(token.kind).let { style ->
                if (gap > 0) {
                    builder.add(MapStyle(setOf("default-text"), null), gap)
                }
                val srgName = when (token.kind) {
                    GeneratedJavaParserConstants.IDENTIFIER -> {
                        val change = when (val c = mapped.changes[tokenCount]) {
                            null -> null
                            else -> {
                                val (srg, new, cTok) = c
                                check(new == token.image) {
                                    "Out of sync! $srg->$new (${cTok.toExtendedString()})" +
                                        " is not ${token.toExtendedString()}"
                                }
                                srg
                            }
                        }
                        tokenCount++
                        change
                    }
                    else -> null
                }
                builder.add(MapStyle(setOf(style), srgName), end - start)
                lastSpan = end
            }
        }
        builder.add(MapStyle(setOf("default-text"), null), mapped.text.length - lastSpan)
        Highlighting(mapped.text, builder.create())
    }
}

private data class Mapped(
    val text: String,
    /** Changed mappings, keyed by token index. */
    val changes: SortedMap<Int, Change>
)

private data class Change(
    val srg: String,
    val new: String,
    val token: Token
)

private suspend fun provideMapped(text: String, mappings: Map<String, SrgMapping>): Mapped {
    return coroutineScope {
        val replacements = mutableMapOf<String, String>()
        val additions = TreeMap<Int, Change>()

        produceTokens(StringProvider(text)).consumeAsFlow()
            .filter { it.kind == GeneratedJavaParserConstants.IDENTIFIER }
            .withIndex()
            .mapNotNull { (i, token) ->
                val newName = (mappings[token.image]?.newName
                    ?: token.image.takeIf { n -> n.detectSrgType() != null })
                newName?.let { name -> i to Change(token.image, name, token) }
            }
            .collect { (index, change) ->
                replacements[change.srg] = change.new
                additions[index] = change
            }

        Mapped(text.replaceMappings(replacements), additions)
    }
}

private fun String.replaceMappings(table: Map<String, String>): String {
    return replace(SRG_REGEX) { match ->
        table.getOrDefault(match.value, match.value)
    }
}

fun styleFor(kind: Int): String {
    return when (TokenTypes.getCategory(kind)!!) {
        WHITESPACE_NO_EOL, EOL, SEPARATOR, OPERATOR ->
            "default-text"
        COMMENT -> "comment"
        IDENTIFIER -> "identifier"
        KEYWORD -> "keyword"
        LITERAL -> when (kind) {
            STRING_LITERAL -> "string-literal"
            else -> "other-literal"
        }
    }
}