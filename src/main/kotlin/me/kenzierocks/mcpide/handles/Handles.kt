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

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType


private val LOOKUP_ANY = ({
    val lookup = MethodHandles.Lookup::class.java.getDeclaredField("IMPL_LOOKUP")
    lookup.isAccessible = true
    lookup.get(null) as MethodHandles.Lookup
})()

private fun methodCall(clazz: Class<*>, methodName: String, type: MethodType, static: Boolean): MethodHandle {
    return if (static) LOOKUP_ANY.findStatic(clazz, methodName, type)
    else LOOKUP_ANY.findVirtual(clazz, methodName, type)
}

fun <C, R> staticMethodCall(clazz: Class<C>, methodName: String,
                            returnType: Class<R>,
                            vararg parameters: Class<*>): MethodCall<R> {
    return MethodCallStatic(clazz, methodName, MethodType.methodType(returnType, parameters))
}

fun <C, R> methodCall(clazz: Class<C>, methodName: String,
                      returnType: Class<R>,
                      vararg parameters: Class<*>): BindableMethodCall<C, R> {
    return MethodCallInstance(clazz, methodName, MethodType.methodType(returnType, parameters))
}

interface MethodCall<out R> {

    fun call(vararg args: Any?): R?

}

interface BindableMethodCall<in C, out R> : MethodCall<R> {

    fun bind(instance: C): MethodCall<R>

}

class MethodCallStatic<C, R>
internal constructor(private val mh: MethodHandle) : MethodCall<R> {

    constructor(clazz: Class<C>,
                methodName: String,
                type: MethodType) : this(methodCall(clazz, methodName, type, true)) {
    }

    @Suppress("UNCHECKED_CAST")
    override fun call(vararg args: Any?) = mh.invokeWithArguments(*args) as R?

}

class MethodCallInstance<C, out R>(clazz: Class<C>,
                                   methodName: String,
                                   type: MethodType) : BindableMethodCall<C, R> {

    private val mh = methodCall(clazz, methodName, type, false)

    override fun bind(instance: C): MethodCall<R> = MethodCallStatic<C, R>(mh.bindTo(instance))

    // This call is supported if you pass the instance as the first arg
    @Suppress("UNCHECKED_CAST")
    override fun call(vararg args: Any?) = mh.invokeWithArguments(*args) as R?

}