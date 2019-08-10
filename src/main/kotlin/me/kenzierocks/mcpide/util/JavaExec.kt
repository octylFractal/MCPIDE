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