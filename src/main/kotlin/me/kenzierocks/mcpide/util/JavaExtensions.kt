package me.kenzierocks.mcpide.util

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Variant of [Process.waitFor] that suspends instead.
 */
suspend fun Process.suspendFor() : Int {
    return suspendCoroutine { cont ->
        onExit().whenCompleteAsync {value: Process?, ex: Throwable? ->
            when {
                value != null -> cont.resume(value.exitValue())
                ex != null -> cont.resumeWithException(ex)
                else -> cont.resumeWithException(IllegalStateException("Both value and exception are null."))
            }
        }
    }
}
