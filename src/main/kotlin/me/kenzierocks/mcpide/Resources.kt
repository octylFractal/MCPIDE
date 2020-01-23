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

import javafx.scene.image.Image
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Resources @Inject constructor() {

    fun loadIcon(location: String): Image {
        return Image(ResourceUrl(location).toExternalForm(), true)
    }

    val syncIcon = loadIcon("font-awesome/sync-alt-solid.png")
    val fileIcon = loadIcon("font-awesome/file-regular.png")
    val folderIcon = loadIcon("font-awesome/folder-regular.png")
    val folderOpenIcon = loadIcon("font-awesome/folder-open-regular.png")
    val applicationIcons = generateAppIcons(loadIcon("icon.png"))

}

/**
 * Helper for getting URLs from the MCPIDE resources root.
 */
object ResourceUrl {
    operator fun invoke(location: String): URL {
        return javaClass.getResource(location)
            ?: throw IllegalArgumentException("No resource at $location")
    }
}

private fun generateAppIcons(appIcon: Image): List<Image> {
    return listOf(16, 32, 64, 128, 256).map { size ->
        Image(appIcon.url, size.toDouble(), 0.0, true, false)
    }
}
