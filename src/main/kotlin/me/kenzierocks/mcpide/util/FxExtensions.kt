package me.kenzierocks.mcpide.util

import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.scene.control.Dialog
import javafx.scene.control.DialogPane
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Set the preferred size of the dialog pane to the preferred size of [DialogPane.content].
 */
fun DialogPane.setPrefSizeFromContent() {
    scene.window.sizeToScene()
    // we need to do this again a bit later to properly size things.
    scene.window.onShown = EventHandler {
        CoroutineScope(Dispatchers.Main + CoroutineName("PrefSizeUpdate")).launch {
            delay(100)
            scene.window.sizeToScene()
        }
    }
}

/**
 * [Dialog.showAndWait], but instead of using a nested event-loop, suspends.
 */
suspend fun <R> Dialog<R>.showAndSuspend(): R? {
    show()
    return suspendCoroutine { cont ->
        resultProperty().addListener(object : ChangeListener<R> {
            override fun changed(observable: ObservableValue<out R>?, oldValue: R, newValue: R) {
                cont.resume(newValue)
                resultProperty().removeListener(this)
            }
        })
    }
}
