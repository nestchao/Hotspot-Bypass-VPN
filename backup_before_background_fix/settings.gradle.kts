pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Try the standard jitpack again with a fresh proxy setting
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Hotspot-Bypass-VPN"
include(":app")