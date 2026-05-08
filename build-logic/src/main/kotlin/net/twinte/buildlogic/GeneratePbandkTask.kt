package net.twinte.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.util.Locale
import javax.inject.Inject

abstract class GeneratePbandkTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoFiles: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val protocExecutable: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pbandkPluginJar: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val protoc = protocExecutable.singleFile
        protoc.setExecutable(true)

        val plugin = temporaryDir.resolve(if (isWindows()) "protoc-gen-pbandk.bat" else "protoc-gen-pbandk")
        plugin.parentFile.mkdirs()
        if (isWindows()) {
            plugin.writeText("@echo off\r\njava -jar \"${pbandkPluginJar.singleFile.absolutePath}\" %*\r\n")
        } else {
            plugin.writeText("#!/usr/bin/env sh\nexec java -jar '${pbandkPluginJar.singleFile.absolutePath}' \"\$@\"\n")
            plugin.setExecutable(true)
        }

        val root = protoRoot.get().asFile
        val relativeProtoFiles = protoFiles.files
            .sortedBy { it.invariantSeparatorsPath }
            .map { it.relativeTo(root).invariantSeparatorsPath }

        execOperations.exec {
            executable = protoc.absolutePath
            args(
                "--plugin=protoc-gen-pbandk=${plugin.absolutePath}",
                "--pbandk_out=${output.absolutePath}",
                "--proto_path=${root.absolutePath}",
            )
            args(relativeProtoFiles)
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")
}
