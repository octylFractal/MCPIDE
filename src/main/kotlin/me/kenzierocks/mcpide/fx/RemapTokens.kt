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

import me.kenzierocks.mcpide.SRG_REGEX
import me.kenzierocks.mcpide.comms.MappingInfo

/**
 * Given some text, tokenize it, replace names (not updating positioning), and produce a stream of styles.
 */
suspend fun remap(
    text: String,
    mappingInfo: MappingInfo
) : Sequence<MapStyle> {
    return sequence {
        var lastEnd = 0
        while (true) {
            val match = SRG_REGEX.find(text, startIndex = lastEnd) ?: break
            yieldMissed(text, lastEnd until match.range.first)
            val srgName = match.value
            val newName = (mappingInfo.mappings[srgName] ?: mappingInfo.exported[srgName])?.newName
                ?: srgName
            yield(DEFAULT_MAP_STYLE.copy(text = newName, srgName = srgName))
            lastEnd = match.range.last + 1
        }
        yieldMissed(text, lastEnd until text.length)
    }
}

private suspend fun SequenceScope<MapStyle>.yieldMissed(text: String, missed: IntRange) {
    if (!missed.isEmpty()) {
        yield(DEFAULT_MAP_STYLE.copy(text = text.substring(missed)))
    }
}
