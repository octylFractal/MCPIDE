package me.kenzierocks.mcpide.project

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

interface ProjectAction<out R> {
    suspend operator fun Project.invoke(): R

    val response: CompletableDeferred<out R>?
        get() = null
}

class ProjectWorker(
    private val project: Project,
    val channel: SendChannel<ProjectAction<*>>
) {
    init {
        channel.invokeOnClose {
            project.close()
        }
    }

    /**
     * Send a request to read [block] from the project, and suspend until satisfied.
     */
    suspend inline fun <R> read(crossinline block: suspend Project.() -> R): R {
        val deferred = CompletableDeferred<R>(coroutineContext[Job])
        channel.send(object : ProjectAction<R> {
            override suspend fun Project.invoke() = block()
            override val response = deferred
        })
        return deferred.await()
    }

    /**
     * Send a write to the worker. To suspend for the result, set [suspendFor] to `true`.
     */
    suspend inline fun write(suspendFor: Boolean = false, crossinline block: suspend Project.() -> Unit) {
        if (suspendFor) {
            return read(block)
        }
        channel.send(object : ProjectAction<Unit> {
            override suspend fun Project.invoke() = block()
        })
    }
}

fun CoroutineScope.projectWorker(project: Project): ProjectWorker {
    return ProjectWorker(project, actor(capacity = 10) {
        while (isActive) {
            val action = channel.receiveOrNull() ?: break
            with(action) {
                try {
                    val r = project()
                    @Suppress("UNCHECKED_CAST")
                    (response as? CompletableDeferred<Any?>)?.complete(r)
                } catch (t: Throwable) {
                    when (val r = response) {
                        null -> when (val ceh = coroutineContext[CoroutineExceptionHandler]) {
                            null -> t.printStackTrace()
                            else -> ceh.handleException(coroutineContext, t)
                        }
                        else -> r.completeExceptionally(t)
                    }
                }
            }
        }
    })
}