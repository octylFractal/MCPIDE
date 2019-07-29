package me.kenzierocks.mcpide.util

import kotlinx.io.errors.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed class CallResult(val call: Call) {
    class Success(call: Call, val response: Response) : CallResult(call) {
        override fun throwIfFailed() = response
    }

    class Failure(call: Call, val error: IOException) : CallResult(call) {
        override fun throwIfFailed(): Nothing = throw error
    }

    abstract fun throwIfFailed(): Response
}

/**
 * Variant of [Call.enqueue] that integrates with suspend mechanisms.
 */
suspend fun Call.enqueueSuspend(): CallResult {
    return suspendCoroutine {
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                it.resume(CallResult.Failure(call, e))
            }

            override fun onResponse(call: Call, response: Response) {
                it.resume(CallResult.Success(call, response))
            }
        })
    }
}

private val DEFAULT_OK_CODES = (200..299)::contains

class BadStatusCodeException(
    val response: Response,
    val code: Int
) : RuntimeException("Bad status code: $code")

fun Response.checkStatusCode(isCodeOkay: (Int) -> Boolean = DEFAULT_OK_CODES) : Response {
    if (!isCodeOkay(code)) {
        throw BadStatusCodeException(this, code)
    }
    return this
}
