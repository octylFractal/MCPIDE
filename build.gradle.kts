import com.techshroom.inciseblue.commonLib
import net.minecrell.gradle.licenser.LicenseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kt = "1.3.61"
    kotlin("jvm") version kt
    kotlin("kapt") version kt
    id("com.techshroom.incise-blue") version "0.5.6"
    id("net.researchgate.release") version "2.8.1"
    id("org.openjfx.javafxplugin") version "0.0.8"
    application
    id("edu.sc.seis.launch4j")
    id("com.techshroom.release-files")
}

application.mainClassName = "me.kenzierocks.mcpide.MCPIDEKt"

inciseBlue {
    util {
        javaVersion = JavaVersion.VERSION_13
        enableJUnit5()
    }
    license()
    ide()
}

kapt {
    useBuildCache = true
    correctErrorTypes = true
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf(
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview",
            "-Xuse-experimental=kotlinx.io.core.ExperimentalIoApi",
            "-XXLanguage:+PolymorphicSignature"
        )
    }
}

dependencies {
    "implementation"(kotlin("stdlib-jdk8"))
    commonLib("org.jetbrains.kotlinx", "kotlinx-coroutines", "1.3.3") {
        "implementation"(lib("core"))
        "implementation"(lib("jdk8"))
        "implementation"(lib("javafx"))
    }
    "implementation"("org.jetbrains.kotlinx", "kotlinx-coroutines-io-jvm", "0.1.16")
    "implementation"("io.github.microutils:kotlin-logging:1.7.8")
    commonLib("ch.qos.logback", "logback", "1.2.3") {
        "implementation"(lib("core"))
        "implementation"(lib("classic"))
    }
    "implementation"("com.google.guava:guava:28.2-jre")
    "implementation"("org.fxmisc.richtext:richtextfx:0.10.3")
    commonLib("com.github.javaparser", "javaparser", "3.15.9") {
        "implementation"(lib("core"))
        "implementation"(lib("symbol-solver-core"))
    }

    commonLib("org.apache.maven.resolver", "maven-resolver", "1.4.1") {
        "implementation"(lib("api"))
        "implementation"(lib("spi"))
        "implementation"(lib("impl"))
        "implementation"(lib("connector-basic"))
        "implementation"(lib("transport-wagon"))
    }
    commonLib("org.apache.maven.wagon", "wagon", "3.3.4") {
        "implementation"(lib("provider-api"))
    }
    "implementation"("org.apache.maven", "maven-resolver-provider", "3.6.3")

    val jacksonVersion = "2.10.2"
    "implementation"("com.fasterxml.jackson.core", "jackson-databind", jacksonVersion)
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
        "implementation"(lib("jsr310"))
    }
    commonLib("com.fasterxml.jackson.dataformat", "jackson-dataformat", jacksonVersion) {
        "implementation"(lib("csv"))
        "implementation"(lib("xml"))
    }
    "implementation"("com.fasterxml.woodstox:woodstox-core:6.0.3")
    "implementation"("javax.xml.bind:jaxb-api:2.3.1")
    "implementation"("com.squareup.okhttp3:okhttp:4.3.1")
    "implementation"("de.skuzzle:semantic-version:2.1.0")
    val files = files("scenicview.jar")
    if (files.all { it.exists() }) {
        "implementation"(files)
    }

    commonLib("com.google.dagger", "dagger", "2.25.4") {
        "implementation"(lib())
        "kapt"(lib("compiler"))
        "kaptTest"(lib("compiler"))
    }

    commonLib("net.octyl.apt-creator", "apt-creator", "0.1.4") {
        "implementation"(lib("annotations"))
        "kapt"(lib("processor"))
        "kaptTest"(lib("processor"))
    }

    commonLib("org.junit.jupiter", "junit-jupiter", "5.6.0") {
        "testImplementation"(lib("api"))
        "testImplementation"(lib("params"))
        "testRuntime"(lib("engine"))
    }
}

javafx {
    version = "13.0.2"
    modules = listOf("fxml", "controls", "web", "swing").map { "javafx.$it" }
}

configure<LicenseExtension> {
    include("**/*.java")
    include("**/*.kt")
    exclude("**/JavaParserTypeSolver.kt")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(mapOf(
            "Main-Class" to application.mainClassName,
            "Implementation-Version" to project.version as String
        ))
    }
}
