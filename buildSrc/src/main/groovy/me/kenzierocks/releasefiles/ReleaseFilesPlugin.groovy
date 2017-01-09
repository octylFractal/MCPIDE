package me.kenzierocks.releasefiles

import com.dd.plist.NSDictionary
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import edu.sc.seis.launch4j.Launch4jPluginExtension
import org.ajoberstar.grgit.operation.LogOp
import org.ajoberstar.grgit.operation.OpenOp
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.java.archives.Manifest
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder

import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

class ReleaseFilesPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        Zip distZipTask = (Zip) project.tasks.getByName("distZip")

        def releasesFolderName = "releases"
        def releasesBase = project.file("build/$releasesFolderName")

        // apps are a directory
        def appDir = releasesBase.toPath().resolve("${project.name}.app").resolve("Contents")
        def appResources = appDir.resolve("Resources")
        def appMacOs = appDir.resolve("MacOS")

        def srcResources = project.file("release-resources").toPath()

        // NOTE: paths are broken with gradle -- don't ever pass them to gradle

        Task macReleaseApp = project.task("macReleaseApp").configure { Task task ->
            task.dependsOn distZipTask

            task.inputs.files(distZipTask.archivePath)
            task.outputs.dir(appDir.toFile())

            task.doFirst {
                // macOS app structure is like so
                // MCPIDE.app/Contents/
                //     MacOS/mcpide-bootstrap - script that calls script from distZip
                //     Resources/MCPIDE - MCPIDE-<version>.zip contents
                //     Resources/logo.png - Logo, data is stored in ./release-resources/
                //     Info.plist - macOS startup file, data is stored in ./release-resources/
                Files.createDirectories(appResources)
                Files.createDirectories(appMacOs)

                def resourceRoot = appResources.resolve(project.name)
                project.copy { CopySpec copySpec ->
                    copySpec.from project.zipTree(distZipTask.archivePath)
                    copySpec.into resourceRoot.toFile()
                }
                // after copying zip content we must move it again
                // see https://github.com/gradle/gradle/issues/1108
                def baseDir = resourceRoot.resolve("${project.name}-${project.version}")

                try {
                    Files.move(baseDir.resolve("bin"), appResources.resolve("bin"), StandardCopyOption.REPLACE_EXISTING)
                } catch (DirectoryNotEmptyException ignore) {
                }
                try {
                    Files.move(baseDir.resolve("lib"), appResources.resolve("lib"), StandardCopyOption.REPLACE_EXISTING)
                } catch (DirectoryNotEmptyException ignore) {
                }
                resourceRoot.toFile().deleteDir()

                def infoPlistFile = Methods.copyIntoDir(srcResources.resolve("Info.plist"), appDir)
                Methods.copyIntoDir(srcResources.resolve("icon.icns"), appResources)
                def bootstrapScript = Methods.copyIntoDir(srcResources.resolve("mcpide-bootstrap"), appMacOs)

                // chmod 755 mcpide-bootstrap
                Files.setPosixFilePermissions(bootstrapScript, PosixFilePermissions.fromString("rwxr-xr-x"))

                // rewrite Info.plist with correct version
                NSDictionary infoPlist = (NSDictionary) PropertyListParser.parse(infoPlistFile.toFile())
                // GetInfo is basically version string
                infoPlist.put("CFBundleGetInfoString", new NSString("${project.version}"))
                PropertyListParser.saveAsXML(infoPlist, infoPlistFile.toFile())
            }
        }

        // make a shadow jar for Windows/Linux -- the gradle script is better for macOS
        project.tasks.getByName('jar').configure { Jar task ->
            task.manifest { Manifest manifest ->
                manifest.attributes 'MainClass': project.property('mainClassName')
            }
        }

        Task createExe = project.tasks.getByName('createExe')
        Task shadowJar = project.tasks.getByName('shadowJar')
        createExe.dependsOn shadowJar

        project.configure(project.extensions.getByType(Launch4jPluginExtension)) { Launch4jPluginExtension ext ->
            ext.setMainClassName(project.property('mainClassName') as String)
            ext.setIcon(srcResources.resolve('icon.ico').toFile().toString())
            ext.outputDir = releasesFolderName // output to the build folder
            ext.copyConfigurable = shadowJar.outputs.files
            ext.setJar(shadowJar.outputs.files.files.first().absolutePath)
            ext.jreRuntimeBits = '64/32'

            // not using HTTPS should be a punishable crime
            ext.downloadUrl = "https://java.com/download"

            ext.fileDescription = "${project.name} ${project.version}"
            ext.windowTitle = project.name
        }

        Task windowsReleaseExe = project.task('windowsReleaseExe').configure { Task task ->
            task.dependsOn createExe

            task.extensions.extraProperties['outFile'] = createExe.property('dest')

            task.outputs.files(createExe.outputs.files)
        }

        // URJ is just the shadowJar
        Copy universalReleaseJar = project.task([type: Copy], 'universalReleaseJar').configure { Copy task ->
            task.dependsOn shadowJar
            def shadowJarFile = shadowJar.outputs.files.first()
            def outFile = new File(releasesBase, "${project.name}-${project.version}-universal.jar")

            task.extensions.extraProperties['outFile'] = outFile

            task.from(shadowJarFile) { CopySpec copySpec ->
                copySpec.rename ".*", outFile.name
            }
            task.into outFile.parentFile
        } as Copy

        project.task("deleteExtraLaunch4jJunk").configure { Task task ->
            task.description = "Deletes the extra launch4j lib folder"
            task.mustRunAfter createExe

            def lib = new File(releasesBase, "lib")
            task.inputs.files(lib)
            task.doLast {
                lib.deleteDir()
            }
        }

        // zip the app for release
        Zip macReleaseZip = project.task([type: Zip], "macReleaseZip").configure { Zip task ->
            task.dependsOn macReleaseApp
            task.from(macReleaseApp.outputs.files)

            task.archiveName = "${project.name}-${project.version}-macOS.zip"
            task.destinationDir = releasesBase
        } as Zip

        def osBundles = project.task("osBundles").configure { Task task ->
            task.dependsOn macReleaseZip, windowsReleaseExe, universalReleaseJar
            task.dependsOn "deleteExtraLaunch4jJunk"

            task.outputs.files(
                    macReleaseZip.outputs.files,
                    windowsReleaseExe.property('outFile'),
                    universalReleaseJar.property('outFile')
            )
        }

        def deployOsBundles = project.task('deployOsBundles').configure { Task task ->
            task.dependsOn osBundles

            task.doLast {
                OpenOp op = new OpenOp()
                op.dir = project.file('.')
                def git = op.call()

                LogOp log = new LogOp(git.repository)
                log.maxCommits = 1
                def tipCommit = log.call().first()

                if (tipCommit == null) {
                    throw new StopExecutionException("No tip commit for branch ${git.branch.current.name}")
                }

                GitHub gitHub = GitHubBuilder
                        .fromCredentials()
                        .build()
                def repository = gitHub.getRepository("kenzierocks/MCPIDE")
                GHRelease rel = repository
                        .createRelease(project.version as String)
                        .name("${project.name} ${project.version}")
                        .body("Release of MCPIDE, version ${project.version}")
                        .draft(true) // draft it so I can write changelogs
                        .commitish(tipCommit.id)
                        .create()
                osBundles.outputs.files.each { File f ->
                    // Possible files are .zip, .jar, and .exe
                    // Anything else -> exception
                    def ext = f.toString().split("\\.").last()
                    String mime
                    switch (ext) {
                        case "zip":
                        case "jar":
                            mime = "application/zip"
                            break
                        case "exe":
                            // I really have no idea which one to use...
                            // application/x-msdownload -- listed by MS as the right one
                            // application/vnd.microsoft.portable-executable -- listed by IANA
                            // there are others
                            mime = "application/x-msdownload"
                            break
                        default:
                            throw new StopExecutionException("What sort of MIME type is $ext?")
                    }
                    println("Uploading asset $f of mime type $mime")
                    try {
                        rel.uploadAsset(f, mime)
                    } catch (Exception e) {
                        rel.delete()
                        throw e
                    }
                }
            }
        }

        project.tasks.getByName('build').dependsOn osBundles
        // Release-plugin: we must run osBundles after release is built
        project.tasks.getByName('afterReleaseBuild').dependsOn deployOsBundles
    }

}
