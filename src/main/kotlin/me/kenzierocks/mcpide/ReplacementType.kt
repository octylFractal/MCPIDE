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

enum class ReplacementType(val namePrefix: String?, val botCommand: String?) {
    FIELD("field", "sf"), METHOD("func", "sm"), PARAMETER("p", "sp"), CUSTOM(null, null);

    val namePrefixUnderscore = namePrefix + "_"
}

const val REPLACEMENT_TYPE_SEPARATOR = ";\u0001 replacementType="
inline fun Config.forEachReplacementType(body: ((String, String, ReplacementType) -> Unit)) {
    forEach { e ->
        val (k, combinedV) = e
        val split = combinedV.split(
            delimiters = *arrayOf(REPLACEMENT_TYPE_SEPARATOR),
            limit = 2
        )
        val v = split[0]
        val replacementType =
            if (split.size >= 2) ReplacementType.valueOf(split.last())
            else ReplacementType.CUSTOM
        body(k, v, replacementType)
    }
}