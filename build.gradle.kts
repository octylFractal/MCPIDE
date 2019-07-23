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

application.mainClassName = "me.kenzierocks.mcpide.MCPIDE"

inciseBlue {
    util {
        javaVersion = JavaVersion.VERSION_12
        enableJUnit5()
    }
    license()
    ide()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    "implementation"("org.slf4j:slf4j-api:1.7.25")
    "implementation"(kotlin("stdlib-jdk8"))
    "implementation"("com.beust:klaxon:5.0.9")
    "implementation"("com.google.guava:guava:28.0-jre")

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
