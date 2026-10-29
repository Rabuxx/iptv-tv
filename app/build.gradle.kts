import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------- Version ----------
// En CI, VERSION_NAME vient du tag Git (v0.3.1 → 0.3.1). En local, de gradle.properties.
val appVersion: String = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: (project.findProperty("iptv.version") as String? ?: "0.0.0")

// Code de version toujours croissant, bêtas comprises (même formule que Versions.versionCode) :
// 0.7.0-beta.2 → 70002, 0.7.0 → 70099, 0.7.1 → 70199
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

// ---------- Clés de services (facultatives) ----------
// En CI : secrets GitHub TMDB_API_KEY et SENTRY_DSN. En local : local.properties (non versionné).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String =
    (System.getenv(name) ?: localProps.getProperty(name) ?: "").replace("\"", "").trim()

// ---------- Signature ----------
// Une clé stable est indispensable pour que les mises à jour s'installent par-dessus l'ancienne version.
// Source 1 : variables d'environnement (CI). Source 2 : fichier keystore.properties à la racine (local).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystorePath: String? = System.getenv("IPTV_KEYSTORE") ?: keystoreProps.getProperty("storeFile")
val hasReleaseKey = keystorePath != null && rootProject.file(keystorePath).exists()

android {
    namespace = "fr.rabuxx.iptvtv"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.rabuxx.iptvtv"
        minSdk = 23
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion

        val updateRepo = project.findProperty("iptv.updateRepo") as String? ?: "Rabuxx/iptv-tv"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${secret("TMDB_API_KEY")}\"")
        buildConfigField("String", "SENTRY_DSN", "\"${secret("SENTRY_DSN")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            // Même clé qu'en release quand elle est disponible : on peut alterner builds locaux et APK de la CI
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

    // Le rapport lint est publié par la CI ; il n'empêche pas une Release
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Lecteur vidéo
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Réseau + images
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Rangée « À suivre » de l'écran d'accueil Android TV
    implementation("androidx.tvprovider:tvprovider:1.0.0")

    // QR code (connexion depuis le téléphone)
    implementation("com.google.zxing:core:3.5.3")

    // Rapports de plantage (actifs seulement si un DSN Sentry est fourni)
    implementation("io.sentry:sentry-android:7.14.0")

    // Tests unitaires (JVM)
    testImplementation("junit:junit:4.13.2")

    // Tests d'interface (émulateur, en CI)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
