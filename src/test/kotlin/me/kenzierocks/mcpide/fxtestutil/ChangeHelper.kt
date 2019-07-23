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