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

package me.kenzierocks.mcpide.mcp

import me.kenzierocks.mcpide.mcp.function.DownloadClientFunction
import me.kenzierocks.mcpide.mcp.function.DownloadPackageManifestFunction
import me.kenzierocks.mcpide.mcp.function.DownloadServerFunction
import me.kenzierocks.mcpide.mcp.function.DownloadVersionManifestFunction
import me.kenzierocks.mcpide.mcp.function.InjectFunction
import me.kenzierocks.mcpide.mcp.function.ListLibrariesFunction
import me.kenzierocks.mcpide.mcp.function.PatchFunction
import me.kenzierocks.mcpide.mcp.function.StripJarFunction
import javax.inject.Inject

class BuiltInFunctionProvider @Inject constructor(
    private val downloadVersionManifestFunction: DownloadVersionManifestFunction,
    private val downloadPackageManifestFunction: DownloadPackageManifestFunction,
    private val downloadClientFunction: DownloadClientFunction,
    private val downloadServerFunction: DownloadServerFunction,
    private val listLibrariesFunction: ListLibrariesFunction
) {
    fun provide(type: String, data: Map<String, String>): McpFunction? {
        return when (type) {
            "downloadManifest" -> downloadVersionManifestFunction
            "downloadJson" -> downloadPackageManifestFunction
            "downloadClient" -> downloadClientFunction
            "downloadServer" -> downloadServerFunction
            "strip" -> StripJarFunction(data.getValue("mappings"))
            "listLibraries" -> listLibrariesFunction
            "inject" -> InjectFunction(data.getValue("inject"))
            "patch" -> PatchFunction(data.getValue("patches"))
            else -> null
        }
    }
}