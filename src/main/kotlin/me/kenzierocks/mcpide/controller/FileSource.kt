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

package me.kenzierocks.mcpide.controller

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import javafx.collections.FXCollections
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.stage.FileChooser
import javafx.stage.Window
import javafx.util.Callback
import kotlinx.coroutines.CoroutineScope
import me.kenzierocks.mcpide.data.MavenMetadata
import me.kenzierocks.mcpide.resolver.MavenAccess
import me.kenzierocks.mcpide.util.setPrefSizeFromContent
import me.kenzierocks.mcpide.util.showAndSuspend
import me.kenzierocks.mcpide.util.sortedByVersion
import me.kenzierocks.mcpide.util.withChildContext
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.metadata.DefaultMetadata
import org.eclipse.aether.metadata.Metadata
import org.eclipse.aether.resolution.MetadataRequest
import java.io.File
import java.nio.file.Path

/**
 * File source for [FileAskDialogController] to build the "Select" menu upon.
 */
interface FileSource {

    val description: String

    /**
     * Retrieve the file. This function is called on the view scope.
     *
     * @param primaryStage the primary stage, for providing owner to dialogs
     */
    suspend fun retrieveFile(primaryStage: Window): Path?

}

data class ExtensionFilter(
    val description: String,
    val extensions: Set<String>
)

/**
 * Source from local file system.
 *
 * @param title the title of the file chooser dialog
 * @param extensionFilters extension filters to offer
 */
class LocalFileSource(
    private val title: String,
    private val extensionFilters: Set<ExtensionFilter>
) : FileSource {
    override val description = "Local Files"

    override suspend fun retrieveFile(primaryStage: Window): Path? {
        val fileChooser = FileChooser()
        fileChooser.title = title
        fileChooser.extensionFilters.addAll(extensionFilters.map {
            FileChooser.ExtensionFilter(it.description, it.extensions.toList())
        })
        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("All Files", "*.*"))
        fileChooser.selectedExtensionFilter = fileChooser.extensionFilters.first()
        return fileChooser.showOpenDialog(primaryStage)?.toPath()
    }

}


class MavenSource(
    override val description: String,
    private val group: String,
    private val name: String,
    private val dialogTitle: String,
    private val header: String,
    private val xmlMapper: XmlMapper,
    private val mavenAccess: MavenAccess,
    private val workerScope: CoroutineScope
) : FileSource {
    override suspend fun retrieveFile(primaryStage: Window): Path? {
        val versionList = withChildContext(workerScope) {
            ListView(FXCollections.observableList(
                loadMavenVersions(group, name)
            ))
        }
        val dialog = Alert(Alert.AlertType.CONFIRMATION)
        dialog.initOwner(primaryStage)
        dialog.isResizable = true
        dialog.title = dialogTitle
        dialog.headerText = header
        dialog.dialogPane.content = versionList
        dialog.dialogPane.setPrefSizeFromContent()

        // Handle double-click as "open this one"
        versionList.cellFactory = Callback {
            object : ListCell<String>() {
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)

                    if (empty) {
                        text = null
                        onMouseClicked = null
                    } else {
                        text = item
                        setOnMouseClicked { e ->
                            if (e.clickCount == 2 && isSelected) {
                                dialog.result = ButtonType.OK
                                dialog.close()
                            }
                        }
                    }
                }
            }
        }

        val ver = when (dialog.showAndSuspend()) {
            null, ButtonType.CANCEL -> null
            else -> versionList.selectionModel.selectedItem
        } ?: return null // no version selected
        return downloadArtifact(DefaultArtifact(
            group, name, "zip", ver
        ))
    }

    private fun loadMavenVersions(group: String, name: String): List<String> {
        return mavenAccess.system.resolveMetadata(mavenAccess.session,
            mavenAccess.repositories.map {
                MetadataRequest(
                    DefaultMetadata(group, name, "maven-metadata.xml", Metadata.Nature.RELEASE),
                    it,
                    null
                )
            })
            .filter { it.isResolved }
            .flatMap { readMetadataVersions(it.metadata.file) }
            .sortedByVersion()
            .asReversed()
    }

    private fun readMetadataVersions(file: File): List<String> {
        val metadata = xmlMapper.readValue<MavenMetadata>(file)
        return metadata.versioning.version
    }

    private fun downloadArtifact(artifact: Artifact): Path {
        val result = mavenAccess.resolveArtifact(artifact)
        if (!result.isResolved) {
            val ex = RuntimeException("Failed to resolve $artifact")
            result.exceptions.forEach(ex::addSuppressed)
            throw ex
        }
        return result.artifact.file.toPath()
    }
}
