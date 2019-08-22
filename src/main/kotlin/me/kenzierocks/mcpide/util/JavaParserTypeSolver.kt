/*
 * Copyright 2016 Federico Tomassetti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.kenzierocks.mcpide.util

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ParserConfiguration.LanguageLevel.BLEEDING_EDGE
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ExecutionException

private val DEFAULT_PARSER_CONFIGURATION = ParserConfiguration().apply {
    languageLevel = BLEEDING_EDGE
}

/**
 * Defines a directory containing source code that should be used for solving symbols.
 * The directory must correspond to the root package of the files within.
 *
 * 2019-08-18 - Kenzie Togami - Kotlin-ize, Fix path loading to allow alternative FileSystems
 *
 * @author Federico Tomassetti
 */
class JavaParserTypeSolver constructor(
    private val srcDir: Path,
    parserConfiguration: ParserConfiguration = DEFAULT_PARSER_CONFIGURATION
) : TypeSolver, CoroutineScope by CoroutineScope(
    Dispatchers.Unconfined + CoroutineName("JavaParserTypeSolver") + SupervisorJob()
) {

    private var parent: TypeSolver? = null

    private val parsedFiles = CacheBuilder.newBuilder().softValues().build<Path, Optional<CompilationUnit>>()
    private val parsedDirectories = CacheBuilder.newBuilder().softValues().build<Path, List<CompilationUnit>>()
    private val foundTypes = CacheBuilder.newBuilder().softValues().build<String, SymbolReference<ResolvedReferenceTypeDeclaration>>()

    private data class Parse(
        val file: Path,
        val parent: Job?
    ) {
        val result: CompletableDeferred<CompilationUnit?> = CompletableDeferred(parent)
    }

    private suspend fun awaitParse(path: Path): CompilationUnit? {
        return Parse(path, coroutineContext[Job]).run {
            parserActor.send(this)
            result.await()
        }
    }

    private val parserActor = actor<Parse> {
        val javaParser = JavaParser(parserConfiguration)

        while (!channel.isClosedForReceive) {
            val task = channel.receiveOrNull() ?: break
            try {
                val result = javaParser.parse(task.file)
                task.result.complete(result.result.orElse(null))
            } catch (e: NoSuchFileException) {
                task.result.complete(null)
            } catch (e: Exception) {
                task.result.completeExceptionally(e)
            }
        }
    }

    init {
        if (!Files.exists(srcDir) || !Files.isDirectory(srcDir)) {
            throw IllegalStateException("SrcDir does not exist or is not a directory: $srcDir")
        }
    }

    override fun toString(): String {
        return "JavaParserTypeSolver{" +
            "srcDir=" + srcDir +
            ", parent=" + parent +
            '}'.toString()
    }

    override fun getParent(): TypeSolver? {
        return parent
    }

    override fun setParent(parent: TypeSolver) {
        this.parent = parent
    }

    private fun parse(srcFile: Path): Optional<CompilationUnit> {
        try {
            return parsedFiles.get(srcFile.toAbsolutePath()) {
                Optional.ofNullable(runBlocking { awaitParse(srcFile) })
            }
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }

    }

    private fun parseDirectory(srcDirectory: Path): List<CompilationUnit> {
        try {
            return parsedDirectories.get(srcDirectory.toAbsolutePath()) {
                if (Files.exists(srcDirectory)) {
                    Files.newDirectoryStream(srcDirectory).use { srcDirectoryStream ->
                        srcDirectoryStream
                            .flatMap { file ->
                                return@flatMap if (file.fileName.toString().toLowerCase().endsWith(".java")) {
                                    parse(file).map { listOf(it) }.orElseGet(::emptyList)
                                } else {
                                    emptyList()
                                }
                            }
                            .toList()
                    }
                } else {
                    emptyList()
                }
            }
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }

    }

    override fun tryToSolveType(name: String): SymbolReference<ResolvedReferenceTypeDeclaration> {
        // TODO support enums
        // TODO support interfaces
        return try {
            foundTypes.get(name) {
                val result = tryToSolveTypeUncached(name)
                if (result.isSolved) {
                    return@get SymbolReference.solved(result.correspondingDeclaration)
                }
                result
            }
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }

    }

    private fun findType(decls: List<TypeDeclaration<*>>, name: String): TypeDeclaration<*>? {
        val parts = name.split('.', limit = 2)

        return decls.firstOrNull { it.nameAsString == parts[0] }?.let { first ->
            when {
                parts.size == 1 -> first
                else -> findType(first.members.filterIsInstance<TypeDeclaration<*>>(), parts[1])
            }
        }
    }

    private fun resolveJavaFile(parts: List<String>): Path {
        var path = srcDir
        parts.dropLast(1).forEach { part ->
            path = path.resolve(part)
        }
        return path.resolve("${parts.last()}.java")
    }

    private fun tryToSolveTypeUncached(name: String): SymbolReference<ResolvedReferenceTypeDeclaration> {
        val nameElements = name.split('.').dropLastWhile { it.isEmpty() }

        for (i in nameElements.size downTo 1) {
            val filePath = resolveJavaFile(nameElements.subList(0, i))

            val typeName = nameElements.subList(i - 1, nameElements.size).joinToString(separator = ".")

            // As an optimization we first try to look in the canonical position where we expect to find the file
            run {
                val found = parse(filePath)
                    .map { findType(it.types, typeName) }
                if (found.isPresent) {
                    return SymbolReference.solved(JavaParserFacade.get(this).getTypeDeclaration(found.get()))
                }
            }

            // If this is not possible we parse all files
            // We try just in the same package, for classes defined in a file not named as the class itself
            run {
                val found = parseDirectory(filePath.parent)
                    .mapNotNull { findType(it.types, typeName) }
                    .firstOrNull()
                if (found != null) {
                    return SymbolReference.solved(JavaParserFacade.get(this).getTypeDeclaration(found))
                }
            }
        }

        return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration::class.java)
    }

}
