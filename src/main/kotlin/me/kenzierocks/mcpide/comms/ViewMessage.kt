package me.kenzierocks.mcpide.comms

import me.kenzierocks.mcpide.SrgMapping
import java.nio.file.Path

sealed class ViewMessage

data class OpenInFileTree(val directory: Path) : ViewMessage()

data class UpdateMappings(val mappings: List<SrgMapping>, val merge: Boolean = false) : ViewMessage()

fun MutableMap<String, SrgMapping>.apply(update: UpdateMappings) {
    if (!update.merge) {
        clear()
    }
    update.mappings.associateByTo(this) { it.srgName }
}
