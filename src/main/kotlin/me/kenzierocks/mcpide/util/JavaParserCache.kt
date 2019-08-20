package me.kenzierocks.mcpide.util

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import mu.KotlinLogging
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Keeps a cache of ASTs generated for various files.
 */
@Singleton
class JavaParserCache @Inject constructor(
    javaParser: Provider<JavaParser>
) {
    private val logger = KotlinLogging.logger { }
    // JavaParser doesn't appear to be thread-safe, store one in each thread
    // Might be a little leaky, but probably better than a single parsing thread
    private val jpThreadLocal = ThreadLocal.withInitial(javaParser::get)
    private val cache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .softValues()
        .build(object : CacheLoader<Path, CompilationUnit>() {
            override fun load(file: Path): CompilationUnit {
                val parseResult = jpThreadLocal.get().parse(file)
                return parseResult.result.orElseThrow {
                    if (logger.isInfoEnabled) {
                        parseResult.problems.forEach { problem ->
                            logger.warn(problem.toString())
                        }
                    }
                    IllegalStateException("Failed to parse.")
                }
            }
        })

    fun parse(file: Path): CompilationUnit {
        return cache[file]
    }
}