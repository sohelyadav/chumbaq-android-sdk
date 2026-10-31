import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish")
}

val sdkGroup = providers.gradleProperty("GROUP")
val sdkVersion = providers.gradleProperty("SDK_VERSION")

android {
    namespace = "com.chumbaq.messages"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            // JUnit 5 jars on the androidTest classpath each ship these.
            excludes += setOf("/META-INF/LICENSE.md", "/META-INF/LICENSE-notice.md")
        }
    }

    testOptions {
        unitTests {
            // The unit-test android.jar is stubs that throw on every call, so
            // android.util.Log takes a suite down before it reaches anything
            // worth asserting.
            isReturnDefaultValues = true
            // The dialog inflates AppCompat views, which resolve themed
            // drawables out of the merged resource table. Without this,
            // Robolectric has no table to resolve them from and every test
            // that shows a dialog dies on a missing resource id.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":chumbaq-core"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.core.ktx)
    implementation(libs.coil)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // The stub android.jar has no working org.json, and parsing a check
    // response is most of what these tests do.
    testImplementation(libs.json)
    // The dialog is the half of this module that only a rendered view can
    // check — a button that fails to close the message looks fine in source.
    testImplementation(libs.robolectric)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(sdkGroup.get(), "chumbaq-messages", sdkVersion.get())
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))

    pom {
        name.set("Chumbaq Android SDK Messages")
        description.set("In-app messages module for Chumbaq Android SDK")
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
