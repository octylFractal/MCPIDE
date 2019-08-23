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

package me.kenzierocks.mcpide.util.typesolve

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.resolution.UnsolvedSymbolException
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver

typealias FailHandler = (name: String, ctx: Node) -> String?

class NodeTypeFinder(
    private val typeSolver: TypeSolver
) {

    fun requireType(name: String, ctx: Node): String {
        return requireTypeUnless(name, ctx) { false }!!
    }

    /**
     * Require resolved type, unless, on exception, [condition] returns `false`.
     */
    inline fun requireTypeUnless(name: String, ctx: Node, crossinline condition: (String) -> Boolean): String? {
        return findType(name, ctx) { n, c ->
            // do not fail if we can instead find a matching type-var
            when {
                condition(n) -> null
                findTypeVar(n, c) -> null
                else -> throw UnsolvedSymbolException(n, c.javaClass.name + "@" + c.tokenRange.flatMap { it.toRange() })
            }
        }
    }

    fun findTypeVar(name: String, ctx: Node): Boolean {
        return generateSequence(ctx) { it.parentNode.orElse(null) }
            .filterIsInstance<NodeWithTypeParameters<*>>()
            .flatMap { it.typeParameters.asSequence() }
            .any { it.nameAsString == name }
    }

    fun findType(name: String, ctx: Node, failHandler: FailHandler = { _, _ -> null }): String? {
        if (ctx is ClassOrInterfaceType) {
            // these can be buggy -- try to resolve their scope first
            // but if that fails, it may be FQN
            if (ctx.scope.isPresent) {
                val scope = ctx.scope.get()
                findType(scope.nameAsString, scope, failHandler)?.let { base ->
                    // now we can solve `name`
                    typeSolver.tryToSolveType("$base.$name").ifSolved { return it.qualifiedName }
                }
                // maybe FQN?
                typeSolver.tryToSolveType(buildFqn(ctx)).ifSolved { return it.qualifiedName }
                // no, it was just unsolved entirely
                return failHandler(name, ctx)
            }
            // No scope, can be solved as normal:
        }
        val dotIdx = name.indexOf('.')
        if (dotIdx > -1) {
            // Maybe already FQN?
            typeSolver.tryToSolveType(name).ifSolved { return it.qualifiedName }
            // possibly not, try solving it by removing last part and solving, then appending.
            val fqc = findType(name.substring(0, dotIdx), ctx, failHandler) ?: return failHandler(name, ctx)
            typeSolver.tryToSolveType("$fqc.$name").ifSolved { return it.qualifiedName }
            // we couldn't solve it
            return failHandler(name, ctx)
        }
        // maybe java.lang?
        typeSolver.tryToSolveType("java.lang.$name").ifSolved { return it.qualifiedName }
        // this name should exist in the context, try to find it
        // if there's no CU, bail out
        val compilationUnit = ctx.findCompilationUnit().orElse(null) ?: return failHandler(name, ctx)
        val fqnByPkg = compilationUnit.packageDeclaration
            .map { "${it.nameAsString}.$name" }
            .orElse(name)
        // maybe it exists in this package
        typeSolver.tryToSolveType(fqnByPkg).ifSolved { return it.qualifiedName }
        // maybe it exists in the imports
        compilationUnit.imports.forEach { importDecl ->
            if (importDecl.isStatic) {
                return@forEach
            }
            if (importDecl.isAsterisk) {
                typeSolver.tryToSolveType("${importDecl.nameAsString}.$name").ifSolved { return it.qualifiedName }
                return@forEach
            }
            if (importDecl.nameAsString.substringAfterLast('.') == name) {
                typeSolver.tryToSolveType(importDecl.nameAsString).ifSolved { return it.qualifiedName }
            }
        }
        // maybe it's inside the CU somewhere?
        var closestDecl = findAncestorFqn(ctx)
        while (closestDecl != null) {
            typeSolver.tryToSolveType("${closestDecl.fullyQualifiedName.get()}.$name")
                .ifSolved { return it.qualifiedName }
            // didn't work, step upwards if we can
            closestDecl = findAncestorFqn(closestDecl)
        }
        // no dice
        return failHandler(name, ctx)
    }

    private fun findAncestorFqn(ctx: Node): TypeDeclaration<*>? {
        return ctx.findAncestor(TypeDeclaration::class.java)
            .filter { it.fullyQualifiedName.isPresent }
            .orElse(null)
    }

    private fun buildFqn(ctx: ClassOrInterfaceType): String {
        if (ctx.scope.isPresent) {
            return "${buildFqn(ctx.scope.get())}.${ctx.nameAsString}"
        }
        return ctx.nameAsString
    }
}

private inline fun <S : ResolvedDeclaration> SymbolReference<S>.ifSolved(block: (S) -> Unit): Unit {
    if (isSolved) {
        block(correspondingDeclaration)
    }
}
