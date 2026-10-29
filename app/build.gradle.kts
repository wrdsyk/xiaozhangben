plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.jizhang"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jizhang"
        minSdk = 26
        targetSdk = 37
        // "owner/repo" whose GitHub Releases the in-app updater checks (gradle.properties); blank = off.
        buildConfigField("String", "UPDATE_REPO", "\"" + (project.findProperty("jizhang.updateRepo") ?: "") + "\"")
        versionCode = 15
        versionName = "0.5.10"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal app, never published: sign with this machine's debug key so the APK installs
            // directly. Later builds must come from the same machine (or a copy of
            // ~/.android/debug.keystore) to install as updates without losing data.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Release lint needs a few hundred MB of extra tooling that keeps failing to download from
    // this machine; the debug build is linted in the IDE anyway.
    lint {
        checkReleaseBuilds = false
    }
}

composeCompiler {
    // The mapping file only helps decode release stack traces; skipping it avoids another download.
    includeComposeMappingFile = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.glass)
    implementation(libs.vico.compose)
    implementation(libs.compose.charts)

    testImplementation(libs.junit)
}
