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
