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

package me.kenzierocks.mcpide.util

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import me.kenzierocks.mcpide.inject.ProjectScope
import mu.KotlinLogging
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Provider

/**
 * Keeps a cache of ASTs generated for various files.
 */
@ProjectScope
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