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

rootProject.name = "chumbaq-android-sdk"

include(":chumbaq-core")
include(":chumbaq-push")
include(":chumbaq-banners")
include(":chumbaq-messages")
include(":chumbaq-bom")
include(":sample")
