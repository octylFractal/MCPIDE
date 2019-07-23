package me.kenzierocks.releasefiles

import com.dd.plist.NSDictionary
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import edu.sc.seis.launch4j.Launch4jPluginExtension
import org.ajoberstar.grgit.operation.LogOp
import org.ajoberstar.grgit.operation.OpenOp
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.kohsuke.github.GitHubBuilder
import java.io.File
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

class ReleaseFilesPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.withId("application") {
            project.plugins.withId("net.researchgate.release") {
                project.setup()
            }
        }
    }

    private fun Project.setup() {
        val distZipTask = tasks.getByName("distZip") as Zip

        val releasesFolderName = "releases"
        val releasesBase = file("build/$releasesFolderName")

        // apps are a directory
        val appDir = releasesBase.toPath().resolve("$name.app").resolve("Contents")
        val appResources = appDir.resolve("Resources")
        val appMacOs = appDir.resolve("MacOS")

        val srcResources = file("release-resources").toPath()

        // NOTE: paths are broken with gradle -- don"t ever pass them to gradle

        val macReleaseApp = tasks.register("macReleaseApp") {
            dependsOn(distZipTask)

            inputs.files(distZipTask.archiveFile)
            outputs.dir(appDir.toFile())

            doFirst {
                // macOS app structure is like so
                // MCPIDE.app/Contents/
                //     MacOS/mcpide-bootstrap - script that calls script from distZip
                //     Resources/MCPIDE - MCPIDE-<version>.zip contents
                //     Resources/logo.png - Logo, data is stored in ./release-resources/
                //     Info.plist - macOS startup file, data is stored in ./release-resources/
                Files.createDirectories(appResources)
                Files.createDirectories(appMacOs)

                val resourceRoot = appResources.resolve(this@setup.name)
                copy {
                    from(zipTree(distZipTask.archiveFile))
                    into(resourceRoot.toFile())
                }
                // after copying zip content we must move it again
                // see https://github.com/gradle/gradle/issues/1108
                val baseDir = resourceRoot.resolve("${this@setup.name}-$version")

                try {
                    Files.move(baseDir.resolve("bin"), appResources.resolve("bin"), StandardCopyOption.REPLACE_EXISTING)
                } catch (ignore: DirectoryNotEmptyException) {
                }

                try {
                    Files.move(baseDir.resolve("lib"), appResources.resolve("lib"), StandardCopyOption.REPLACE_EXISTING)
                } catch (ignore: DirectoryNotEmptyException) {
                }

                resourceRoot.toFile().deleteRecursively()

                val infoPlistFile = srcResources.resolve("Info.plist").copyIntoDir(appDir)
                srcResources.resolve("icon.icns").copyIntoDir(appResources)
                val bootstrapScript = srcResources.resolve("baleout-bootstrap").copyIntoDir(appMacOs)

                // chmod 755 mcpide-bootstrap
                Files.setPosixFilePermissions(bootstrapScript, PosixFilePermissions.fromString("rwxr-xr-x"))

                // rewrite Info.plist with correct version
                val infoPlist = PropertyListParser.parse(infoPlistFile.toFile()) as NSDictionary
                // GetInfo is basically version string
                infoPlist["CFBundleGetInfoString"] = NSString("$version")
                PropertyListParser.saveAsXML(infoPlist, infoPlistFile.toFile())
            }
        }

        // make a shadow jar for Windows/Linux -- the gradle script is better for macOS
        val createExe = tasks.getByName("createExe")
        val shadowJar = tasks.getByName("shadowJar")
        createExe.dependsOn(shadowJar)

        configure<Launch4jPluginExtension> {
            mainClassName = the<ApplicationPluginConvention>().mainClassName
            icon = srcResources.resolve("icon.ico").toFile().toString()
            outputDir = releasesFolderName // output to the build folder
            outfile = "$name-${this@setup.version}.exe"
            copyConfigurable = shadowJar.outputs.files
            jar = shadowJar.outputs.files.files.first().absolutePath
            jreRuntimeBits = "64/32"

            // not using HTTPS should be a punishable crime
            downloadUrl = "https://java.com/download"

            fileDescription = "$name ${this@setup.version}"
            windowTitle = name
        }

        val windowsReleaseExe = tasks.register("windowsReleaseExe") {
            extensions.extraProperties["outFile"] = createExe.property("dest")

            outputs.files(createExe.outputs.files)
        }

        // URJ is just the shadowJar
        val universalReleaseJar = tasks.register<Copy>("universalReleaseJar") {
            dependsOn(shadowJar)
            val shadowJarFile = shadowJar.outputs.files.first()
            val outFile = File(releasesBase, "${this@setup.name}-$version-universal.jar")

            from(shadowJarFile) {
                rename(".*", outFile.name)
            }
            into(outFile.parentFile)
        }

        val deleteExtraL4jJunk = tasks.register("deleteExtraLaunch4jJunk") {
            description = "Deletes the extra launch4j lib folder"
            mustRunAfter(createExe)

            val lib = File(releasesBase, "lib")
            inputs.files(lib)
            doLast { lib.deleteRecursively() }
        }

        // zip the app for release
        val macReleaseZip = tasks.register<Zip>("macReleaseZip") {
            dependsOn(macReleaseApp)
            from(macReleaseApp.get().outputs.files)

            archiveFileName.set("${this@setup.name}-${this@setup.version}-macOS.zip")
            destinationDirectory.set(releasesBase)
        }

        val osBundles = tasks.register("osBundles") {
            dependsOn(macReleaseZip)
            dependsOn(windowsReleaseExe)
            dependsOn(universalReleaseJar)
            dependsOn(deleteExtraL4jJunk)

            outputs.files(
                    macReleaseZip.get().outputs.files,
                    windowsReleaseExe.get().outputs.files,
                    universalReleaseJar.get().outputs.files
            )
        }

        val deployOsBundles = tasks.register("deployOsBundles") {
            dependsOn(osBundles)

            doLast {
                val op = OpenOp()
                op.dir = file(".")
                val git = op.call()

                val log = LogOp(git.repository)
                log.maxCommits = 1
                val tipCommit = log.call().first()
                    ?: throw StopExecutionException("No tip commit for branch \${git.branch.current.name}")

                val gitHub = GitHubBuilder
                    .fromCredentials()
                    .build()
                val repository = gitHub.getRepository("TechShroom/UnplannedDescent")
                val rel = repository
                    .createRelease(version.toString())
                    .name("\${project.name} \${project.version}")
                    .body("Release of Bale Out, version \${project.version}.")
                    .draft(true) // draft it so I can write changelogs
                    .commitish(tipCommit.id)
                    .create()
                osBundles.get().outputs.files.forEach { f ->
                    // Possible files are .zip, .jar, and .exe
                    // Anything else -> exception
                    val mime = when (val ext = f.toString().split("\\.").last()) {
                        "zip", "jar" -> "application/zip"
                        "exe" ->
                            // I really have no idea which one to use...
                            // application/x-msdownload -- listed by MS as the right one
                            // application/vnd.microsoft.portable-executable -- listed by IANA
                            // there are others
                            "application/x-msdownload"
                        else -> throw StopExecutionException("What sort of MIME type is $ext?")
                    }
                    println("Uploading asset $f of mime type $mime")
                    try {
                        rel.uploadAsset(f, mime)
                    } catch (e: Exception) {
                        rel.delete()
                        throw e
                    }
                }
            }
        }

        tasks.getByName("build").dependsOn(osBundles)
        // Release-plugin: we must run osBundles after release is built
        tasks.getByName("afterReleaseBuild").dependsOn(deployOsBundles)
    }

}
