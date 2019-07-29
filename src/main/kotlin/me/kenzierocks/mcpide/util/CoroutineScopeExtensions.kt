package me.kenzierocks.mcpide.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext

suspend fun <R> withChildContext(coroutineScope: CoroutineScope, block: suspend CoroutineScope.() -> R): R {
    return withContext(coroutineScope.newCoroutineContext(EmptyCoroutineContext), block)
}