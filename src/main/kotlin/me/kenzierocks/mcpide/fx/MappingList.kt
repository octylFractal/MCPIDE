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

import com.google.common.base.Preconditions.checkElementIndex
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList
import java.util.ArrayList
import java.util.Collections

class MappingList<O, N>(val originalList: ObservableList<out O>,
                        val mappingFunc: (O) -> N,
                        val removeTest: (N) -> Boolean = { true }) : TransformationList<N, O>(originalList) {
    private val storageList = ArrayList<N?>(originalList.size)

    init {
        setFromOriginal()
    }

    private fun ensureCapacity(capacity: Int) {
        if (storageList.size < capacity) {
            val diff = capacity - storageList.size
            storageList.ensureCapacity(capacity)
            storageList.addAll(Collections.nCopies(diff, null))
        }
    }

    private fun setFromOriginal() {
        ensureCapacity(originalList.size)
        originalList.forEachIndexed { i, o ->
            storageList[i] = mappingFunc(o)
        }
    }

    override val size: Int
        get() = originalList.size

    override fun get(index: Int): N {
        checkElementIndex(index, size, "get")
        return storageList[index]!!
    }

    // direct mapping -- no index changes
    override fun getViewIndex(index: Int) = index

    override fun getSourceIndex(index: Int) = index

    fun invalidateCaches() {
        setFromOriginal()
    }

    override fun sourceChanged(c: ListChangeListener.Change<out O>?) {
        if (c == null) return
        // fire a new change similar to the one that came in
        beginChange()
        while (c.next()) {
            if (c.wasPermutated()) {
                val permRange = c.from..(c.to - 1)
                val permArray = IntArray(permRange.count())
                for (i in permRange) {
                    val perm = c.getPermutation(i)
                    val tmp = storageList[i]
                    storageList[i] = storageList[perm]
                    storageList[perm] = tmp
                    permArray[i - c.from] = perm
                }
                nextPermutation(c.from, c.to, permArray)
            } else {
                if (c.wasRemoved()) {
                    // removes section that was removed
                    val sbl = storageList.subList(c.from, c.from + c.removedSize)
                    (sbl.size - 1 downTo 0)
                        .filter { removeTest(sbl[it]!!) }
                        .forEach {
                            nextRemove(it, sbl[it])
                            sbl.removeAt(it)
                        }
                }
                if (c.wasAdded()) {
                    // the add should just be a range, we can insert the transformed list at from index
                    storageList.addAll(c.from, c.addedSubList.map(mappingFunc))
                    nextAdd(c.from, c.to)
                }
            }
        }
        endChange()
    }
}
