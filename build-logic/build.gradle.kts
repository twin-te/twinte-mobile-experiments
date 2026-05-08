plugins {
    `kotlin-dsl`
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
