package me.kenzierocks.mcpide

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.comms.ViewComms

class ViewEventLoop(
    private val scope: CoroutineScope,
    private val viewComms: ViewComms,
    private val controller: Controller
) {
    fun start() {
        scope.launch {
            while (!viewComms.viewChannel.isClosedForReceive) {
                val msg = viewComms.viewChannel.receive()
                controller.handleMessage(msg)
            }
        }
    }
}