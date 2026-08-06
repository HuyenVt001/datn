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

// URL server. Fallback HTTP chi de tien cho DEBUG tren emulator — ban RELEASE
// bi chan bang check ben duoi neu khong phai HTTPS.
val serverBaseUrl: String =
    localProperties.getProperty("server.base.url") ?: "http://10.0.2.2:3000/api/"

/**
 * Chan build RELEASE khi `server.base.url` khong phai HTTPS (hardening 2026-07-28).
 *
 * Truoc day: thieu local.properties -> app release duoc build voi
 * "http://10.0.2.2:3000/api/" => Firebase ID token di qua HTTP tran.
 * Nay fail ngay luc build thay vi phat hien khi da phat hanh.
 *
 * Kiem tra o taskGraph (khong phai trong khoi release{}) de build DEBUG voi
 * server HTTP local van chay binh thuong.
 */
gradle.taskGraph.whenReady {
    val buildingRelease = allTasks.any { it.name.contains("Release") }
    if (buildingRelease && !serverBaseUrl.startsWith("https://")) {
        throw GradleException(
            "Build RELEASE bi chan: server.base.url = '$serverBaseUrl' khong phai HTTPS.\n" +
                "Sua trong Snapget/local.properties, vi du:\n" +
                "  server.base.url=https://datn-8810.onrender.com/api/",
        )
    }
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
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // BAT R8 (2026-07-28, hardening bao mat). Truoc day isMinifyEnabled=false
            // => APK release giu nguyen ten class/method + toan bo chuoi, jadx dich
            // nguoc ra source gan nhu nguyen ban, va moi Log.d khong bi strip.
            // Rule keep day du o proguard-rules.pro (Gson dung reflection tren DTO).
            isMinifyEnabled = true
            isShrinkResources = true
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

    // Asset RIENG cua tung skin gom vao 1 thu muc/skin (res-skins/skin1_snow,
    // res-skins/skin2_forest — cau truc ben trong van la drawable/,
    // drawable-nodpi/ chuan). Khai o day de AAPT gop chung voi res/ luc build;
    // designer thay skin nao chi dung dung thu muc skin do.
    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/main/res-skins/skin1_snow",
                "src/main/res-skins/skin2_forest",
            )
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Lint phai SACH truoc khi xuat APK: `./gradlew :app:lintDebug` = 0 error,
        // 0 warning (dot don 2026-08-06). Co canh bao moi thi sua, dung de don.
        warningsAsErrors = true
        abortOnError = true

        // ==== 3 luat CO Y tat, khong phai bo qua cho tien ====
        disable +=
            setOf(
                // "Co ban thu vien moi hon" — 116 canh bao. Phien ban duoc CHOT trong
                // gradle/libs.versions.toml va bo nay da chay on dinh; nang version
                // hang loat ngay truoc khi bao ve DATN la rui ro thuan tuy, khong doi
                // lai chuc nang nao. Nang co chu dich thi sua TOML, khong phai vi lint giuc.
                "GradleDependency",
                "SimilarGradleDependency",
                "AndroidGradlePluginVersion",
            )
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

    // Widget man hinh chinh (Jetpack Glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

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
    implementation(libs.guava)

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
