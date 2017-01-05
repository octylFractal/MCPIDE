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