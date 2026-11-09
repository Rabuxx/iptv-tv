import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Même numéro de version et même clé de signature que Rabux TV (voir app/build.gradle.kts)
val appVersion: String = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: (project.findProperty("iptv.version") as String? ?: "0.0.0")

val appVersionCode: Int = run {
    val core = appVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val base = core.getOrElse(0) { 0 } * 10_000 + core.getOrElse(1) { 0 } * 100 + core.getOrElse(2) { 0 }
    val pre = if ('-' in appVersion) {
        (appVersion.substringAfterLast('.').toIntOrNull() ?: 1).coerceIn(1, 98)
    } else {
        99
    }
    (base * 100 + pre).coerceAtLeast(1)
}

// Clé TMDB (facultative) : secret GitHub TMDB_API_KEY en CI, local.properties en local
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tmdbKey: String = (System.getenv("TMDB_API_KEY") ?: localProps.getProperty("TMDB_API_KEY") ?: "").replace("\"", "").trim()

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystorePath: String? = System.getenv("IPTV_KEYSTORE") ?: keystoreProps.getProperty("storeFile")
val hasReleaseKey = keystorePath != null && rootProject.file(keystorePath).exists()

android {
    namespace = "fr.rabuxx.home"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.rabuxx.home"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbKey\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystorePath!!)
                storePassword = System.getenv("IPTV_KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
                keyAlias = System.getenv("IPTV_KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias")
                keyPassword = System.getenv("IPTV_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        val signing = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = false
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
}
