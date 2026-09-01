import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun boundedIntProperty(name: String, defaultValue: Int): Int =
    providers.gradleProperty(name).orElse(defaultValue.toString()).get().toInt()

fun booleanProperty(name: String, defaultValue: Boolean): Boolean =
    providers.gradleProperty(name).orElse(defaultValue.toString()).get().let { value ->
        require(value == "true" || value == "false") { "$name must be true or false" }
        value.toBoolean()
    }

val playerCandidateId = providers.gradleProperty("cvfPlayerCandidate")
    .orElse("12D-FINAL")
    .get()
    .also { value ->
        require(value.matches(Regex("[A-Za-z0-9_-]+"))) {
            "cvfPlayerCandidate must contain only letters, numbers, '_' or '-'"
        }
    }
val playerStartupMillis = boundedIntProperty("cvfPlayerStartupMs", 2_500)
val playerPrioritizeTime = booleanProperty("cvfPlayerPrioritizeTime", true)
val playerBackBufferMillis = boundedIntProperty("cvfPlayerBackBufferMs", 0)
val playerTargetBufferBytes = boundedIntProperty("cvfPlayerTargetBufferBytes", -1)
val playerPlayBeforePrepare = booleanProperty("cvfPlayerPlayBeforePrepare", false)
val telegramHlsEnabled = booleanProperty("cvfTelegramHlsEnabled", true)
val hybridAbrEnabled = booleanProperty("cvfHybridAbrEnabled", true)
val dynamicNextPreloadEnabled = booleanProperty("cvfDynamicNextPreloadEnabled", true)
val sampleQueuePreloadEnabled = booleanProperty("cvfSampleQueuePreloadEnabled", false)
val startupRangeCandidate = providers.gradleProperty("cvfStartupRangeCandidate")
    .orElse("BASELINE")
    .get()
    .also { value ->
        require(value in setOf("BASELINE", "TAIL_64", "TAIL_128", "HEAD_512_WIFI")) {
            "cvfStartupRangeCandidate must be BASELINE, TAIL_64, TAIL_128, or HEAD_512_WIFI"
        }
    }

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.qixuan.channelvideoflow.player"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PLAYBACK_TUNING_CANDIDATE", "\"$playerCandidateId\"")
        buildConfigField("int", "PLAYBACK_STARTUP_BUFFER_MILLIS", playerStartupMillis.toString())
        buildConfigField(
            "boolean",
            "PLAYBACK_PRIORITIZE_TIME_OVER_SIZE",
            playerPrioritizeTime.toString(),
        )
        buildConfigField("int", "PLAYBACK_BACK_BUFFER_MILLIS", playerBackBufferMillis.toString())
        buildConfigField("int", "PLAYBACK_TARGET_BUFFER_BYTES", playerTargetBufferBytes.toString())
        buildConfigField(
            "boolean",
            "PLAYBACK_PLAY_BEFORE_PREPARE",
            playerPlayBeforePrepare.toString(),
        )
        buildConfigField(
            "String",
            "STARTUP_RANGE_CANDIDATE",
            "\"$startupRangeCandidate\"",
        )
        buildConfigField("boolean", "TELEGRAM_HLS_ENABLED", telegramHlsEnabled.toString())
        buildConfigField("boolean", "HYBRID_ABR_ENABLED", hybridAbrEnabled.toString())
        buildConfigField(
            "boolean",
            "DYNAMIC_NEXT_PRELOAD_ENABLED",
            dynamicNextPreloadEnabled.toString(),
        )
        buildConfigField(
            "boolean",
            "SAMPLE_QUEUE_PRELOAD_ENABLED",
            sampleQueuePreloadEnabled.toString(),
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource)
    api(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
