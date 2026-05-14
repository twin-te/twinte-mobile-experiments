plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("pbandkGeneration") {
            id = "net.twinte.pbandk-generation"
            implementationClass = "net.twinte.buildlogic.PbandkGenerationPlugin"
        }
    }
}
