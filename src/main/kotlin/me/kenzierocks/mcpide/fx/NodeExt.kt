package me.kenzierocks.mcpide.fx

import javafx.scene.Node
import javafx.scene.Parent

fun <T : Node> Parent.lookupCast(selector: String): T {
    val node: Node = this.lookup(selector) ?: throw IllegalStateException("$selector not found.")
    return node.autoCast()
}

fun <T : Node> Node.autoCast(): T {
    @Suppress("UNCHECKED_CAST")
    return this as T
}

data class Coordinate(val x: Double, val y: Double)

val Node.globalLayoutCoords: Coordinate
    get() {
        val p2d = localToScreen(layoutX, layoutY)
        return Coordinate(p2d.x, p2d.y)
    }