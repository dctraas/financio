plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.financio.app"
    // 37, and AGP 9.1.0 in gradle/libs.versions.toml, because navigation-compose 2.10.0 and the
    // compose-bom 2026.08.00 artifacts both declare that floor in their AAR metadata — bumping
    // the libraries without this raises "checkDebugAarMetadata" failures naming exactly this.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.financio.app"
        minSdk = 26 // biometric app-lock + adaptive icons without extra fallback code
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Room schemas are exported for migration testing; see data/local/FinancioDatabase.kt.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

kotlin {
    // Must match android.compileOptions above — the Android Gradle plugin does not sync these
    // for you, so without this the Kotlin (and therefore ksp) compile tasks target whatever JDK
    // is running Gradle instead of 17, and the build refuses the mismatch. Same fix as :core.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // TopAppBar (used on every screen) is still @ExperimentalMaterial3Api in the pinned
        // Material3 version — an unacknowledged @RequiresOptIn usage is a compile error, not
        // just a warning, so this needs an explicit opt-in rather than per-file annotations.
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)

    implementation(libs.biometric)
    implementation(libs.fragment.ktx) // FragmentActivity host for BiometricPrompt
}
