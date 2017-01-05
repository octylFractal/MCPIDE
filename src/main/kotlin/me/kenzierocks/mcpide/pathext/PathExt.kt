package me.kenzierocks.mcpide.pathext

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

// Usage: path / (str|path) -> path
operator fun Path.div(append: Path): Path = this.resolve(append)

operator fun Path.div(append: String): Path = this.resolve(append)

val Path.name: String get() = this.fileName.toString()

fun Path.touch() {
    try {
        Files.createFile(this)
    } catch (e: FileAlreadyExistsException) {
    }
}

fun Path.write(str: String): Path = Files.write(this, str.toByteArray())