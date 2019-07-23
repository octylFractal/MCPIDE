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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableRangeMap
import com.google.common.collect.Range
import com.google.common.collect.RangeMap
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableListBase
import java.util.HashMap
import java.util.HashSet

data class ListWithOffset<T>(val list: ObservableList<T>, val offset: Int)

private fun id(a: Any?) = System.identityHashCode(a)


class MultiList<T>(vararg lists: ObservableList<T>) : ObservableListBase<T>() {

    init {
        // Validate lists -- no duplicate lists
        val seen = HashSet<Int>()
        lists.forEachIndexed { i, list ->
            if (!seen.add(id(list))) {
                throw IllegalArgumentException("duplicate list $list at index $i")
            }
        }
    }

    private val sourceLists: List<ObservableList<T>>
    private val listToOffsetMap: MutableMap<Int, Int> = HashMap()
    private lateinit var listRangeMap: RangeMap<Int, ListWithOffset<T>>
    private var realSize: Int = 0
    override val size: Int get() = realSize

    init {
        sourceLists = ImmutableList.copyOf(lists)
        sourceLists.forEach {
            it.addListener(ListChangeListener { change ->
                // must represent the appropriate view in changes
                reconstructRanges()
                fireChangeListener(change)
            })
        }
        reconstructRanges()
    }

    private fun fireChangeListener(change: ListChangeListener.Change<out T>) {
        // We need to adapt all of the indexes in change to the list's offset
        val offset = listToOffsetMap[id(change.list)]!!
        beginChange()
        while (change.next()) {
            if (change.wasPermutated()) {
                val permRange = change.from..(change.to - 1)
                val permArray = IntArray(permRange.count())
                for (i in permRange) {
                    permArray[i - change.from] = change.getPermutation(i) + offset
                }
                nextPermutation(change.from + offset, change.to + offset, permArray)
            } else if (change.wasAdded()) {
                nextAdd(change.from + offset, change.to + offset)
            } else if (change.wasRemoved()) {
                nextRemove(change.from + offset, change.removed)
            } else if (change.wasUpdated()) {
                nextUpdate(change.from + offset)
            }
        }
        endChange()
    }

    private fun reconstructRanges() {
        val b = ImmutableRangeMap.builder<Int, ListWithOffset<T>>()
        var lastIndex = 0
        for (list in sourceLists) {
            val nextLast = lastIndex + list.size
            listToOffsetMap.put(id(list), lastIndex)
            if (list.isEmpty()) {
                continue
            }
            b.put(Range.closedOpen(lastIndex, nextLast), ListWithOffset(list, lastIndex))
            lastIndex = nextLast
        }
        listRangeMap = b.build()
        realSize = lastIndex
    }

    override fun get(index: Int): T? {
        val (subList, offset) = listRangeMap.get(index)
            ?: throw IndexOutOfBoundsException("$index is larger than ${size - 1}")
        return subList[index - offset]
    }

}