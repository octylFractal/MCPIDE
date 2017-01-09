package me.kenzierocks.releasefiles

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@CompileStatic
class Methods {
    static Path copyIntoDir(Path from, Path dirTo) {
        Path pathTo = dirTo.resolve(from.fileName)
        Files.copy(from, pathTo, StandardCopyOption.REPLACE_EXISTING)
        return pathTo
    }
}
