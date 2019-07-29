package me.kenzierocks.mcpide.data

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/**
 * Representation of `maven-metadata.xml`.
 */
@JacksonXmlRootElement(localName = "metadata")
data class MavenMetadata(
    val groupId: String,
    val artifactId: String,
    val versioning: Versioning
)

@JacksonXmlRootElement(localName = "versioning")
class Versioning {
    @get:JacksonXmlElementWrapper(localName = "versions")
    lateinit var version: List<String>
    lateinit var release: String
    lateinit var lastUpdated: String
}
