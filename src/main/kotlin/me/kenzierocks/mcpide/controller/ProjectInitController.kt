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
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.stage.FileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.kenzierocks.mcpide.MCPIDE
import me.kenzierocks.mcpide.data.MavenMetadata
import me.kenzierocks.mcpide.resolver.RemoteRepositories
import me.kenzierocks.mcpide.util.setPrefSizeFromContent
import me.kenzierocks.mcpide.util.showAndSuspend
import me.kenzierocks.mcpide.util.sortedByVersion
import me.kenzierocks.mcpide.util.withChildContext
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.metadata.DefaultMetadata
import org.eclipse.aether.metadata.Metadata
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.MetadataRequest
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

private const val FORGE_MAVEN = "https://files.minecraftforge.net/maven"

class ProjectInitController(
    private val app: MCPIDE,
    private val repositorySystem: RepositorySystem,
    private val repositorySystemSession: RepositorySystemSession,
    private val remoteRepositories: RemoteRepositories,
    private val xmlMapper: XmlMapper,
    private val workerScope: CoroutineScope,
    private val viewScope: CoroutineScope
) {

    @FXML
    private lateinit var mcpZipText: TextField
    @FXML
    private lateinit var mcpConfigText: TextField

    val mcpZipPath get() = Paths.get(mcpZipText.text)
    val mcpConfigPath get() = Paths.get(mcpConfigText.text)

    private fun TextField.selectLocalFiles(title: String, extensions: List<FileChooser.ExtensionFilter>) {
        val fileChooser = FileChooser()
        fileChooser.title = title
        fileChooser.extensionFilters.addAll(extensions)
        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("All Files", "*.*"))
        fileChooser.selectedExtensionFilter = fileChooser.extensionFilters.first()
        val selected = fileChooser.showOpenDialog(app.stage) ?: return
        text = selected.absolutePath
    }

    private fun TextField.selectMavenFile(group: String,
                                          name: String,
                                          title: String,
                                          header: String) {
        workerScope.launch {
            val versionList = ListView(FXCollections.observableList(
                loadMavenVersions(group, name)
            ))
            val ver = withChildContext(viewScope) {
                val dialog = Alert(Alert.AlertType.CONFIRMATION)
                dialog.isResizable = true
                dialog.title = title
                dialog.headerText = header
                dialog.dialogPane.content = versionList
                dialog.dialogPane.setPrefSizeFromContent()

                when (dialog.showAndSuspend()) {
                    null, ButtonType.CANCEL -> null
                    else -> versionList.selectionModel.selectedItem
                }
            } ?: return@launch // no version selected
            val path = downloadArtifact(DefaultArtifact(
                group, name, "zip", ver
            ))
            withChildContext(viewScope) {
                text = path.toAbsolutePath().toString()
            }
        }
    }

    private fun loadMavenVersions(group: String, name: String): List<String> {
        return repositorySystem.resolveMetadata(repositorySystemSession,
            remoteRepositories.map {
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
        val result = repositorySystem.resolveArtifact(repositorySystemSession,
            ArtifactRequest(artifact, remoteRepositories, null))
        if (!result.isResolved) {
            val ex = RuntimeException("Failed to resolve $artifact")
            result.exceptions.forEach(ex::addSuppressed)
            throw ex
        }
        return result.artifact.file.toPath()
    }

    fun selectMcpForgeMaven() {
        mcpZipText.selectMavenFile(
            "de.oceanlabs.mcp", "mcp_snapshot",
            title = "MCP Release Selection",
            header = "Choose an MCP release"
        )
    }

    fun selectMcpLocalFiles() {
        mcpZipText.selectLocalFiles(
            title = "Select an MCP ZIP",
            extensions = listOf(FileChooser.ExtensionFilter("ZIP Files", "*.zip"))
        )
    }

    fun selectMcpConfigForgeMaven() {
        mcpZipText.selectMavenFile(
            "de.oceanlabs.mcp", "mcp_config",
            title = "MCP Config Release Selection",
            header = "Choose an MCP Config release"
        )
    }

    fun selectMcpConfigLocalFiles() {
        mcpConfigText.selectLocalFiles(
            title = "Select an MCP Config ZIP",
            extensions = listOf(FileChooser.ExtensionFilter("ZIP Files", "*.zip"))
        )
    }

}