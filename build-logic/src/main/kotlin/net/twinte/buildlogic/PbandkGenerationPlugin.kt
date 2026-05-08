package net.twinte.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Locale

class PbandkGenerationPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
        val protobufVersion = libs.findVersion("protobuf").get().requiredVersion
        val pbandkVersion = libs.findVersion("pbandk").get().requiredVersion

        val protocBinaryConfiguration = configurations.create("pbandkProtocBinary") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        val pbandkPluginConfiguration = configurations.create("pbandkPlugin") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        dependencies.add(
            protocBinaryConfiguration.name,
            "com.google.protobuf:protoc:$protobufVersion:${protocClassifier()}@exe",
        )
        dependencies.add(
            pbandkPluginConfiguration.name,
            "pro.streem.pbandk:protoc-gen-pbandk-jvm:$pbandkVersion:jvm8@jar",
        )

        val generatedPbandkDirectory = layout.buildDirectory.dir("generated/source/pbandk/commonMain")
        val generatePbandk = tasks.register("generatePbandk", GeneratePbandkTask::class.java) {
            protoRoot.set(layout.projectDirectory.dir("src/commonMain/proto"))
            protoFiles.from(fileTree(layout.projectDirectory.dir("src/commonMain/proto")) {
                include("**/*.proto")
                exclude("shared/option.proto")
            })
            protocExecutable.from(protocBinaryConfiguration)
            pbandkPluginJar.from(pbandkPluginConfiguration)
            outputDirectory.set(generatedPbandkDirectory)
        }

        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            extensions.configure(KotlinMultiplatformExtension::class.java) {
                sourceSets.named("commonMain") {
                    kotlin.srcDir(generatedPbandkDirectory)
                }
            }
            tasks.matching { it.name.startsWith("compile") }.configureEach {
                dependsOn(generatePbandk)
            }
        }
    }

    private fun protocClassifier(): String {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        val arch = System.getProperty("os.arch").lowercase(Locale.ROOT)
        val osPart = when {
            os.contains("mac") || os.contains("darwin") -> "osx"
            os.contains("windows") -> "windows"
            os.contains("linux") -> "linux"
            else -> error("Unsupported protoc OS: $os")
        }
        val archPart = when (arch) {
            "aarch64", "arm64" -> "aarch_64"
            "x86_64", "amd64" -> "x86_64"
            else -> error("Unsupported protoc architecture: $arch")
        }
        return "$osPart-$archPart"
    }
}
