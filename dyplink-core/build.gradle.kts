import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    id("com.vanniktech.maven.publish")
}

val sdkGroup = providers.gradleProperty("GROUP")
val sdkVersion = providers.gradleProperty("SDK_VERSION")

android {
    namespace = "com.dyplink.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    testOptions {
        unitTests {
            // The unit-test android.jar is stubs that throw on every call, so
            // android.util.Log takes a suite down before it reaches anything
            // worth asserting.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.install.referrer)
    implementation(libs.startup.runtime)
    implementation(libs.annotation)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    // Real JSONObject: the stubbed one throws instead of parsing, which is
    // most of what these tests do.
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.mockk.android)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(sdkGroup.get(), "dyplink-core", sdkVersion.get())
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))

    pom {
        name.set("Dyplink Android SDK Core")
        description.set("Core module for Dyplink Android SDK")
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
