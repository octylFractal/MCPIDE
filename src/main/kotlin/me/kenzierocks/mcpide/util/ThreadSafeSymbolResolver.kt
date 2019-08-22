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

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.type.Type
import com.github.javaparser.resolution.SymbolResolver
import com.github.javaparser.resolution.types.ResolvedType
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/* TODO we should probably just clone JavaSymbolSolver and make the JPF instances per-thread,
    that way there's not a huge bottleneck */
class ThreadSafeSymbolResolver(
    private val delegate: SymbolResolver
) : SymbolResolver {

    /**
     * Thread-lock around everything to prevent issues with JavaParserFacade.
     */
    private val lock = ReentrantLock()

    override fun calculateType(expression: Expression?): ResolvedType? = lock.withLock { delegate.calculateType(expression) }

    override fun <T : Any?> toResolvedType(javaparserType: Type?, resultClass: Class<T>?): T = lock.withLock { delegate.toResolvedType(javaparserType, resultClass) }

    override fun <T : Any?> resolveDeclaration(node: Node?, resultClass: Class<T>?): T = lock.withLock { delegate.resolveDeclaration(node, resultClass) }
}