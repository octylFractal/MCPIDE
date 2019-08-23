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

package me.kenzierocks.mcpide.mcp.function

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.kenzierocks.mcpide.data.MojangVersionManifest
import me.kenzierocks.mcpide.mcp.McpContext
import me.kenzierocks.mcpide.mcp.getStepOutput
import me.kenzierocks.mcpide.util.toHttpUrl
import okhttp3.OkHttpClient
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPackageManifestFunction @Inject constructor(
    private val mapper: ObjectMapper,
    httpClient: OkHttpClient
) : AbstractFileDownloadFunction(httpClient) {

    override fun resolveOutput(context: McpContext) = "package_manifest.json"

    override fun resolveDlInfo(context: McpContext): DownloadInfo {
        val result = Files.newBufferedReader(context.getStepOutput<DownloadVersionManifestFunction>()).use { reader ->
            mapper.readValue<MojangVersionManifest>(reader)
        }

        return result.versions.find { it.id == context.minecraftVersion }?.let {
            DownloadInfo(it.url.toHttpUrl())
        } ?: throw IllegalStateException("Version not found: ${context.minecraftVersion}")
    }
}
