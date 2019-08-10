package me.kenzierocks.mcpide.mcp.function

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kenzierocks.mcpide.mcp.McpContext
import me.kenzierocks.mcpide.mcp.McpFunction
import me.kenzierocks.mcpide.util.computeOutput
import me.kenzierocks.mcpide.util.diff.ContextualPatch
import me.kenzierocks.mcpide.util.diff.PatchFile
import me.kenzierocks.mcpide.util.diff.ZipContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class PatchFunction(
    private val path: String
) : McpFunction {

    private lateinit var patches: Map<String, String>

    override suspend fun initialize(context: McpContext, zip: ZipFile) {
        patches = zip.entries().asSequence().filter {
            !it.isDirectory && it.name.startsWith(path) && it.name.endsWith(".patch")
        }.associate { ze ->
            ze.name.substring(path.length) to withContext(Dispatchers.IO) {
                zip.getInputStream(ze).use { it.reader().readText() }
            }
        }
    }

    override suspend fun invoke(context: McpContext): Path {
        val input = context.arguments.getValue("input") as Path
        val output = context.file("output.jar")

        computeOutput(input, output) {
            Files.createDirectories(output.parent)
            ZipFile(input.toFile()).use { zip ->
                val patchContext = ZipContext(zip)

                var success = true
                patches.forEach { (name, file) ->
                    val patch = ContextualPatch.create(PatchFile.from(file), patchContext)
                    patch.setCanonicalization(access = true, whitespace = false)
                    patch.maxFuzz = 0

                    context.logger.info("Applying patch: $name")
                    patch.patch(false)
                        .filterNot { it.status.success }
                        .forEach { report ->
                            success = false
                            report.hunkReports.forEach { hunk ->
                                if (hunk.hasFailed()) {
                                    when (hunk.failure) {
                                        null -> context.logger.error("Hunk #" + hunk.hunkID + " Failed @" + hunk.index + ", Fuzzing: " + hunk.fuzz)
                                        else -> context.logger.error("Hunk #" + hunk.hunkID + " Failed: " + hunk.failure.message)
                                    }
                                }
                            }
                        }
                }

                if (success) {
                    patchContext.save(output.toFile())
                }
            }
        }

        return output
    }

}