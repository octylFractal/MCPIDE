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

import kotlinx.io.errors.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed class CallResult {
    class Success(val response: Response) : CallResult() {
        override fun throwIfFailed() = response
    }

    class Failure(private val error: IOException) : CallResult() {
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
                it.resume(CallResult.Failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                it.resume(CallResult.Success(response))
            }
        })
    }
}

private val DEFAULT_OK_CODES = (200..299)::contains

class BadStatusCodeException(
    code: Int
) : RuntimeException("Bad status code: $code")

fun Response.checkStatusCode(isCodeOkay: (Int) -> Boolean = DEFAULT_OK_CODES): Response {
    if (!isCodeOkay(code)) {
        throw BadStatusCodeException(code)
    }
    return this
}

// IntelliJ has trouble resolving the bundled extension.
fun String.toHttpUrl() = with(HttpUrl.Companion) { toHttpUrl() }
