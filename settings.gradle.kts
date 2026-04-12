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
    }
}

rootProject.name = "dyplink-android-sdk"

include(":dyplink-core")
include(":dyplink-push")
include(":dyplink-banners")
include(":dyplink-messages")
include(":dyplink-bom")
include(":sample")
