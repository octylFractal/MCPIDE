package me.kenzierocks.mcpide

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import me.kenzierocks.mcpide.fx.MappingList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("A MappingList")
class MappingListTest {

    companion object {
        private fun originalList(vararg content: Int): ObservableList<Int> {
            return FXCollections.observableArrayList<Int>(*content.toTypedArray())
        }

        private fun mapList(originalList: ObservableList<Int> = originalList()): MappingList<Int, String> {
            return MappingList(originalList, Int::toString)
        }
    }

    @Nested
    @DisplayName("is empty when")
    inner class IsEmptyWhen {

        @Test
        @DisplayName("the original list is empty")
        fun originalListEmpty() {
            val ml = mapList()
            assertEquals(0, ml.size)
        }

        @Test
        @DisplayName("the original list starts with one object and has it removed")
        fun originalListOne() {
            val ol = originalList(1)
            val ml = mapList(ol)
            assertEquals(1, ml.size)
            assertEquals("1", ml[0])

            ol.removeAt(0)
            assertEquals(0, ml.size)
        }

        @Test
        @DisplayName("the original list starts with two objects and has them removed")
        fun originalListTwo() {
            val ol = originalList(1, 2)
            val ml = mapList(ol)
            assertEquals(2, ml.size)
            assertEquals("1", ml[0])
            assertEquals("2", ml[1])

            ol.removeAt(0)
            assertEquals(1, ml.size)
            assertEquals("2", ml[0])

            ol.removeAt(0)
            assertEquals(0, ml.size)
        }

    }

    @Nested
    @DisplayName("has one object when")
    inner class HasOneObjectWhen {

        @Test
        @DisplayName("the original list starts empty and has one object added")
        fun originalListEmpty() {
            val ol = originalList()
            val ml = mapList(ol)
            assertEquals(0, ml.size)

            ol.add(1)
            assertEquals(1, ml.size)
            assertEquals("1", ml[0])
        }

        @Test
        @DisplayName("the original list starts with one object and has it replaced")
        fun originalListOne() {
            val ol = originalList(1)
            val ml = mapList(ol)
            assertEquals(1, ml.size)
            assertEquals("1", ml[0])

            ol[0] = 2
            assertEquals(1, ml.size)
            assertEquals("2", ml[0])
        }

        @Test
        @DisplayName("the original list starts with two objects and has one replaced and then another removed")
        fun originalListTwo() {
            val ol = originalList(1, 2)
            val ml = mapList(ol)
            assertEquals(2, ml.size)
            assertEquals("1", ml[0])
            assertEquals("2", ml[1])

            ol[0] = 3
            assertEquals(2, ml.size)
            assertEquals("3", ml[0])
            assertEquals("2", ml[1])

            ol.removeAt(1)
            assertEquals(1, ml.size)
            assertEquals("3", ml[0])
        }

    }

}