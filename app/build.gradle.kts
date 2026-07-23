import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.schwanitz"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.schwanitz"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProps = Properties().apply {
            val f = project.rootProject.file("local.properties")
            if (f.exists()) load(f.inputStream())
        }
        fun envOrLocal(envKey: String, localKey: String): String =
            System.getenv(envKey) ?: localProps.getProperty(localKey) ?: ""
        val discogsKey = envOrLocal("DISCOGS_CONSUMER_KEY", "discogsKey")
        val discogsSecret = envOrLocal("DISCOGS_CONSUMER_SECRET", "discogsSecret")
        buildConfigField("String", "DISCOGS_CONSUMER_KEY", "\"$discogsKey\"")
        buildConfigField("String", "DISCOGS_CONSUMER_SECRET", "\"$discogsSecret\"")
        val lastfmKey = envOrLocal("LASTFM_API_KEY", "lastfmKey")
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmKey\"")
        val geniusAccessToken = envOrLocal("GENIUS_ACCESS_TOKEN", "geniusAccessToken")
        buildConfigField("String", "GENIUS_ACCESS_TOKEN", "\"$geniusAccessToken\"")
    }

    buildTypes {
        release {
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.reorderable)
    implementation("com.github.ansch190:Tagix:android-SNAPSHOT")
    implementation("org.slf4j:slf4j-android:1.7.36")
    implementation(libs.jsoup)
    implementation(libs.timber)
    implementation(libs.security.crypto)
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}