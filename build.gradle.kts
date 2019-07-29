import com.techshroom.inciseblue.commonLib
import net.minecrell.gradle.licenser.LicenseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.41"
    id("com.techshroom.incise-blue") version "0.3.14"
    id("com.github.johnrengelman.shadow") version "5.1.0"
    id("net.researchgate.release") version "2.8.1"
    id("org.openjfx.javafxplugin") version "0.0.7"
    application
    id("edu.sc.seis.launch4j")
    id("com.techshroom.release-files")
}

application.mainClassName = "me.kenzierocks.mcpide.MCPIDEKt"

inciseBlue {
    util {
        javaVersion = JavaVersion.VERSION_12
        enableJUnit5()
    }
    license()
    ide()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf(
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi",
            "-Xuse-experimental=kotlinx.io.core.ExperimentalIoApi"
        )
    }
}

dependencies {
    "implementation"(kotlin("stdlib-jdk8"))
    commonLib("org.jetbrains.kotlinx", "kotlinx-coroutines", "1.3.0-RC") {
        "implementation"(lib("core"))
        "implementation"(lib("jdk8"))
        "implementation"(lib("javafx"))
    }
    "implementation"("org.jetbrains.kotlinx", "kotlinx-coroutines-io-jvm", "0.1.12")
    "implementation"("io.github.microutils:kotlin-logging:1.6.26")
    commonLib("ch.qos.logback", "logback", "1.2.3") {
        "implementation"(lib("core"))
        "implementation"(lib("classic"))
    }
    "implementation"("com.google.guava:guava:28.0-jre")
    "implementation"("org.koin:koin-core:2.0.1")
    "implementation"("org.fxmisc.richtext:richtextfx:0.10.1")
    commonLib("com.github.javaparser", "javaparser", "3.14.8") {
        "implementation"(lib("core"))
        "implementation"(lib("symbol-solver-core"))
    }

    val jacksonVersion = "2.9.9"
    "implementation"("com.fasterxml.jackson.core", "jackson-databind", "$jacksonVersion.1")
    commonLib("com.fasterxml.jackson.core", "jackson", jacksonVersion) {
        "implementation"(lib("core"))
        "implementation"(lib("annotations"))
    }
    commonLib("com.fasterxml.jackson.module", "jackson-module", jacksonVersion) {
        "implementation"(lib("kotlin"))
        "implementation"(lib("parameter-names"))
    }
    commonLib("com.fasterxml.jackson.datatype", "jackson-datatype", jacksonVersion) {
        "implementation"(lib("guava"))
        "implementation"(lib("jdk8"))
    }
    commonLib("com.fasterxml.jackson.dataformat", "jackson-dataformat", jacksonVersion) {
        "implementation"(lib("csv"))
        "implementation"(lib("xml"))
    }
    "implementation"("com.fasterxml.woodstox:woodstox-core:5.3.0")
    "implementation"("javax.xml.bind:jaxb-api:2.3.1")
    "implementation"("com.squareup.okhttp3:okhttp:4.0.1")

    commonLib("org.junit.jupiter", "junit-jupiter", "5.5.1") {
        "testImplementation"(lib("api"))
        "testRuntime"(lib("engine"))
    }
}

javafx {
    version = "12.0.1"
    modules = listOf("fxml", "controls").map { "javafx.$it" }
}

configure<LicenseExtension> {
    include("**/*.java")
    include("**/*.kt")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(mapOf(
            "Main-Class" to application.mainClassName,
            "Implementation-Version" to project.version as String
        ))
    }
}
