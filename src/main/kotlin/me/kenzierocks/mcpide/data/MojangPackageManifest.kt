package me.kenzierocks.mcpide.data

data class MojangPackageManifest(
    val downloads: MojangPackageDownloads
)

data class MojangPackageDownloads(
    val client: MojangPackageDownload
)

data class MojangPackageDownload(
    val url: String
)
