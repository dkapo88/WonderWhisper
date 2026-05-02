import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Compose compiler plugin for Kotlin 2.2+
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun quoteBuildConfig(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val groqProxyBaseUrl = (
    localProperties.getProperty("groq.proxy.baseUrl")
        ?: providers.gradleProperty("groq.proxy.baseUrl").orNull
        ?: ""
).trim().trimEnd('/')

val groqProxyAppToken = (
    localProperties.getProperty("groq.proxy.appToken")
        ?: providers.gradleProperty("groq.proxy.appToken").orNull
        ?: ""
).trim()

android {
    namespace = "com.slumdog88.dictationkeyboardai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.slumdog88.dictationkeyboardai"
        minSdk = 24
        targetSdk = 35
        versionCode = 51
        versionName = "10.7.3"

        buildConfigField("String", "GROQ_PROXY_BASE_URL", quoteBuildConfig(groqProxyBaseUrl))
        buildConfigField("String", "GROQ_PROXY_APP_TOKEN", quoteBuildConfig(groqProxyAppToken))

        // testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // Enable Jetpack Compose
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE-LGPL-3.txt",
                "META-INF/LICENSE-LGPL-2.1.txt",
                "META-INF/LICENSE-W3C-TEST",
                "META-Tweak",
                "META-INF/DEPENDENCIES"
            )
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // tasks.withType<Test> {
    //     enabled = true
    // }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Activity (non-Compose helpers)
    implementation("androidx.activity:activity:1.9.0")

    // Jetpack Compose (use BOM to align versions)
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    debugImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    // Compose Google Fonts runtime provider
    implementation("androidx.compose.ui:ui-text-google-fonts")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.savedstate:savedstate:1.2.1")

    // For Whisper API integration
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // For encrypted storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // For Markdown rendering
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")

    // For Markdown to HTML conversion (rich clipboard)
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")

    // YAML-driven layouts / serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.charleskorn.kaml:kaml:0.60.0")

    // Unit test dependencies
    testImplementation(libs.junit)

    // Instrumented test dependencies
    androidTestImplementation(libs.androidx.junit)
    // VAD Library (2.0.10+ required for 16 KB page size support)
    implementation("com.github.gkonovalov.android-vad:silero:2.0.10")
}
