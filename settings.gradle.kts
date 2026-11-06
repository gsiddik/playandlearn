pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral(); google() }
    // Versi plugin dideklarasikan di sini (lazy): :core dapat dibangun tanpa Android SDK/AGP.
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("com.android.application") version "8.5.2"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral(); google() }
}
rootProject.name = "playandlearn"
include(":core")
// Modul :app butuh Android SDK; hanya disertakan bila SDK tersedia (mis. di CI atau Android Studio).
val hasSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
if (hasSdk) include(":app")
