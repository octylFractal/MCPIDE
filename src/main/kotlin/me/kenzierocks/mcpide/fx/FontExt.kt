package me.kenzierocks.mcpide.fx

import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight

fun Font.withFamily(family: String): Font {
    val weight: FontWeight? = FontWeight.findByName(name)
    val posture: FontPosture? = FontPosture.findByName(name)
    return Font.font(family, weight, posture, this.size)
}

fun Font.withWeight(weight: FontWeight?): Font {
    val posture: FontPosture? = FontPosture.findByName(name)
    return Font.font(this.family, weight, posture, this.size)
}

fun Font.withPosture(posture: FontPosture?): Font {
    val weight: FontWeight? = FontWeight.findByName(name)
    return Font.font(this.family, weight, posture, this.size)
}

fun Font.withSize(size: Double): Font {
    val weight: FontWeight? = FontWeight.findByName(name)
    val posture: FontPosture? = FontPosture.findByName(name)
    return Font.font(this.family, weight, posture, size)
}