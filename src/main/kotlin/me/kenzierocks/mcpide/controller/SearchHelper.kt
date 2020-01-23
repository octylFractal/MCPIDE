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

import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyListWrapper
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.withContext
import me.kenzierocks.mcpide.util.getValue
import me.kenzierocks.mcpide.util.setValue

/**
 * Class for adapting search functionality to [FindPopupController].
 *
 * @param T search result, should hold location information
 */
abstract class SearchHelper<T> {
    private val listInternalProperty by lazy { ReadOnlyListWrapper<T>(this, "list", FXCollections.observableArrayList()) }
    val listProperty = listInternalProperty.readOnlyProperty!!
    val list: ObservableList<T> by listProperty

    val searchingBinding = listProperty.emptyProperty().not()!!

    val searchTermProperty by lazy {
        object : SimpleStringProperty(this, "searchTerm", "") {
            override fun invalidated() {
                val new = get()
                val trimmed = new.trim()
                if (new != trimmed) {
                    set(trimmed)
                }
            }
        }
    }
    private var searchTerm: String by searchTermProperty

    private val currentSearchIndexProperty by lazy { SimpleIntegerProperty(this, "currentSearchIndex", -1) }
    private var currentSearchIndex: Int by currentSearchIndexProperty

    private val maxSearchIndexProperty = listProperty.sizeProperty()!!
    private val maxSearchIndex: Int by maxSearchIndexProperty

    private var searchJob: Job? = null

    val searchTrackerTextBinding by lazy(LazyThreadSafetyMode.NONE) {
        Bindings.createStringBinding({
            when {
                currentSearchIndex < 0 || maxSearchIndex < 0
                    || currentSearchIndex > maxSearchIndex -> "0/0"
                else -> "$currentSearchIndex/$maxSearchIndex"
            }
        }, arrayOf(currentSearchIndexProperty, maxSearchIndexProperty))!!
    }

    private fun requireList(): List<T> = requireNotNull(list.takeUnless { it.isEmpty() }) {
        "No search started yet!"
    }

    /**
     * Trigger search initialization again. This should be called if
     * [search]'s input changes.
     */
    suspend fun reSearch() {
        onSearchStart()
    }

    suspend fun onSearchStart(): Boolean {
        return withContext(Dispatchers.JavaFx.immediate) {
            val term = searchTerm
            if (term.isBlank()) {
                return@withContext false
            }
            val results = search(term)
            // Cancel any currently in progress search, wait for it to exit out
            searchJob?.cancelAndJoin()
            searchJob = coroutineContext[Job]!!
            list.clear()
            currentSearchIndex = -1
            // ensure we collect the flow on Default, avoid blocking UI
            val l = withContext(Dispatchers.Default) {
                results
                    .onEach { ensureActive() }
                    .toList()
            }
            return@withContext when {
                // Catch oddities about the search
                l.isEmpty() -> false
                else -> {
                    list.setAll(l)
                    currentSearchIndex = 0
                    moveToCurrentResult()
                    true
                }
            }
        }
    }

    private suspend fun moveToCurrentResult() {
        val l = requireList()
        val result = l[currentSearchIndex]
        moveToResult(result)
    }

    suspend fun onSearchNext() {
        val l = requireList()
        currentSearchIndex = (currentSearchIndex + 1) % l.size
        moveToCurrentResult()
    }

    suspend fun onSearchPrevious() {
        val l = requireList()
        // Correct `mod` for negative numbers
        val m = currentSearchIndex - 1 % l.size
        currentSearchIndex = when {
            0 <= m -> m
            else -> m + l.size
        }
        moveToCurrentResult()
    }

    suspend fun onSearchCanceled() {
        cancel()
    }

    // Implementation

    abstract fun search(term: String): Flow<T>
    abstract suspend fun moveToResult(result: T)
    abstract suspend fun cancel()

}
