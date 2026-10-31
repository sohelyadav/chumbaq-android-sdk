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
    namespace = "com.chumbaq.push"
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
            // android.util.Log and friends are stubs on the unit-test
            // classpath and throw rather than no-op. Logging is not what any
            // of these tests are about, so defaults let them exercise the code
            // that matters.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":chumbaq-core"))
    implementation(libs.firebase.messaging)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // The unit-test android.jar ships stubs that throw on every call, so the
    // real JSONObject has to come from somewhere for these tests to run.
    testImplementation(libs.json)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(sdkGroup.get(), "chumbaq-push", sdkVersion.get())
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))

    pom {
        name.set("Chumbaq Android SDK Push")
        description.set("Push notifications module for Chumbaq Android SDK")
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
