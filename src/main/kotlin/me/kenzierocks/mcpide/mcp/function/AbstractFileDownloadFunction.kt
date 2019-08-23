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

import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import kotlinx.coroutines.io.jvm.javaio.copyTo
import kotlinx.coroutines.io.jvm.javaio.toByteReadChannel
import me.kenzierocks.mcpide.mcp.McpContext
import me.kenzierocks.mcpide.mcp.McpFunction
import me.kenzierocks.mcpide.util.checkStatusCode
import me.kenzierocks.mcpide.util.enqueueSuspend
import me.kenzierocks.mcpide.util.putFile
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

abstract class AbstractFileDownloadFunction(
    private val httpClient: OkHttpClient
) : McpFunction {

    protected abstract fun resolveOutput(context: McpContext): String

    protected abstract fun resolveDlInfo(context: McpContext): DownloadInfo

    override suspend operator fun invoke(context: McpContext): Path {
        val output = context.arguments.getOrElse("output", {
            context.file(resolveOutput(context))
        }) as Path
        val outputExists = Files.exists(output)
        val download = when {
            outputExists -> output.resolveSibling("${output.fileName}.new")
            else -> output
        }

        val info = resolveDlInfo(context)
        if (info.hash != null && outputExists && info.hash.matchesFile(output)) {
            // Found matching hash
            return output
        }

        Files.deleteIfExists(download)
        Files.createDirectories(download.parent)

        try {
            val response = httpClient.newCall(Request.Builder()
                .url(info.url)
                .build()).enqueueSuspend().throwIfFailed().checkStatusCode()
            response.body!!.byteStream().toByteReadChannel().apply {
                Files.newOutputStream(download).use { output ->
                    copyTo(output)
                }
            }
        } catch (t: Throwable) {
            Files.deleteIfExists(download)
            throw t
        }

        if (output !== download) {
            Files.move(download, output, StandardCopyOption.REPLACE_EXISTING)
        }

        return output
    }


}

data class DownloadInfo(val url: HttpUrl, val hash: HashValue? = null)

data class HashValue(val hash: HashCode, val func: HashFunction)

fun HashValue.matchesFile(file: Path): Boolean {
    val intSafeSize = Files.size(file).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return func.newHasher(intSafeSize).putFile(file).hash() == hash
}
