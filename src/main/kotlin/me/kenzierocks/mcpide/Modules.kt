package me.kenzierocks.mcpide

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.kenzierocks.mcpide.comms.comms
import org.koin.dsl.module

private val COMMS = comms()

val viewModule = module {
    single(View) { CoroutineScope(Dispatchers.Main + CoroutineName("View") + SupervisorJob()) }
    single { COMMS.first }
    single { Controller(get(), get(), workerScope = get(App), viewScope = get(View)) }
    single { ViewEventLoop(get(View), get(), get()) }
}

val modelModule = module {
    single { COMMS.second }
    single { ModelProcessing(get(App), get()) }
}
