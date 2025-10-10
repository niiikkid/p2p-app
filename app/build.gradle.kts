import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.room)
}

// Helper to read simple KEY=VALUE lines from .env (without external plugins)
fun readDotEnvFile(file: java.io.File): Map<String, String> {
    val result: MutableMap<String, String> = mutableMapOf()
    if (!file.exists()) return result
    file.forEachLine { rawLine: String ->
        val line: String = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val idx: Int = line.indexOf('=')
        if (idx <= 0) return@forEachLine
        val key: String = line.substring(0, idx).trim()
        var value: String = line.substring(idx + 1).trim()
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length - 1)
        }
        if (key.isNotEmpty()) result[key] = value
    }
    return result
}

android {
    namespace = "com.android.autopay"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.android.autopay"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // BuildConfig field for API host loaded from ENV/.env/local.properties (no defaults)
        val envApiHost: String? = System.getenv("API_HOST")?.trim()?.takeIf { it.isNotBlank() }
        val dotEnv: Map<String, String> = readDotEnvFile(rootProject.file(".env"))
        val dotEnvApiHost: String? = dotEnv["API_HOST"]?.trim()?.takeIf { it.isNotBlank() }
        val localProps: Properties = Properties()
        val localPropsFile: java.io.File = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { inputStream -> localProps.load(inputStream) }
        }
        val localPropsApiHost: String? = localProps.getProperty("API_HOST")?.trim()?.takeIf { candidate -> candidate.isNotBlank() }
        val apiHost: String = (envApiHost ?: dotEnvApiHost ?: localPropsApiHost)
            ?: throw GradleException("API_HOST is required. Provide it via ENV, .env or local.properties")
        buildConfigField("String", "API_HOST", "\"$apiHost\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.hilt.core)
    implementation(libs.hilt.work)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
}