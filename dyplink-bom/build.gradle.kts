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
        api("${sdkGroup.get()}:dyplink-core:${sdkVersion.get()}")
        api("${sdkGroup.get()}:dyplink-push:${sdkVersion.get()}")
        api("${sdkGroup.get()}:dyplink-banners:${sdkVersion.get()}")
        api("${sdkGroup.get()}:dyplink-messages:${sdkVersion.get()}")
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(sdkGroup.get(), "dyplink-bom", sdkVersion.get())
    configure(JavaPlatform())

    pom {
        name.set("Dyplink Android SDK BOM")
        description.set("Bill of Materials for aligning Dyplink Android SDK module versions")
        url.set("https://github.com/sohelyadav/dyplink-android-sdk")

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
            connection.set("scm:git:git://github.com/sohelyadav/dyplink-android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/sohelyadav/dyplink-android-sdk.git")
            url.set("https://github.com/sohelyadav/dyplink-android-sdk")
        }
    }
}

