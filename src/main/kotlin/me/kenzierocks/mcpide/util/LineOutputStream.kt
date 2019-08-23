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

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

typealias LineConsumer = (line: String) -> Unit

/**
 * [OutputStream] implementation that forwards to a [LineConsumer].
 */
class LineOutputStream(
    private val lineConsumer: LineConsumer
) : OutputStream() {

    private class ExposedBAOS : ByteArrayOutputStream() {
        val buffer: ByteBuffer
            get() = ByteBuffer.wrap(buf, 0, count)
    }

    private val buffer = ExposedBAOS()

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        var nextStart = 0
        for (i in 0 until len) {
            if (b[i + off] == '\n'.toByte()) {
                buffer.write(b, nextStart + off, i - nextStart)
                cutLine()
                nextStart = i + 1
            }
        }
        buffer.write(b, nextStart + off, len - nextStart)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: Int) {
        if (b == '\n'.toInt()) {
            cutLine()
            return
        }
        buffer.write(b)
    }

    private fun cutLine() {
        val lineBytes = buffer.buffer
        val line = StandardCharsets.UTF_8.decode(lineBytes).toString()
        lineConsumer(line)
        buffer.reset()
    }

    @Throws(IOException::class)
    override fun close() {
        if (buffer.size() > 0) {
            cutLine()
        }
    }
}
