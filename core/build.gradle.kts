// Pure-Kotlin module: no Android dependency, so it builds and tests without the Android SDK.
// Everything here is the logic the architecture doc calls out as the risky part —
// parsing, dedup, categorization, budget thresholds — kept framework-free on purpose.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.coroutines.core)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    // Gradle 9 no longer puts this on the test classpath implicitly — without it, `gradle test`
    // fails at runtime with "Failed to load JUnit Platform" even though everything compiles.
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
