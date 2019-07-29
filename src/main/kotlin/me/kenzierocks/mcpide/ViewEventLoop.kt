package me.kenzierocks.mcpide

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.comms.ViewComms
import me.kenzierocks.mcpide.controller.MainController

class ViewEventLoop(
    private val scope: CoroutineScope,
    private val viewComms: ViewComms,
    private val mainController: MainController
) {
    fun start() {
        scope.launch {
            while (!viewComms.viewChannel.isClosedForReceive) {
                val msg = viewComms.viewChannel.receive()
                mainController.handleMessage(msg)
            }
        }
    }
}