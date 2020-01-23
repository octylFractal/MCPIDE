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

import com.github.javaparser.JavaToken
import com.github.javaparser.Position
import me.kenzierocks.mcpide.util.requireRange
import org.fxmisc.richtext.model.EditableStyledDocument
import org.fxmisc.richtext.model.StyledDocument

typealias EditableJeaDoc = EditableStyledDocument<Collection<String>, String, MapStyle>
typealias JeaDoc = StyledDocument<Collection<String>, String, MapStyle>

fun StyledDocument<*, *, *>.offset(pos: Position): Int {
    return position(pos.line - 1, pos.column - 1).toOffset()
}

fun StyledDocument<*, *, *>.offsets(token: JavaToken): IntRange {
    val range = token.requireRange()
    return offset(range.begin)..offset(range.end)
}

fun EditableJeaDoc.updateStyle(range: IntRange, copyImpl: MapStyle.() -> MapStyle) {
    val styleSpans = getStyleSpans(range.first, range.last + 1)
    if (styleSpans.count() > 1) {
        assert(true) { "I would like a breakpoint please!" }
    }
    val originalStyle = styleSpans.single().style
    setStyle(range.first, range.last + 1, originalStyle.copyImpl())
}
