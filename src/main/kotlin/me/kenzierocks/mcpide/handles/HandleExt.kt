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
package me.kenzierocks.mcpide.handles

import kotlin.reflect.KClass

// Support something like Class::class[Static, "resolveName"].parameters(String::class).returns(String::class)

class HandleAddParams<C, R : Any>(val clazz: Class<C>, val methodName: String) {
    private lateinit var parameters: Array<Class<*>>
    private lateinit var returnType: Class<R>

    fun parameters(vararg params: KClass<*>): HandleAddParams<C, R> {
        parameters = params.map { it.java }.toTypedArray()
        return this
    }

    fun <NR : Any> returns(type: KClass<NR>): HandleAddParams<C, NR> {
        @Suppress("UNCHECKED_CAST")
        val hap = this as HandleAddParams<C, NR>
        hap.returnType = type.java
        return hap
    }

    fun build() = methodCall(clazz, methodName, returnType, *parameters)

    fun buildStatic() = staticMethodCall(clazz, methodName, returnType, *parameters)
}

operator fun <C : Any> KClass<C>.get(methodName: String)
    = HandleAddParams<C, Any>(this.java, methodName)