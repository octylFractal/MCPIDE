package me.kenzierocks.mcpide

enum class ReplacementType(val namePrefix: String?, val botCommand: String?) {
    FIELD("field", "sf"), METHOD("func", "sm"), PARAMETER("p", "sp"), CUSTOM(null, null);

    val namePrefixUnderscore = namePrefix + "_"
}

val REPLACEMENT_TYPE_SEPARATOR = ";\u0001 replacementType="
inline fun Config.forEachReplacementType(body: ((String, String, ReplacementType) -> Unit)) {
    forEach { e ->
        val (k, combinedV) = e
        val split = combinedV.split(
            delimiters = REPLACEMENT_TYPE_SEPARATOR,
            limit = 2
        )
        val v = split[0]
        val replacementType =
            if (split.size >= 2) ReplacementType.valueOf(split.last())
            else ReplacementType.CUSTOM
        body(k, v, replacementType)
    }
}