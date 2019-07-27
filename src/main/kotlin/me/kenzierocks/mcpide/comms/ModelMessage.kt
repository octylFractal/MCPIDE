package me.kenzierocks.mcpide.comms

import java.nio.file.Path

sealed class ModelMessage

data class LoadProject(val projectDirectory: Path) : ModelMessage()

object ExportMappings : ModelMessage()
