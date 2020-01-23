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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.withIndex
import me.kenzierocks.mcpide.fx.HighlightRequirements
import me.kenzierocks.mcpide.fx.Highlighter
import me.kenzierocks.mcpide.fx.JeaDoc
import me.kenzierocks.mcpide.fx.remap
import java.nio.file.Files
import java.nio.file.Path

data class SearchResult(
    /**
     * File result was found in.
     */
    val path: Path,
    /**
     * Zero-indexed line number.
     */
    val lineNo: Int,
    /**
     * Zero-indexed column range.
     */
    val columns: IntRange,
    /**
     * Line text.
     */
    val line: String,
    /**
     * Highlighted result document containing the text from the line.
     */
    val highlighting: Deferred<JeaDoc>
)

abstract class AsyncPathSearchHelper(
    private val root: Path,
    private val highlighter: Highlighter,
    private val highlightRequirements: Deferred<HighlightRequirements>
) : SearchHelper<SearchResult>(),
    CoroutineScope by CoroutineScope(Dispatchers.Default + CoroutineName("AsyncPathSearch")) {
    override fun search(term: String): Flow<SearchResult> {
        val regex = term.toRegex(RegexOption.LITERAL)
        return flow {
            Files.walk(root).use { paths ->
                paths.iterator().asFlow()
                    .filter { Files.isRegularFile(it) }
                    .map { file ->
                        findMatches(file, regex)
                    }
                    .buffer()
                    .collect { emitAll(it) }
            }
        }
    }

    private data class FileLine(
        val line: String,
        val highlightedText: Flow<JeaDoc>
    )

    private fun findMatches(file: Path, regex: Regex): Flow<SearchResult> {
        val highlightingScope = CoroutineScope(
            Dispatchers.Default + CoroutineName("AsyncPathHighlighting")
        )
        return flow {
            val text = Files.readString(file).replace("\r\n", "\n")

            // Start highlighting text in advance
            val highlightedTextDeferred = highlightingScope.async {
                highlighter.highlight(highlightRequirements.await(), text, file.toString())
            }
            val highlightedText = flow {
                emit(highlightedTextDeferred.await())
            }
            val remappedText = remap(text, highlightRequirements.await().mappings)
                .joinToString("") { it.text }

            val lines = remappedText.split("\n").map {
                FileLine(it, highlightedText)
            }
            emitAll(lines.asFlow())
        }
            .flowOn(Dispatchers.IO)
            .withIndex()
            .flatMapConcat { (lineNo, line) ->
                regex.findAll(line.line)
                    .asFlow()
                    .map { result ->
                        val highlightedLine = highlightingScope.async {
                            line.highlightedText
                                .single()
                                .subDocument(lineNo)
                        }
                        SearchResult(file, lineNo, result.range, line.line, highlightedLine)
                    }
                    .flowOn(Dispatchers.Default)
            }
    }

    override suspend fun moveToResult(result: SearchResult) {
        // does nothing, there's no jumping to do.
    }
}
