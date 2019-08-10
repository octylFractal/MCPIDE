package me.kenzierocks.mcpide.util.diff

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class PatchFile private constructor(
    private val supplier: () -> InputStream,
    private val requiresFurtherProcessing: Boolean
) {

    @Throws(IOException::class)
    internal fun openStream(): InputStream {
        return supplier()
    }

    internal fun requiresFurtherProcessing(): Boolean {
        return requiresFurtherProcessing
    }

    companion object {

        fun from(string: String): PatchFile {
            return PatchFile({ ByteArrayInputStream(string.toByteArray(StandardCharsets.UTF_8)) }, false)
        }

        fun from(data: ByteArray): PatchFile {
            return PatchFile({ ByteArrayInputStream(data) }, false)
        }

        fun from(file: File): PatchFile {
            return PatchFile({ FileInputStream(file) }, true)
        }
    }

}
