plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.luis.end_security"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.luis.end_security"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // DEC-37: R8 (minificación, ofuscación y recorte de recursos) en release.
            optimization {
                enable = true
            }
        }
        // Solo para medir Argon2 (PEND-01): como release pero instrumentable. No depurable (ART
        // optimiza igual que en release), firmado con la llave de depuración y sin R8, para que
        // no quite el código que usa la prueba. No se publica.
        create("benchmark") {
            initWith(getByName("release"))
            // Otra app (otro id): no puede actualizar ni ser actualizada por release o debug.
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            optimization {
                enable = false
            }
        }
    }
    // Las pruebas instrumentadas usan debug; con -PbenchmarkBuild se corren contra benchmark.
    if (project.hasProperty("benchmarkBuild")) {
        testBuildType = "benchmark"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG: rutas de depuración que R8 quita en release.
        buildConfig = true
    }
}

// Esquemas de Room versionados (exportSchema = true): app/schemas/.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.zxcvbn)
    // DEC-28 / PEND-01: Argon2id y HKDF (API ligera de Bouncy Castle, Java puro).
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}