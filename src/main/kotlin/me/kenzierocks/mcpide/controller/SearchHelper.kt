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

/**
 * Class for adapting search functionality to [FindPopupController].
 *
 * @param search turn a term into a sequence of locatable Ts
 * @param moveToResult move to a given result
 * @param T search result, should hold location information
 */
class SearchHelper<T>(
    private val search: (term: String) -> Sequence<T>?,
    private val moveToResult: (result: T) -> Unit
) {

    fun bindTo(fpc: FindPopupController) {
        fpc.onStartSearch = this::onSearchStart
        fpc.onSearchNext = this::onSearchNext
        fpc.onSearchPrevious = this::onSearchPrevious
    }

    private var list: List<T>? = null
    private var index: Int = -1

    private fun requireList(): List<T> = requireNotNull(list) {
        "No search started yet!"
    }

    /**
     * Trigger search initialization again. This should be called if
     * [search]'s input changes.
     */
    fun reSearch(fpc: FindPopupController) {
        fpc.searching = false
        fpc.currentSearchIndex = -1
        fpc.maxSearchIndex = -1
        onSearchStart(fpc)
    }

    private fun onSearchStart(fpc: FindPopupController): Boolean {
        val term = fpc.searchTerm
        if (term.isBlank()) {
            return false
        }
        val results = search(term)
        list = null
        index = -1
        return when (results) {
            null -> false
            else -> {
                val l = results.toList()
                when {
                    // Catch oddities about the search
                    l.isEmpty() -> false
                    else -> {
                        list = l
                        index = 0
                        pushSearch(fpc)
                        true
                    }
                }
            }
        }
    }

    private fun pushSearch(fpc: FindPopupController) {
        val l = requireList()
        val result = l[index]
        moveToResult(result)
        fpc.currentSearchIndex = index + 1
        fpc.maxSearchIndex = l.lastIndex + 1
    }

    private fun onSearchNext(fpc: FindPopupController) {
        val l = requireList()
        index = (index + 1) % l.size
        pushSearch(fpc)
    }

    private fun onSearchPrevious(fpc: FindPopupController) {
        val l = requireList()
        // Correct `mod` for negative numbers
        val m = index - 1 % l.size
        index = when {
            0 <= m -> m
            else -> m + l.size
        }
        pushSearch(fpc)
    }
}