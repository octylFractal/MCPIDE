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

package me.kenzierocks.mcpide.project

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Path
import java.nio.file.StandardOpenOption

interface ProjectLock : AutoCloseable {
    fun acquire()

    fun release()
}

fun projectLock(directory: Path): ProjectLock = ProjectLockImpl(directory)

private class ProjectLockImpl(
    directory: Path
) : ProjectLock {

    private val channel = directory.resolve(".lock").let { lockFile ->
        FileChannel.open(lockFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    }
    @Volatile
    private var lock: FileLock? = null

    override fun acquire() {
        require(channel.isOpen) { "Lock closed." }
        lock = channel.lock()
    }

    override fun release() {
        val l = requireNotNull(lock) { "Lock not acquired." }
        l.release()
    }

    override fun close() {
        // lock implicitly releases on channel close
        lock = null
        channel.close()
    }

}
