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
import com.github.javaparser.ParseStart.COMPILATION_UNIT
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ParserConfiguration.LanguageLevel.BLEEDING_EDGE
import com.github.javaparser.Providers.provider
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.symbolsolver.javaparser.Navigator
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.google.common.cache.CacheBuilder
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.Optional
import java.util.concurrent.ExecutionException
import java.util.stream.Collectors
import java.util.stream.Stream

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
) : TypeSolver {
    private val javaParser: JavaParser

    private var parent: TypeSolver? = null

    private val parsedFiles = CacheBuilder.newBuilder().softValues().build<Path, Optional<CompilationUnit>>()
    private val parsedDirectories = CacheBuilder.newBuilder().softValues().build<Path, List<CompilationUnit>>()
    private val foundTypes = CacheBuilder.newBuilder().softValues().build<String, SymbolReference<ResolvedReferenceTypeDeclaration>>()

    init {
        if (!Files.exists(srcDir) || !Files.isDirectory(srcDir)) {
            throw IllegalStateException("SrcDir does not exist or is not a directory: $srcDir")
        }
        javaParser = JavaParser(parserConfiguration)
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
                try {
                    when {
                        Files.isRegularFile(srcFile) -> javaParser.parse(COMPILATION_UNIT, provider(srcFile))
                            .result
                            .map { cu -> cu.setStorage(srcFile) }
                        else -> Optional.empty()
                    }
                } catch (e: FileNotFoundException) {
                    throw RuntimeException("Issue while parsing while type solving: " + srcFile.toAbsolutePath(), e)
                }
            }
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }

    }

    private fun parseDirectoryRecursively(srcDirectory: Path): List<CompilationUnit> {
        return parseDirectory(srcDirectory, true)
    }

    private fun parseDirectory(srcDirectory: Path, recursively: Boolean = false): List<CompilationUnit> {
        try {
            return parsedDirectories.get(srcDirectory.toAbsolutePath()) {
                if (Files.exists(srcDirectory)) {
                    Files.newDirectoryStream(srcDirectory).use { srcDirectoryStream ->
                        srcDirectoryStream
                            .flatMap { file ->
                                return@flatMap if (file.fileName.toString().toLowerCase().endsWith(".java")) {
                                    parse(file).map { listOf(it) }.orElseGet(::emptyList)
                                } else if (recursively && file.toFile().isDirectory) {
                                    parseDirectoryRecursively(file)
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
                val compilationUnit = parse(filePath)
                if (compilationUnit.isPresent) {
                    val astTypeDeclaration = Navigator.findType(compilationUnit.get(), typeName)
                    if (astTypeDeclaration.isPresent) {
                        return SymbolReference.solved(JavaParserFacade.get(this).getTypeDeclaration(astTypeDeclaration.get()))
                    }
                }
            }

            // If this is not possible we parse all files
            // We try just in the same package, for classes defined in a file not named as the class itself
            run {
                val compilationUnits = parseDirectory(filePath.parent)
                for (compilationUnit in compilationUnits) {
                    val astTypeDeclaration = Navigator.findType(compilationUnit, typeName)
                    if (astTypeDeclaration.isPresent) {
                        return SymbolReference.solved(JavaParserFacade.get(this).getTypeDeclaration(astTypeDeclaration.get()))
                    }
                }
            }
        }

        return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration::class.java)
    }

}
/**
 * Note that this parse only files directly contained in this directory.
 * It does not traverse recursively all children directory.
 */
