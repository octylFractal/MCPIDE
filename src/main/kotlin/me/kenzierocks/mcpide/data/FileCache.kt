package me.kenzierocks.mcpide.data

import kotlinx.io.errors.IOException
import java.nio.file.Files
import java.nio.file.Path

private val SPECIAL_DIRS = setOf("..", ".")

/**
 * Data cache for global MCPIDE data.
 */
class FileCache(
    val directory: Path
) {

    private fun Path.resolveAndCreate(dir: String): Path {
        val newDir = resolve(dir)
        try {
            Files.createDirectory(newDir)
        } catch (ignored: IOException) {
            require(Files.isDirectory(newDir)) {
                "$newDir already exists but is not a directory."
            }
        }
        return newDir
    }

    val mcpideCacheDirectory = directory.resolveAndCreate("files-1")
    val okHttpCacheDirectory = directory.resolveAndCreate("okhttp-4")

    fun cacheEntry(name: String): Path {
        require(!name.contains('/')) { "Name cannot contain slashes." }
        require(name !in SPECIAL_DIRS) { "Name cannot be '..' or '.'."}
        return mcpideCacheDirectory.resolve(name)
    }

}