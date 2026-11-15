plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.elsewhere.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.elsewhere.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.useJUnitPlatform() } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.foundation); implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview); debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose); implementation(libs.lifecycle.vm.compose); implementation(libs.lifecycle.runtime.compose)
    implementation(libs.nav.compose)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler); implementation(libs.hilt.nav.compose)
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.serialization.json); implementation(libs.coroutines.android)
    implementation(libs.retrofit); implementation(libs.retrofit.serialization); implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.maplibre.compose)
    testImplementation(libs.junit5.api); testImplementation(libs.junit5.params); testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.3")
    testImplementation(libs.coroutines.test); testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit4); testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom)); testImplementation(libs.compose.ui.test); debugImplementation(libs.compose.ui.test.manifest)
}
