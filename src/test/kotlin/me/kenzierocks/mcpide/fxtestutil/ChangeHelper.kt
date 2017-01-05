package me.kenzierocks.mcpide.fxtestutil

import java.util.Arrays

object ChangeHelper {
    fun addRemoveChangeToString(from: Int, to: Int, list: List<*>, removed: List<*>): String {
        val b = StringBuilder()

        if (removed.isEmpty()) {
            b.append(list.subList(from, to))
            b.append(" added at ").append(from)
        } else {
            b.append(removed)
            if (from == to) {
                b.append(" removed at ").append(from)
            } else {
                b.append(" replaced by ")
                b.append(list.subList(from, to))
                b.append(" at ").append(from)
            }
        }
        return b.toString()
    }

    fun permChangeToString(permutation: IntArray): String {
        return "permutated by " + Arrays.toString(permutation)
    }

    fun updateChangeToString(from: Int, to: Int): String {
        return "updated at range [$from, $to)"
    }
}