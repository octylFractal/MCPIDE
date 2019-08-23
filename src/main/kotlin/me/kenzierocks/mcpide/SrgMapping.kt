/*
 * This file is part of MCPIDE, licensed under the MIT License (MIT).
 *
 * Copyright (c) kenzierocks <https://kenzierocks.me>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package me.kenzierocks.mcpide

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("srgName", "newName", "side", "desc")
data class SrgMapping(
    val srgName: String,
    val newName: String,
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
    val side: Side,
    val desc: String? = null
) {
    @JsonIgnore
    val type = srgName.detectSrgType()
        ?: throw IllegalArgumentException("SRG name did not contain type prefix")

    fun toCommand() = "!${type.commandPrefix} $srgName $newName" + when (desc) {
        null -> ""
        else -> " $desc"
    }
}

@Suppress("unused")
enum class Side {
    CLIENT,
    SERVER,
    JOINED
}

@Suppress("unused")
enum class SrgType(
    val prefix: String,
    val commandPrefix: String
) {
    METHOD("func", "sm"),
    FIELD("field", "sf"),
    PARAMETER("p", "sp")
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

/**
 * Matches valid SRG names.
 */
val SRG_REGEX = Regex(
    "(func|field)_\\d+_[a-zA-Z]+_?|p_i?\\d+_\\d+_"
)
