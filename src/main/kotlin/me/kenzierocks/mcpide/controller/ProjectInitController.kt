package me.kenzierocks.mcpide.controller

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.stage.FileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kenzierocks.mcpide.MCPIDE
import me.kenzierocks.mcpide.data.FileCache
import me.kenzierocks.mcpide.data.MavenMetadata
import me.kenzierocks.mcpide.util.checkStatusCode
import me.kenzierocks.mcpide.util.enqueueSuspend
import me.kenzierocks.mcpide.util.setPrefSizeFromContent
import me.kenzierocks.mcpide.util.showAndSuspend
import me.kenzierocks.mcpide.util.withChildContext
import mu.KotlinLogging
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val FORGE_MAVEN = "https://files.minecraftforge.net/maven"

// Allow very old cached responses. No reason to avoid it with Maven.
private val STALE_ALLOWED_CC = CacheControl.Builder()
    .maxStale(365, TimeUnit.DAYS)
    .build()

class ProjectInitController(
    private val app: MCPIDE,
    private val okHttp: OkHttpClient,
    private val xmlMapper: XmlMapper,
    private val fileCache: FileCache,
    private val workerScope: CoroutineScope,
    private val viewScope: CoroutineScope
) {

    private val logger = KotlinLogging.logger { }

    @FXML
    private lateinit var mcpZipText: TextField
    @FXML
    private lateinit var minecraftJarText: TextField

    private fun TextField.selectLocalFiles(title: String, extensions: List<FileChooser.ExtensionFilter>) {
        val fileChooser = FileChooser()
        fileChooser.title = title
        fileChooser.extensionFilters.addAll(extensions)
        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("All Files", "*.*"))
        fileChooser.selectedExtensionFilter = fileChooser.extensionFilters.first()
        val selected = fileChooser.showOpenDialog(app.stage) ?: return
        text = selected.absolutePath
    }

    fun selectMcpForgeMaven(event: ActionEvent) {
        // TODO: add a download progress indicator?
        workerScope.launch {
            val versionList = ListView(FXCollections.observableList(
                loadMavenVersions("/de/oceanlabs/mcp/mcp_snapshot/maven-metadata.xml")
            ))
            val ver = withChildContext(viewScope) {
                val dialog = Alert(Alert.AlertType.CONFIRMATION)
                dialog.title = "MCP Release Selection"
                dialog.headerText = "Choose an MCP release"
                dialog.dialogPane.content = versionList
                dialog.dialogPane.setPrefSizeFromContent()

                when (dialog.showAndSuspend()) {
                    null, ButtonType.CANCEL -> null
                    else -> versionList.selectionModel.selectedItem
                }
            } ?: return@launch // no version selected
            val path = downloadFile(
                "$FORGE_MAVEN/de/oceanlabs/mcp/mcp_snapshot/$ver/mcp_snapshot-$ver.zip"
            )
            withChildContext(viewScope) {
                mcpZipText.text = path.toAbsolutePath().toString()
            }
        }
    }

    private suspend fun downloadFile(url: String): Path {
        val fileName = url.splitToSequence('/').last()
        val response = standardCall(Request.Builder()
            .cacheControl(STALE_ALLOWED_CC)
            .url(url)
            .build())
        val target = fileCache.cacheEntry(fileName)
        withContext(Dispatchers.IO) {
            Files.newOutputStream(target).use { out ->
                response.body!!.byteStream().copyTo(out)
            }
        }
        return target
    }

    private suspend fun loadMavenVersions(path: String): List<String> {
        val mavenMetadata = "$FORGE_MAVEN$path"

        val response = standardCall(Request.Builder()
            .cacheControl(STALE_ALLOWED_CC)
            .url(mavenMetadata)
            .build())
        val body = response.body ?: return listOf()
        val metadata = xmlMapper.readValue<MavenMetadata>(body.charStream())
        return metadata.versioning.version
    }

    private suspend fun standardCall(request: Request) =
        okHttp.newCall(request)
            .enqueueSuspend().throwIfFailed().checkStatusCode()

    fun selectMcpLocalFiles(event: ActionEvent) {
        mcpZipText.selectLocalFiles(
            title = "Select an MCP ZIP",
            extensions = listOf(FileChooser.ExtensionFilter("ZIP Files", "*.zip"))
        )
    }

    fun selectMinecraftForgeMaven(event: ActionEvent) {

    }

    fun selectMinecraftLocalFiles(event: ActionEvent) {
        minecraftJarText.selectLocalFiles(
            title = "Select a Minecraft JAR",
            extensions = listOf(FileChooser.ExtensionFilter("JAR Files", "*.jar"))
        )
    }
}