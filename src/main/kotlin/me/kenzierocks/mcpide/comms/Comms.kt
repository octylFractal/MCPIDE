package me.kenzierocks.mcpide.comms

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

fun comms(): Pair<ViewComms, ModelComms> {
    val mc = Channel<ModelMessage>(100)
    val vc = Channel<ViewMessage>(100)
    return ViewComms(mc, vc) to ModelComms(mc, vc)
}

class ViewComms(
    val modelChannel: SendChannel<ModelMessage>,
    val viewChannel: ReceiveChannel<ViewMessage>
)

class ModelComms(
    val modelChannel: ReceiveChannel<ModelMessage>,
    val viewChannel: SendChannel<ViewMessage>
)
