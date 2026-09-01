plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.drinkless.tdlib.android"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.kotlin.stdlib)
}
