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

package me.kenzierocks.mcpide.fxtestutil

import javafx.collections.ListChangeListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

fun <T> assertChangeIteratorsEqual(expected: Iterable<ListChangeListener.Change<out T>>,
                                   actual: Iterable<ListChangeListener.Change<out T>>) {
    expected.zip(actual).forEach {
        val (exp, act) = it
        val changesStr = { "Expected: $exp\nActual: $act" }
        while (exp.next()) {
            assertTrue(act.next(), { "no next for expected, changes: " + changesStr() })
            if (exp.wasPermutated()) {
                assertTrue(act.wasPermutated(),
                    { "not a permutation for expected, changes: " + changesStr() })
                assertEquals(exp.from, act.from)
                assertEquals(exp.to, act.to)
                for (i in exp.from..(exp.to - 1)) {
                    assertEquals(exp.getPermutation(i), act.getPermutation(i),
                        { "perm differs at $i, changes: " + changesStr() })
                }
            } else if (exp.wasAdded()) {
                assertTrue(act.wasAdded(),
                    { "not an added for expected, changes: " + changesStr() })
                assertEquals(exp.from, act.from)
                assertEquals(exp.to, act.to)
                assertEquals(exp.addedSubList, act.addedSubList,
                    { "original lists were ${exp.list} and ${act.list}" })
            } else if (exp.wasRemoved()) {
                assertTrue(act.wasRemoved(),
                    { "not a removed for expected, changes: " + changesStr() })
                assertEquals(exp.from, act.from)
                assertEquals(exp.to, act.to)
            } else if (exp.wasUpdated()) {
                assertTrue(act.wasUpdated(),
                    { "not an updated for expected, changes: " + changesStr() })
                assertEquals(exp.from, act.from)
                assertEquals(exp.to, act.to)
            } else {
                fail("Unknown change: $exp")
            }
        }
    }
}