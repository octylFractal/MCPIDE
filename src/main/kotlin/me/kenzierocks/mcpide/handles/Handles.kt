package me.kenzierocks.mcpide.handles

import me.kenzierocks.mcpide.IIFE
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType


private val LOOKUP_ANY = IIFE {
    val lookup = MethodHandles.Lookup::class.java.getDeclaredField("IMPL_LOOKUP")
    lookup.isAccessible = true
    lookup.get(null) as MethodHandles.Lookup
}

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

    override fun bind(instance: C): MethodCall<R>
        = MethodCallStatic<C, R>(mh.bindTo(instance))

    // This call is supported if you pass the instance as the first arg
    @Suppress("UNCHECKED_CAST")
    override fun call(vararg args: Any?) = mh.invokeWithArguments(*args) as R?

}