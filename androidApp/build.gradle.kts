import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.googleid)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.ktor.client.core)
}

android {
    namespace = "net.twinte.mobile_experiments"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "net.twinte.mobile_experiments"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField(
            "String",
            "TWINTE_GOOGLE_SERVER_CLIENT_ID",
            "\"${googleServerClientId()}\"",
        )
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "TWINTE_APP_BASE_URL",
                "\"${appBaseUrl("https://app.stg.twinte.net")}\"",
            )
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "TWINTE_APP_BASE_URL",
                "\"${appBaseUrl("https://app.twinte.net")}\"",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

fun googleServerClientId(): String =
    providers.gradleProperty("twinteGoogleServerClientId")
        .orElse(providers.environmentVariable("TWINTE_GOOGLE_SERVER_CLIENT_ID"))
        .orElse(providers.provider { localProperty("twinteGoogleServerClientId") })
        .get()
        .replace("\"", "\\\"")

fun appBaseUrl(defaultUrl: String): String =
    listOfNotNull(
        providers.gradleProperty("twinteAppBaseUrl").orNull,
        providers.environmentVariable("TWINTE_APP_BASE_URL").orNull,
        localProperty("twinteAppBaseUrl"),
    )
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .trimEnd('/')
        .ifBlank { defaultUrl }
        .replace("\"", "\\\"")

fun localProperty(name: String): String {
    val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties").asFile
    if (!localPropertiesFile.isFile) return ""

    return localPropertiesFile.inputStream().use { input ->
        Properties().apply { load(input) }.getProperty(name).orEmpty()
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
