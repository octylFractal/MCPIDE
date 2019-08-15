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

package me.kenzierocks.mcpide.util

import java.util.TreeMap

// to be inline-class'd later
class LineOffsets(
    private val offsets: IntArray
) {
    /**
     * Compute index into original text, with 1-based lines and columns.
     */
    fun computeTextIndex(line: Int, column: Int): Int {
        return offsets[(line - 1).coerceIn(0, offsets.lastIndex)] + (column - 1).coerceAtLeast(0)
    }
}

// Stores total offset at offsets[lineNumber - 1]
// So, offsets[0] is always 0, offsets[1] is the length of the first line

fun createLineOffsets(text: String): LineOffsets {
    val offsets = TreeMap<Int, Int>()
    text.lineSequence().forEachIndexed { i, line ->
        offsets[i + 1] = offsets.getOrDefault(i, 0) + line.length + 1
    }
    return LineOffsets(
        IntArray(offsets.lastKey() + 1) { k -> offsets[k] ?: 0 }
    )
}

