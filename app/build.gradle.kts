import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.paparazzi)
}

// The version comes from the release tag (CI passes -Pelsewhere.version=0.9.0); a local build is 0.0.0.
val appVersion: String = (findProperty("elsewhere.version") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "0.0.0"

/** "1.2.3" → 1020399, "2.0.0-alpha.3" → 2000003, so every release installs over the one before and pre-releases sit below their final. */
fun versionCodeOf(v: String): Int {
    val (base, pre) = v.split("-", limit = 2) + listOf("")
    val (a, b, c) = base.split(".").map { it.toInt() } + listOf(0, 0, 0)
    val n = pre.substringAfterLast('.', "").toIntOrNull() ?: 0
    return (a * 1_000_000 + b * 10_000 + c * 100 + (if (pre.isEmpty()) 99 else n.coerceIn(0, 98))).coerceAtLeast(1)
}

// One keystore for every published APK, so an upgrade installs in place and keeps the phone's recorded data.
// CI writes it to ELSEWHERE_KEYSTORE; without it the release build is left unsigned.
val keystorePath: String? = System.getenv("ELSEWHERE_KEYSTORE")?.takeIf { File(it).isFile }

// Google's Maps SDK reads its key from the manifest, so it belongs to whoever builds the APK, not the user:
// -Pelsewhere.mapsSdkKey=… or MAPS_SDK_KEY (CI). Without one the map falls back to tiles or the free style.
val mapsSdkKey: String = (findProperty("elsewhere.mapsSdkKey") as String?)?.takeIf { it.isNotBlank() } ?: System.getenv("MAPS_SDK_KEY").orEmpty()

// The web OAuth client ID, as the token audience the account service checks; `googleClientId` in local.properties (CI: GOOGLE_CLIENT_ID).
// Empty hides "Continue with Google", so a plain checkout still builds and runs.
val googleClientId: String = Properties().apply { rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) } }
    .getProperty("googleClientId")?.trim()?.takeIf { it.isNotEmpty() } ?: System.getenv("GOOGLE_CLIENT_ID").orEmpty()

android {
    namespace = "com.elsewhere.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.elsewhere.app"
        minSdk = 30
        targetSdk = 35
        versionName = appVersion
        versionCode = versionCodeOf(appVersion)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["mapsSdkKey"] = mapsSdkKey
        buildConfigField("boolean", "HAS_MAPS_SDK", mapsSdkKey.isNotBlank().toString())
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${googleClientId.replace("\"", "\\\"")}\"")
    }
    signingConfigs {
        if (keystorePath != null) create("elsewhere") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("ELSEWHERE_KEYSTORE_PASSWORD")
            keyAlias = "elsewhere"
            keyPassword = System.getenv("ELSEWHERE_KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("elsewhere")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.useJUnitPlatform() } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.foundation); implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview); debugImplementation(libs.compose.ui.tooling)
    implementation(libs.core.ktx); implementation(libs.activity.compose); implementation(libs.lifecycle.vm.compose); implementation(libs.lifecycle.runtime.compose)
    implementation(libs.nav.compose)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler); implementation(libs.hilt.nav.compose)
    implementation(libs.hilt.work); ksp(libs.hilt.work.compiler)
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.datastore); implementation(libs.exifinterface)
    implementation(libs.serialization.json); implementation(libs.coroutines.android)
    implementation(libs.retrofit); implementation(libs.retrofit.serialization); implementation(libs.okhttp)
    implementation(libs.coil.compose); implementation(libs.work.runtime)
    implementation(libs.maplibre.compose); implementation(libs.play.location)
    implementation(libs.play.maps); implementation(libs.maps.compose)
    implementation(libs.tink.android); implementation(libs.zxing.core); implementation(libs.play.code.scanner)
    implementation(libs.credentials); implementation(libs.credentials.play); implementation(libs.googleid)
    testImplementation(libs.junit5.api); testImplementation(libs.junit5.params); testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.3")
    testImplementation(libs.coroutines.test); testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit4); testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom)); testImplementation(libs.compose.ui.test); debugImplementation(libs.compose.ui.test.manifest)
}
