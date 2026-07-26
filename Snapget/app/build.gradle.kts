import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.plugin.compose)
    id("com.google.devtools.ksp")
    // id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

// Doc local.properties de lay config may local (khong commit file nay)
val localProperties =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

android {
    namespace = "com.example.snapget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.snapget"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // URL server NestJS. Emulator dung 10.0.2.2 = localhost cua may host.
        // May that: doi trong local.properties -> server.base.url=http://<IP-LAN>:3000/api/
        val serverBaseUrl =
            localProperties.getProperty("server.base.url")
                ?: "http://10.0.2.2:3000/api/"
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.play.services.auth) // hoặc bản mới nhất
    implementation(libs.kotlinx.coroutines.play.services)

    // compose platform
    implementation(platform(libs.androidx.compose.bom))

    // ui, preview & material
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.logging.interceptor)
    implementation(libs.androidx.work.runtime.ktx)

    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.composeIcons.fontAwesome)

    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)

    implementation(libs.okhttp)

    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp.urlconnection)

    // accompanist
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.pager)

    implementation(libs.androidx.core.splashscreen)
    implementation(libs.coil.compose)

    // Phat video moment trong feed/post detail (Media3 ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    implementation(libs.coil.network.okhttp)

    // QR Code Scanning
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    // Quay video ngan <=5s (VideoCapture use case)
    implementation(libs.androidx.camera.video)
    // Doc EXIF de xoay anh dung chieu khi bake filter/doodle
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.camera.view.v131)
    implementation(libs.barcode.scanning)
    // Sinh anh QR tu ma moi ket ban (zxing chi dung de GENERATE, quet dung ML Kit)
    implementation(libs.zxing.core)

    // Guava — provides com.google.common.util.concurrent.ListenableFuture on the
    // COMPILE classpath, which CameraX exposes in its public API.
    implementation("com.google.guava:guava:32.1.3-android")

    // Firebase (BOM manages versions of the modules below)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    // Google Sign-In via Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // splashscreen
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.androidx.browser)
    implementation(libs.androidx.animation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // debug libraries
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
