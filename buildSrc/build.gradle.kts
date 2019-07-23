plugins {
    `kotlin-dsl`
    idea
    eclipse
}

repositories {
    jcenter()
    gradlePluginPortal()
}
configurations.all {
    resolutionStrategy {
        force("commons-io:commons-io:2.5")
    }
}
dependencies {
    "implementation"(gradleApi())
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(kotlin("gradle-plugin"))

    "implementation"("gradle.plugin.edu.sc.seis.gradle", "launch4j", "2.4.5")

    "implementation"("com.googlecode.plist", "dd-plist", "1.16")
    "implementation"("org.kohsuke", "github-api", "1.82")
    "implementation"("org.ajoberstar", "grgit", "1.7.2")
}

gradlePlugin {
    plugins {
        create("releaseFilesPlugin") {
            id = "com.techshroom.release-files"
            implementationClass = "me.kenzierocks.releasefiles.ReleaseFilesPlugin"
        }
    }
}
