package me.kenzierocks.mcpide

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import me.kenzierocks.mcpide.controller.MainController
import me.kenzierocks.mcpide.controller.ProjectInitController
import java.net.URL

data class LoadedParent<T : Parent, C>(val parent: T, val controller: C)

class FxmlFiles(
    private val fxmlLoader: (URL) -> FXMLLoader
) {
    private inline fun <reified T : Parent, reified C> load(location: String): LoadedParent<T, C> {
        val loader = fxmlLoader(FxmlRefClass.relativeUrl(location))
        // Enforce generics now, to prevent CCE later
        val parent: T = T::class.java.cast(loader.load())
        val controller: C = C::class.java.cast(loader.getController())
        return LoadedParent(parent, controller)
    }

    fun main() = load<Parent, MainController>("Main.fxml")
    fun projectInit() = load<Parent, ProjectInitController>("ProjectInit.fxml")
}

private object FxmlRefClass {
    fun relativeUrl(location: String): URL {
        return javaClass.getResource(location) ?: throw IllegalArgumentException("No resource at $location")
    }
}
