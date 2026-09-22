plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.dgpack.chamcong"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dgpack.chamcong"
        // Thiết bị thực tế tại nhà máy chạy Android 14+, nhưng giữ minSdk 26
        // (Android 8.0) để không khoá cứng nếu sau này đổi sang tablet đời cũ hơn.
        minSdk = 26
        targetSdk = 34
        // CI truyền -PciBuildNumber=<github.run_number> để mỗi bản build từ GitHub Actions
        // có versionCode/versionName riêng biệt, tự hiện trên màn hình camera (xem
        // CameraScreen.kt) — giúp xác nhận thiết bị đang chạy đúng bản mới nhất, tránh
        // nhầm với file .apk cũ còn nằm trong thư mục Download.
        val ciBuildNumber = (project.findProperty("ciBuildNumber") as String?)?.toIntOrNull() ?: 0
        versionCode = if (ciBuildNumber > 0) ciBuildNumber else 1
        versionName = if (ciBuildNumber > 0) "1.0.0-build$ciBuildNumber" else "1.0.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // Khai báo tường minh 1 file keystore cố định trong project (app/debug.keystore,
            // sinh bằng keytool trong CI nếu chưa có — xem build-apk.yml) thay vì để AGP tự
            // dùng keystore mặc định ẩn của máy build. Máy build (GitHub Actions) là VM mới
            // mỗi lần chạy nên nếu không cố định file này, MỖI build sẽ bị ký bằng 1 key khác
            // nhau -> Android từ chối cài "update" đè lên bản cũ (đã xảy ra thực tế).
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Model .tflite không được nén trong APK — bắt buộc để TFLite Interpreter
        // có thể memory-map trực tiếp file (mmap) khi load.
        jniLibs {
            useLegacyPackaging = false
        }
    }

    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    // Core / Kotlin
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Bộ icon đầy đủ (thẻ từ, huy hiệu, lịch, tiền…) cho màn chấm công thẻ / quản trị.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Face Detection (on-device, miễn phí, không cần internet)
    implementation("com.google.mlkit:face-detection:16.1.7")

    // TensorFlow Lite (MobileFaceNet embedding)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Room (SQLite local)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager (đồng bộ định kỳ)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Jetpack Security — lưu API key mã hoá, không hardcode
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
