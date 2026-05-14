val rootVersionCatalog = settingsDir.parentFile.resolve("gradle/libs.versions.toml")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files(rootVersionCatalog))
        }
    }
}

rootProject.name = "build-logic"
