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

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import me.kenzierocks.mcpide.handles.get

fun main(args: Array<String>) {
    Application.launch(MCPIDE::class.java, *args)
}

class MCPIDE : Application() {

    companion object {
        val TITLE = "MCPIDE"
        private var INSTANCE_NULLABLE: MCPIDE? = null

        val INSTANCE: MCPIDE
            get() = this.INSTANCE_NULLABLE!!

        // Class.resolveName(Ljava.lang.String;)Ljava.lang.String; -- bound to MCPIDE.class
        private val HANDLE_RESOLVE_NAME =
            Class::class["resolveName"]
                .parameters(String::class)
                .returns(String::class)
                .build()
                .bind(MCPIDE::class.java)

        private fun resolveName(resource: String): String {
            return HANDLE_RESOLVE_NAME.call(resource)!!
        }

        fun getResource(resource: String) = MCPIDE::class.java.getResource(resource)
            ?: throw IllegalStateException("Missing resource: ${resolveName(resource)}")

        fun <R : Parent> loadParent(resource: String): R {
            return FXMLLoader.load(getResource(resource))
        }

        fun <R : Parent> parentLoader(resource: String): () -> R {
            val loader = FXMLLoader(getResource(resource))
            return { loader.load<R>() }
        }
    }

    init {
        Companion.INSTANCE_NULLABLE = this
        // attempt hack to set name
        setInternalApplicationName()
    }

    lateinit var stage: Stage

    override fun start(stage: Stage) {
        this.stage = stage
        val parent: Parent = MCPIDE.loadParent("Main.fxml")
        stage.scene = Scene(parent)
        stage.title = TITLE
        stage.show()
        stage.centerOnScreen()
        stage.isMaximized = true
    }

}

private fun setInternalApplicationName() {
    // Set the name of the application internally, so the macOS menus have the correct name
    val comSunGlassUiApplicationClassName = "com.sun.glass.ui.Application"
    try {
        val comSunGlassUiApplication = Class.forName(comSunGlassUiApplicationClassName)
        val applicationGetApplication = comSunGlassUiApplication.getDeclaredMethod("GetApplication")
        val applicationSetName = comSunGlassUiApplication.getDeclaredMethod("setName", String::class.java)
        applicationSetName.invoke(applicationGetApplication.invoke(null), MCPIDE.TITLE)
    } catch (e: Exception) {
        // ignore -- this is fine
        System.err.println("""
Warning: Unable to set Application name
This may be because your JDK does not have $comSunGlassUiApplicationClassName.
This is not necessarily a problem, but you may experience weird UI issues.
        """)
        e.printStackTrace()
    }
}
