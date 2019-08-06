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

import com.github.javaparser.Position
import com.github.javaparser.TokenRange
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.LineNumberFactory
import java.nio.file.Path

class JavaEditorArea(var path: Path) : CodeArea() {
    private var lineOffsets: IntArray? = null

    init {
        isEditable = false
        paragraphGraphicFactory = LineNumberFactory.get(this)
    }

    fun setText(text: String) {
        replaceText(text)
        val offsets = IntArray(paragraphs.size)
        paragraphs.forEachIndexed { i, p ->
            offsets[i] = offsets.getOrElse(i) { 0 } + p.length()
        }
        lineOffsets = offsets
    }

    fun styleTokenRange(style: String, tokenRange: TokenRange) {
        val range = tokenRange.toRange().orElse(null) ?: return
        val begin = computeTextIndex(range.begin) ?: return
        val end = computeTextIndex(range.end) ?: return
        setStyleClass(begin, end, style)
    }

    private fun computeTextIndex(position: Position): Int? {
        val offsets = lineOffsets ?: return null
        if (position.line > offsets.lastIndex) {
            return null
        }
        return offsets[position.line] + position.column
    }
}
