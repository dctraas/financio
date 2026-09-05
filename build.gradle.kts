// Top-level build file: declares plugin versions once, applied per-module below.
// No kotlin.android alias: removed together with its use in app/build.gradle.kts and its
// [plugins] catalog entry (AGP 9.0+ built-in Kotlin support makes the plugin a hard error).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
