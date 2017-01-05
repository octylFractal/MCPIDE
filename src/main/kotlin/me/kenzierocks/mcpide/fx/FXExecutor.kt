package me.kenzierocks.mcpide.fx

import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.sun.javafx.application.PlatformImpl
import javafx.application.Platform
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Implements ExecutorService by running the command on the JavaFX Application Thread.
 * The command will not run until the next tick of the the thread, as it is implemented by {@link Platform#runLater}.
 */
object FXExecutor : AbstractExecutorService() {

    private val terminationFuture = CompletableFuture<Unit>()

    init {
        PlatformImpl.addListener(object : PlatformImpl.FinishListener {
            override fun idle(implicitExit: Boolean) {
            }

            override fun exitCalled() {
                terminationFuture.complete(Unit)
            }
        })
    }

    private class Task(val runnable: Runnable) : Runnable {

        @Volatile
        private var executingThread: Thread? = null

        init {
            unfinishedTasks.add(this)
        }

        override fun run() {
            executingThread = Thread.currentThread()
            try {
                runnable.run()
            } finally {
                // unset executing thread to prevent interrupting other things
                executingThread = null
                unfinishedTasks.remove(this)
                // clear interrupt after we're gone
                Thread.interrupted()
            }
        }

        fun interrupt() {
            executingThread?.interrupt()
        }

    }

    private val shutdownLock = ReentrantReadWriteLock()
    private var shutdown = false
    private val unfinishedTasks = Sets.newConcurrentHashSet<Task>()
    @Volatile
    private var allTasksCompleted = false

    init {
        // if Platform.exit is called without shutdown being called, still do shutdown
        terminationFuture.whenComplete { unit, throwable -> shutdownLock.write { shutdown = true } }
    }

    override fun isTerminated(): Boolean = terminationFuture.isDone && allTasksCompleted

    override fun execute(command: Runnable) {
        shutdownLock.read {
            if (isShutdown) throw RejectedExecutionException("Platform is exited")
            // lock around submit to prevent adding tasks when we're not allowed to
            Platform.runLater(Task(command))
        }
    }

    override fun shutdown() {
        shutdownLock.write {
            shutdown = true
        }
        // don't need to lock over Platform.exit -- could cause deadlock
        // TODO should we really just call Platform.exit?
        Platform.exit()
    }

    override fun shutdownNow(): List<Runnable> {
        // use write lock to prevent other threads submitting tasks while we collect
        val unfinishedCopy = shutdownLock.write {
            shutdown = true
            return@write ImmutableList.copyOf(unfinishedTasks)
        }
        allTasksCompleted = unfinishedCopy.isEmpty()
        if (!allTasksCompleted) {
            // interrupt all tasks before shutting down
            unfinishedCopy.forEach { it.interrupt() }
        }
        shutdown()
        return unfinishedCopy
    }

    override fun isShutdown(): Boolean = shutdown

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        try {
            terminationFuture.get(timeout, unit)
        } catch (timeoutException: TimeoutException) {
            return false
        }
        return true
    }

}