plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace = "com.example.lumosonic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.lumosonic"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
    buildFeatures {
        viewBinding = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        aidl = true
    }
}

dependencies {

    // gson
    implementation ("com.google.code.gson:gson:2.8.6")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    // Gson转换器,用于JSON解析
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp日志拦截器
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    // permissionx
    implementation ("com.guolindev.permissionx:permissionx:1.7.1")

    // lottie动画
    implementation("com.airbnb.android:lottie:6.4.0")

    // 新拟态
    implementation("com.github.fornewid:neumorphism:0.3.2")

    // glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // 媒体库
    implementation("androidx.media:media:1.7.0")

    // 列表
    implementation ("androidx.recyclerview:recyclerview:1.3.2")

    // 谷歌材质
    implementation ("com.google.android.material:material:1.12.0")

    // exoplayer播放器
    implementation ("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")

    // room
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")  

    // acrcloud听歌识曲
    implementation (files("libs/acrcloud-universal-sdk-1.3.30.jar"))

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.core.ktx)
    implementation(libs.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}