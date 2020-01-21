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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.nio.file.Files
import java.nio.file.Path

data class SearchResult(
    val path: Path,
    val line: Int,
    val columns: IntRange
)

abstract class AsyncPathSearchHelper(
    private val root: Path
) : SearchHelper<SearchResult>(),
    CoroutineScope by CoroutineScope(Dispatchers.Default + CoroutineName("AsyncPathSearch")) {
    override fun search(term: String): Flow<SearchResult>? {
        val regex = term.toRegex(RegexOption.LITERAL)
        return flow<SearchResult> {
            Files.walk(root).use { paths ->
                paths.iterator().asFlow()
                    .filter { Files.isRegularFile(it) }
                    .map { file ->
                        Files.newBufferedReader(file).useLines { lines ->
                            lines.withIndex().flatMap { (i, line) ->
                                regex.findAll(line).map { SearchResult(file, i, it.range) }
                            }.asFlow()
                        }
                    }
                    .flowOn(Dispatchers.IO)
                    .buffer()
                    .collect { emitAll(it) }
            }
        }
    }

    override suspend fun moveToResult(result: SearchResult) {
        // does nothing, there's no jumping to do.
    }
}