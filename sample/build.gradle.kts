import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Push needs a Firebase config. google-services.json is gitignored, so the plugin
// is applied only when one has been dropped into sample/; without it the sample
// still builds and runs, just with push turned off.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Debug builds read the Chumbaq API they talk to from local.properties (gitignored),
// so a local platform can be used without editing code:
//   chumbaq.baseUrl=http://10.0.2.2:3000   (the emulator's address for this Mac)
//   chumbaq.apiKey=<project API key>
//   chumbaq.projectId=<project id>
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String, default: String): String =
    (localProps.getProperty(key) ?: default).replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.chumbaq.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chumbaq.sample"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "CHUMBAQ_BASE_URL", "\"${localProp("chumbaq.baseUrl", "http://10.0.2.2:3000")}\"")
            buildConfigField("String", "CHUMBAQ_API_KEY", "\"${localProp("chumbaq.apiKey", "your-api-key-here")}\"")
            buildConfigField("String", "CHUMBAQ_PROJECT_ID", "\"${localProp("chumbaq.projectId", "your-project-id")}\"")
        }
        release {
            buildConfigField("String", "CHUMBAQ_BASE_URL", "\"https://api.chumbaq.com\"")
            buildConfigField("String", "CHUMBAQ_API_KEY", "\"your-api-key-here\"")
            buildConfigField("String", "CHUMBAQ_PROJECT_ID", "\"your-project-id\"")
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
}

dependencies {
    implementation(project(":chumbaq-core"))
    implementation(project(":chumbaq-push"))
    implementation(project(":chumbaq-banners"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime.ktx)
}
