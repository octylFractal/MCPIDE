package me.kenzierocks.releasefiles

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

fun Path.copyIntoDir(dirTo: Path): Path {
    val pathTo = dirTo.resolve(fileName)
    Files.copy(this, pathTo, StandardCopyOption.REPLACE_EXISTING)
    return pathTo
}
