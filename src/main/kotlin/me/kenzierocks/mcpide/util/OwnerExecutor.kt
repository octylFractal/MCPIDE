package me.kenzierocks.mcpide.util

import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext

class OwnerExecutor(val executor: ExecutorService) : CoroutineContext.Element {
    override val key = Key

    companion object Key : CoroutineContext.Key<OwnerExecutor>
}
