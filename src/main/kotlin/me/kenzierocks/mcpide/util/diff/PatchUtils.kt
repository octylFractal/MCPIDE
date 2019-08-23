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

package me.kenzierocks.mcpide.util.diff

object PatchUtils {

    internal fun similar(patch: ContextualPatch, target: String, hunk: String, lineType: Char): Boolean {
        var target = target
        var hunk = hunk
        if (patch.c14nAccess) {
            if (patch.c14nWhitespace) {
                target = target.replace("[\t| ]+".toRegex(), " ")
                hunk = hunk.replace("[\t| ]+".toRegex(), " ")
            }
            val t = target.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val h = hunk.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            //don't check length, changing any modifier to default (removing it) will change length
            var targetIndex = 0
            var hunkIndex = 0
            while (targetIndex < t.size && hunkIndex < h.size) {
                val isTargetAccess = isAccess(t[targetIndex])
                val isHunkAccess = isAccess(h[hunkIndex])
                if (isTargetAccess || isHunkAccess) {
                    //Skip access modifiers
                    if (isTargetAccess) {
                        targetIndex++
                    }
                    if (isHunkAccess) {
                        hunkIndex++
                    }
                    continue
                }
                val hunkPart = h[hunkIndex]
                val targetPart = t[targetIndex]
                val labels = isLabel(targetPart) && isLabel(hunkPart)
                if (!labels && targetPart != hunkPart) {
                    return false
                }
                hunkIndex++
                targetIndex++
            }
            return h.size == hunkIndex && t.size == targetIndex
        }
        return if (patch.c14nWhitespace) {
            target.replace("[\t| ]+".toRegex(), " ") == hunk.replace("[\t| ]+".toRegex(), " ")
        } else {
            target == hunk
        }
    }

    private fun isAccess(data: String): Boolean {
        return data.equals("public", ignoreCase = true) ||
            data.equals("private", ignoreCase = true) ||
            data.equals("protected", ignoreCase = true) ||
            data.equals("final", ignoreCase = true)
    }

    private fun isLabel(data: String): Boolean { //Damn FernFlower
        return data.startsWith("label")
    }

}
