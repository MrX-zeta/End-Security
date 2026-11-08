import java.util.Properties

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
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Variantes (tarea 31): `demo` lleva el backend falso (DEC-61) y es la que se publica; `prod` no lo contiene
    // y queda a la espera del transporte real (`docs/api/brechas-backend.md`).
    flavorDimensions += "backend"
    productFlavors {
        create("demo") {
            dimension = "backend"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "USE_FAKE_BACKEND", "true")
            // «Modo demostración»: muestra el código de verificación en pantalla. Solo en `demo`, también en release.
            buildConfigField("boolean", "DEMO_MODE", "true")
        }
        create("prod") {
            dimension = "backend"
            buildConfigField("boolean", "USE_FAKE_BACKEND", "false")
            buildConfigField("boolean", "DEMO_MODE", "false")
        }
    }

    // Firma de release (tarea 31): lee `keystore.properties` de la raíz (storeFile, storePassword, keyAlias, keyPassword).
    // Si no existe, release queda sin firmar y la compilación no falla. Nunca se imprimen sus valores.
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val keystoreProps = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
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

// Tarea 22: los textos legales viven solo en `docs/legal/`; se copian a los assets antes de compilar para no mantener dos copias.
// La copia no se versiona (`.gitignore`).
val copyLegalDocs by tasks.registering(Copy::class) {
    from(rootProject.file("docs/legal")) { include("aviso-de-privacidad.md", "terminos-y-condiciones.md") }
    into(layout.projectDirectory.dir("src/main/assets/legal"))
}

tasks.named("preBuild") { dependsOn(copyLegalDocs) }
