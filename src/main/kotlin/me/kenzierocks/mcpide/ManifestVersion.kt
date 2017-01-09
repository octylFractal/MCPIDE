package me.kenzierocks.mcpide

private val DEFAULT_VERSION = "DEV"

class ManifestVersion {
    companion object {
        fun getProjectVersion(): String {
            return ManifestVersion::class.java.`package`?.implementationVersion ?: DEFAULT_VERSION
        }
    }
}