package me.kenzierocks.mcpide.util

private val GRADLE_COORDS = Regex("([^: ]+)(:)", RegexOption.COMMENTS)

/**
 * Parse Gradle's `group:name:version:classifier@extension` into Maven's
 * `group:name:extension:classifier:version`
 *
 * Extension defaults to `jar` if it is unspecified and classifier is specified.
 */
fun gradleCoordsToMaven(gradle: String) : String {
    val extParts = gradle.split('@', limit = 2)
    val extension = extParts.takeIf { it.size == 2 }?.first()
    val rest = extParts.last()
    val parts = rest.split(':', limit = 4)
    val iter = parts.iterator()
    val group = iter.takeIf { it.hasNext() }?.next()
    val name = iter.takeIf { it.hasNext() }?.next()
    val version = iter.takeIf { it.hasNext() }?.next()
    val classifier = iter.takeIf { it.hasNext() }?.next()
    return listOfNotNull(group, name, extension ?: classifier?.let { "jar" }, classifier, version)
        .joinToString(":")
}