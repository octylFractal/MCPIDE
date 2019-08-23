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

import com.github.javaparser.ast.Node

typealias NodeMarker<N> = N.() -> Unit

val NO_OP: NodeMarker<Node> = {}

class NodeMarkTable {
    private val map = mutableMapOf<Class<out Node>, NodeMarker<Node>>()

    operator fun <N : Node> get(clazz: Class<N>): NodeMarker<N>? {
        return map[clazz]
    }

    inline fun <reified N : Node> get(): NodeMarker<N>? = this[N::class.java]

    operator fun <N : Node> set(clazz: Class<N>, block: NodeMarker<N>) {
        @Suppress("UNCHECKED_CAST")
        map[clazz] = block as NodeMarker<Node>
    }

    inline fun <reified N : Node> add(noinline block: NodeMarker<N>) {
        this[N::class.java] = block
    }
}