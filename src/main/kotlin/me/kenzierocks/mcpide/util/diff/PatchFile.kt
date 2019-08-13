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

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class PatchFile private constructor(
    private val supplier: () -> InputStream,
    private val requiresFurtherProcessing: Boolean
) {

    @Throws(IOException::class)
    internal fun openStream(): InputStream {
        return supplier()
    }

    internal fun requiresFurtherProcessing(): Boolean {
        return requiresFurtherProcessing
    }

    companion object {

        fun from(string: String): PatchFile {
            return PatchFile({ ByteArrayInputStream(string.toByteArray(StandardCharsets.UTF_8)) }, false)
        }

        fun from(data: ByteArray): PatchFile {
            return PatchFile({ ByteArrayInputStream(data) }, false)
        }

        fun from(file: File): PatchFile {
            return PatchFile({ FileInputStream(file) }, true)
        }
    }

}
