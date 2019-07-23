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
package me.kenzierocks.mcpide

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import javafx.beans.InvalidationListener
import javafx.beans.binding.ObjectBinding
import javafx.beans.binding.ObjectExpression
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.StringPropertyBase
import javafx.collections.FXCollections
import javafx.collections.ObservableMap
import me.kenzierocks.mcpide.fx.map
import me.kenzierocks.mcpide.pathext.div
import me.kenzierocks.mcpide.pathext.touch
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedHashMap

fun p(s: String) = Paths.get(s)!!

fun Map<String, String>.save(p: Path, comment: CharSequence? = null) {
    Files.newBufferedWriter(p).use { w ->
        // !isNullOrBlank() doesn't mark comment as non-null
        if (comment != null && comment.isNotBlank()) {
            w.append("# ")
            comment.split('\n').joinTo(w, "\n# ")
            w.appendln()
        }
        for ((k, v) in this) {
            w.append(k).append('=').appendln(v)
        }
        w.appendln()
    }
}

/**
 * Returns the comments, for no reason.
 */
fun MutableMap<String, String>.load(p: Path): String {
    val str = StringBuilder()
    Files.newBufferedReader(p).useLines { lines ->
        lines
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter {
                if (it.startsWith('#')) {
                    str.append(it.substring(1).trimStart()).appendln()
                    false
                } else {
                    true
                }
            }
            .forEach { val (k, v) = it.split('=', limit = 2); put(k, v) }
    }
    return str.toString()
}

class ConfigKey(val config: Config, val key: String)
    : StringPropertyBase(config[key]) {

    private val present = ReadOnlyBooleanWrapper(this, "present", key in config)
    fun isPresent(): Boolean = present.value
    fun presentProperty(): ReadOnlyBooleanProperty = present.readOnlyProperty

    override fun getName() = key

    override fun getBean() = config

    init {
        addListener { _, _, new -> config[key] = new }
    }

}

interface Config : MutableMap<String, String> {
    companion object {
        val GLOBAL = newConfig(IIFE {
            val directory = IIFE {
                val xdgCfg = System.getenv("XDG_CONFIG_HOME")
                if (xdgCfg != null) {
                    p(xdgCfg)
                } else {
                    p(System.getProperty("user.home")) / ".config"
                }
            } / MCPIDE.TITLE
            Files.createDirectories(directory)
            if (!Files.isDirectory(directory)) {
                throw IllegalStateException("$directory is not a directory!")
            }
            directory
        } / "config.properties")
    }

    // Public API implemented by Proxy
    @Suppress("unused")
    fun load()

    @Suppress("unused")
    fun save(comment: String? = null)

    @Suppress("unused")
    var comments: String

    @Suppress("unused")
    val file: Path

    @Suppress("unused")
    fun computeCommentsWith(func: () -> String)

    /**
     * Returns an observable ConfigKey for the specified key.
     */
    @Suppress("unused")
    infix fun observable(key: String): ConfigKey

    operator fun set(key: String, value: String): String?

}

fun ObjectExpression<Config?>.observable(key: String): ObjectBinding<ConfigKey?> {
    return map { it?.observable(key) }
}


fun ObjectExpression<Path?>.configFileProperty(bean: Any? = null, name: String? = null): ReadOnlyObjectProperty<Config?> {
    val wrapper = ReadOnlyObjectWrapper<Config?>(bean, name)
    wrapper.bind(map {
        if (it == null) return@map null
        it.touch()
        newConfig(it)
    })
    return wrapper.readOnlyProperty
}

fun newConfig(configFile: Path): Config {
    return ObservableBasedConfig(configFile = configFile)
}

private class ObservableBasedConfig(val internalMap: ObservableMap<String, String>
                                    = FXCollections.observableMap(LinkedHashMap<String, String>()),
                                    private val configFile: Path)
    : Config, ObservableMap<String, String> by internalMap {

    private val weakConfigKeys = CacheBuilder.newBuilder()
        .weakValues()
        .build<String, ConfigKey>(CacheLoader.from { key ->
            ConfigKey(this, key!!)
        })

    init {
        load()
        addListener(InvalidationListener { save() })
    }

    override fun load() {
        load(configFile)
    }

    override fun save(comment: String?) {
        save(configFile, comments)
    }

    private var directComment: String? = ""
    private var funcComment: (() -> String)? = null
    override var comments: String
        get() = directComment ?: funcComment!!()
        set(value) {
            funcComment = null
            directComment = value
        }

    override fun computeCommentsWith(func: () -> String) {
        directComment = null
        funcComment = func
    }

    override val file = configFile

    override fun observable(key: String): ConfigKey = weakConfigKeys[key]

    override fun set(key: String, value: String): String? {
        val ret = put(key, value)
        weakConfigKeys.getIfPresent(key)?.value = value
        return ret
    }

}
