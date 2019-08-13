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

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

val JAVA_HOME: String? = System.getProperty("java.home")
val DEFAULT_JAVA_EXECUTABLE = JAVA_HOME?.let { javaHome ->
    Paths.get(javaHome, "bin/java")
}?.takeIf { Files.exists(it) } ?: throw IllegalStateException("No Java executable in $JAVA_HOME")

/**
 * Utility for executing `java` commands.
 */
class JavaExec(
    private val jvmArgs: List<String>,
    private val applicationArgs: List<String>,
    private val classpath: List<Path>,
    private val workingDir: Path,
    private val mainClass: String,
    private val javaExecutable: Path = DEFAULT_JAVA_EXECUTABLE
) : AutoCloseable {
    lateinit var process: Process

    fun execute(): Process {
        // <java> <jvmArgs> <mainClass> <appArgs>
        val command = mutableListOf(javaExecutable.toAbsolutePath().toString())
        command.addAll(jvmArgs)
        command.add("-classpath")
        command.add(classpath.joinToString(separator = File.pathSeparator) { it.toString() })
        command.add(mainClass)
        command.addAll(applicationArgs)
        process = ProcessBuilder()
            .command(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start()
        return process
    }

    override fun close() {
        if (this::process.isInitialized) {
            process.destroy()
            if (!process.waitFor(10, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly().waitFor()
            }
        }
    }
}