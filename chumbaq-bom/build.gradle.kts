import com.vanniktech.maven.publish.JavaPlatform
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `java-platform`
    id("com.vanniktech.maven.publish")
}

val sdkGroup = providers.gradleProperty("GROUP")
val sdkVersion = providers.gradleProperty("SDK_VERSION")

dependencies {
    constraints {
        api("${sdkGroup.get()}:chumbaq-core:${sdkVersion.get()}")
        api("${sdkGroup.get()}:chumbaq-push:${sdkVersion.get()}")
        api("${sdkGroup.get()}:chumbaq-banners:${sdkVersion.get()}")
        api("${sdkGroup.get()}:chumbaq-messages:${sdkVersion.get()}")
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(sdkGroup.get(), "chumbaq-bom", sdkVersion.get())
    configure(JavaPlatform())

    pom {
        name.set("Chumbaq Android SDK BOM")
        description.set("Bill of Materials for aligning Chumbaq Android SDK module versions")
        url.set("https://github.com/sohelyadav/chumbaq-android-sdk")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("sohelyadav")
                name.set("Sohel Yadav")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/sohelyadav/chumbaq-android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/sohelyadav/chumbaq-android-sdk.git")
            url.set("https://github.com/sohelyadav/chumbaq-android-sdk")
        }
    }
}

