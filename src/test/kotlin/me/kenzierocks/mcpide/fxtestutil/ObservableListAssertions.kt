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