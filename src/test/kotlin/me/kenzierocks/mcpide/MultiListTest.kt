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

import com.google.common.collect.ImmutableList
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableListBase
import me.kenzierocks.mcpide.fx.MultiList
import me.kenzierocks.mcpide.fxtestutil.assertChangeIteratorsEqual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.ArrayList


@DisplayName("A MultiList")
class MultiListTest {

    companion object {
        private fun ml(vararg lists: ObservableList<String>) = MultiList(*lists)
        private fun emptyObsList() = FXCollections.observableArrayList<String>()
    }

    @Nested
    @DisplayName("is empty when created with")
    internal inner class IsEmpty {

        @DisplayName("no lists")
        @Test
        internal fun noLists() {
            assertTrue(ml().isEmpty())
        }

        @DisplayName("an empty list")
        @Test
        internal fun anEmptyList() {
            assertTrue(ml(FXCollections.emptyObservableList()).isEmpty())
        }

        @DisplayName("multiple empty lists")
        @Test
        internal fun multipleEmptyLists() {
            assertTrue(ml(
                emptyObsList(),
                emptyObsList(),
                emptyObsList(),
                emptyObsList()
            ).isEmpty())
        }

        @DisplayName("a non-empty list that is then cleared")
        @Test
        internal fun nonEmptyList() {
            val obs = FXCollections.observableArrayList<String>()
            obs += ""
            val ml = ml(obs)
            obs.clear()
            assertTrue(ml.isEmpty())
        }
    }

    @Nested
    @DisplayName("has a size of 1 when created with")
    internal inner class Size1 {

        @DisplayName("a single-element list")
        @Test
        internal fun aSingleElementList() {
            val ml = ml(FXCollections.observableArrayList("1"))
            assertEquals(1, ml.size)
            assertEquals("1", ml[0])
        }

        @DisplayName("a single-element list and multiple empty lists")
        @Test
        internal fun singleElementListAndMultipleEmptyLists() {
            val ml = ml(
                FXCollections.observableArrayList("1"),
                emptyObsList(),
                emptyObsList(),
                emptyObsList(),
                emptyObsList()
            )
            assertEquals(1, ml.size)
            assertEquals("1", ml[0])
        }

        @DisplayName("a two-element list that then has one element removed")
        @Test
        internal fun nonEmptyList() {
            val obs = FXCollections.observableArrayList("1", "2")
            val ml = ml(obs)
            obs.removeAt(0)
            assertEquals(1, ml.size)
            assertEquals("2", ml[0])
        }

    }

    @Nested
    @DisplayName("properly offsets changes from sub-lists")
    internal inner class OffsetsChanges {

        @DisplayName("with a single sub-list")
        @Test
        internal fun singleList() {
            // assumed that FXCollections properly gives off changes
            val obs = FXCollections.observableArrayList<String>()
            val ml = ml(obs)
            val changeList = ArrayList<ListChangeListener.Change<out String>>()
            val changeListMl = ArrayList(changeList)
            obs.addListener(ListChangeListener { change ->
                changeList += change
            })
            ml.addListener(ListChangeListener { change ->
                changeListMl += change
            })

            obs.add("1")
            obs.add("2")
            obs.add("3")
            obs.removeAt(0)
            assertEquals(listOf("2", "3"), obs)
            obs.add(0, "1")
            obs.remove(0, 3)
            assertEquals(listOf<String>(), obs)
            obs.addAll("4", "2", "1", "3")
            obs.sort()
            assertEquals(listOf("1", "2", "3", "4"), obs)

            assertEquals(obs, ml)
            assertChangeIteratorsEqual(changeList, changeListMl)
        }

        @DisplayName("with the mutated list at the start")
        @Test
        internal fun mutatedListAtStart() {
            // assumed that FXCollections properly gives off changes
            val mutated = FXCollections.observableArrayList<String>()
            val static = FXCollections.observableArrayList("1")
            val ml = ml(mutated, static)
            val changeList = ArrayList<ListChangeListener.Change<out String>>()
            val changeListMl = ArrayList(changeList)
            mutated.addListener(ListChangeListener { change ->
                changeList += change
            })
            ml.addListener(ListChangeListener { change ->
                changeListMl += change
            })

            mutated.add("1")
            mutated.add("2")
            mutated.add("3")
            mutated.removeAt(0)
            assertEquals(listOf("2", "3"), mutated)
            mutated.add(0, "1")
            mutated.remove(0, 3)
            assertEquals(listOf<String>(), mutated)
            mutated.addAll("4", "2", "1", "3")
            mutated.sort()
            assertEquals(listOf("1", "2", "3", "4"), mutated)

            assertEquals(mutated + static, ml)
            assertChangeIteratorsEqual(changeList, changeListMl)
        }

        @DisplayName("with the mutated list at the end")
        @Test
        internal fun mutatedListAtEnd() {
            // assumed that FXCollections properly gives off changes
            val mutated = FXCollections.observableArrayList<String>()
            val static = FXCollections.observableArrayList("1")
            val ml = ml(static, mutated)
            val changeList = ArrayList<ListChangeListener.Change<out String>>()
            val changeListMl = ArrayList(changeList)
            mutated.addListener(ListChangeListener { change ->
                changeList += change
            })
            ml.addListener(ListChangeListener { change ->
                changeListMl += UnoffsetChange(change, static.size, mutated.size)
            })

            mutated.add("1")
            mutated.add("2")
            mutated.add("3")
            mutated.removeAt(0)
            assertEquals(listOf("2", "3"), mutated)
            mutated.add(0, "1")
            mutated.remove(0, 3)
            assertEquals(listOf<String>(), mutated)
            mutated.addAll("4", "2", "1", "3")
            mutated.sort()
            assertEquals(listOf("1", "2", "3", "4"), mutated)

            assertEquals(static + mutated, ml)
            assertChangeIteratorsEqual(changeList, changeListMl)
        }

        @DisplayName("with the mutated list in the middle")
        @Test
        internal fun mutatedListAtMiddle() {
            // assumed that FXCollections properly gives off changes
            val mutated = FXCollections.observableArrayList<String>()
            val static1 = FXCollections.observableArrayList("1")
            val static2 = FXCollections.observableArrayList("2")
            val ml = ml(static1, mutated, static2)
            val changeList = ArrayList<ListChangeListener.Change<out String>>()
            val changeListMl = ArrayList(changeList)
            mutated.addListener(ListChangeListener { change ->
                changeList += freezeChange(change)
            })
            ml.addListener(ListChangeListener { change ->
                changeListMl += freezeChange(UnoffsetChange(change, static1.size, mutated.size))
            })

            mutated.add("1")
            mutated.add("2")
            mutated.add("3")
            mutated.removeAt(0)
            assertEquals(listOf("2", "3"), mutated)

            assertEquals(static1 + mutated + static2, ml)
            assertChangeIteratorsEqual(changeList, changeListMl)

            mutated.add(0, "1")
            mutated.remove(0, 3)
            assertEquals(listOf<String>(), mutated)

            assertEquals(static1 + mutated + static2, ml)
            assertChangeIteratorsEqual(changeList, changeListMl)

            mutated.addAll("4", "2", "1", "3")
            mutated.sort()
            assertEquals(listOf("1", "2", "3", "4"), mutated)

            assertEquals(static1 + mutated + static2, ml)
            assertChangeIteratorsEqual(changeList, changeListMl)
        }

    }
}

private fun <E> freezeChange(change: ListChangeListener.Change<E>): ListChangeListener.Change<out E> {
    val copy: List<E> = ImmutableList.copyOf(change.list)
    val list = object : ObservableListBase<E>(), List<E> by copy {
        fun duplicateChange() {
            beginChange()
            while (change.next()) {
                when {
                    change.wasPermutated() -> {
                        val permRange = change.from until change.to
                        val permArray = IntArray(permRange.count())
                        for (i in permRange) {
                            permArray[i - change.from] = change.getPermutation(i)
                        }
                        nextPermutation(change.from, change.to, permArray)
                    }
                    change.wasAdded() -> nextAdd(change.from, change.to)
                    change.wasRemoved() -> nextRemove(change.from, change.removed)
                    change.wasUpdated() -> nextUpdate(change.from)
                }
            }
            change.reset()
            endChange()
        }

        override fun iterator() = super.iterator()

        override fun listIterator() = super.listIterator()

        override fun listIterator(index: Int) = super.listIterator(index)

        override fun subList(fromIndex: Int, toIndex: Int) = super.subList(fromIndex, toIndex)
        override fun contains(element: E): Boolean {
            return copy.contains(element)
        }

        override fun containsAll(elements: Collection<E>): Boolean {
            return copy.containsAll(elements)
        }

        override fun indexOf(element: E): Int {
            return copy.indexOf(element)
        }

        override fun isEmpty(): Boolean {
            return copy.isEmpty()
        }

        override fun lastIndexOf(element: E): Int {
            return copy.lastIndexOf(element)
        }
    }
    var frozenChange: ListChangeListener.Change<out E>? = null
    list.addListener(ListChangeListener { frozenChange = it })
    list.duplicateChange()
    return frozenChange!!
}

/**
 * Removes an offset from all changes
 */
private class UnoffsetChange<E>(private val change: ListChangeListener.Change<E>,
                                private val offset: Int,
                                private val sizeOfList: Int)
    : ListChangeListener.Change<E>(change.list) {

    companion object {
        private val EMPTY_INT_ARRAY = IntArray(0)
    }

    override fun wasUpdated() = change.wasUpdated()

    override fun wasAdded() = change.wasAdded()

    override fun wasPermutated() = change.wasPermutated()

    override fun wasReplaced() = change.wasReplaced()

    override fun wasRemoved() = change.wasRemoved()

    override fun next() = change.next()

    override fun getTo() = change.to - offset

    override fun getFrom() = change.from - offset

    override fun reset() = change.reset()

    override fun getPermutation(i: Int) = change.getPermutation(i + offset) - offset

    override fun getPermutation() = EMPTY_INT_ARRAY

    override fun getRemoved(): List<E> = change.removed

    override fun getList(): ObservableList<E> {
        val sublist = change.list.subList(offset, offset + sizeOfList)
        return sublist as? ObservableList<E> ?: FXCollections.observableList(sublist)
    }

    override fun toString() = "Change $change - offset $offset"

}
