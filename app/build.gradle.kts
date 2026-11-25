import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 起内置 Kotlin 支持，不再需要 org.jetbrains.kotlin.android
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 发布签名：keystore.properties 与 .jks 都不进仓库，换机器时要一起带过去。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "dev.orgtd"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "dev.orgtd"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.2.2"
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":orgmode"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.mockwebserver)
}
