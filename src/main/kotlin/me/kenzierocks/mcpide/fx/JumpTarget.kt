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

package me.kenzierocks.mcpide.fx

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.TypeDeclaration
import me.kenzierocks.mcpide.inject.ProjectScope
import me.kenzierocks.mcpide.util.JavaParserCache
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import javax.inject.Inject

/**
 * Information on locating a specific point in the project's files.
 */
sealed class JumpTarget {

    data class File(
        val file: Path,
        val lineNo: Int
    ) : JumpTarget()

    data class Directory(
        val dir: Path
    ) : JumpTarget()

}

private fun Path.resolvePackage(pkg: String) : Path {
    return resolve(pkg.replace('.', '/'))
}

private fun Path.resolveFile(fqn: String) : Path {
    val (pkg, name) = fqn.lastIndexOf('.').let { split ->
        when {
            split < 0 -> "" to fqn
            else -> fqn.substring(0, split) to fqn.substring(split + 1)
        }
    }
    return resolvePackage(pkg).resolve("$name.java")
}

private fun separateParts(name: String): Pair<String, List<String>> {
    val topLevel = StringBuilder()
    val enclosed = mutableListOf<String>()
    var hasTopLevel = false
    for (part in name.splitToSequence('.')) {
        if (part.all { it.isLowerCase() }) {
            // package piece
            topLevel.append(part).append('.')
        } else if (!hasTopLevel) {
            // final top-level part
            topLevel.append(part)
            hasTopLevel = true
        } else {
            // enclosed part
            enclosed.add(part)
        }
    }
    return topLevel.toString() to enclosed
}

private fun CompilationUnit.resolveType(steps: List<String>) : TypeDeclaration<*>? {
    var currentDecl: TypeDeclaration<*>? = null
    var typeList: List<TypeDeclaration<*>> = types
    for (step in steps) {
        val match = typeList.firstOrNull { it.nameAsString == step } ?: return null
        currentDecl = match
        typeList = match.members.filterIsInstance<TypeDeclaration<*>>()
    }
    return currentDecl
}

@ProjectScope
class JumpTargetResolver @Inject constructor(
    private val javaParserCache: JavaParserCache
) {
    /**
     * Resolve a jump target given a root directory and an expected FQN.
     *
     * @param root the root directory
     * @param name expected fully qualified type name
     */
    fun resolveJumpTarget(
        root: Path,
        name: String,
        nameIsPackage: Boolean
    ): JumpTarget? {
        if (nameIsPackage) {
            val pkg = root.resolvePackage(name)
            return when {
                Files.exists(pkg) -> JumpTarget.Directory(pkg)
                else -> null
            }
        }
        val (topLevel, enclosingParts) = separateParts(name)
        val file = root.resolveFile(topLevel)
        if (Files.notExists(file)) {
            return null
        }
        val cu = javaParserCache.parse(file)
        // Try and resolve it via enclosing parts to jump to the right part of the file
        // If we can't, just jump to the first one if _that's_ available.
        val type = cu.resolveType(listOf(topLevel.substringAfterLast('.')) + enclosingParts)
            ?: cu.types.firstOrNull()
        return JumpTarget.File(file, Optional.ofNullable(type).flatMap { it.begin }.map { it.line }.orElse(0))
    }
}
