pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Xposed API (compileOnly for the LSPosed module)
        maven { url = uri("https://api.xposed.info/") }
        // JitPack fallback for osmdroid / misc community libs
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MockPilot"

include(":app")
include(":xposed")
