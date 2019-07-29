package me.kenzierocks.mcpide.data

import java.time.ZonedDateTime

data class MojangVersionManifest(
    val versions: List<MojangVersion>
)

data class MojangVersion(
    val id: String,
    val url: String,
    val time: ZonedDateTime
)

private val TIME_COMPARATOR = Comparator.comparing { it: MojangVersion ->
    it.time
}.reversed()

fun List<MojangVersion>.sortedByTime(): List<MojangVersion> {
    return sortedWith(TIME_COMPARATOR)
}
