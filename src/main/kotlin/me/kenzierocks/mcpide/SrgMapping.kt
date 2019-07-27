package me.kenzierocks.mcpide

import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("srgName", "newName")
data class SrgMapping(
    val srgName: String,
    val newName: String
) {
    val type = srgName.detectSrgType()
        ?: throw IllegalArgumentException("SRG name did not contain type prefix")
}

enum class SrgType(val prefix: String) {
    FUNCTION("func"),
    FIELD("field"),
    PARAMTER("p")
}

// match TYPE_, to prevent other misc. matches
private val SRG_TYPE_REGEX = Regex(
    "(" +
        SrgType.values().joinToString(separator = "|", transform = { it.prefix }) +
        ")_"
)
private val SRG_TYPE_MAP = SrgType.values().associateBy { it.prefix }

fun String.detectSrgType(): SrgType? {
    val type = SRG_TYPE_REGEX.find(this) ?: return null
    return SRG_TYPE_MAP.getValue(type.groupValues[1])
}
