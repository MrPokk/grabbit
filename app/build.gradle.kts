import org.gradle.api.Action
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

val i18nRoot = layout.projectDirectory.dir("src/main/i18n").asFile
val defaultLanguage = "en"
val localeCodes =
    i18nRoot
        .listFiles { file: File -> file.isDirectory }
        ?.map { it.name }
        ?.sorted()
        .orEmpty()
val generatedI18nRes = layout.buildDirectory.dir("generated/i18n/res")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val generateI18nRes =
    tasks.register<Sync>("generateI18nRes") {
        from(i18nRoot) {
            include("**/*.xml")
            eachFile(
                Action<FileCopyDetails> {
                    val code = path.substringBefore('/')
                    val qualifier = code.replace("-", "-r")
                    setPath(if (code == defaultLanguage) "values/$name" else "values-$qualifier/$name")
                },
            )
        }
        into(generatedI18nRes)
    }

tasks
    .matching {
        it.name.contains("Resources") ||
            it.name.startsWith("extract") ||
            it.name.contains("SourceSetPaths")
    }.configureEach {
        dependsOn(generateI18nRes)
    }

android {
    namespace = "com.grabbit"
    compileSdk = 36

    sourceSets {
        getByName("main").res.srcDir("src/main/res")
        getByName("main").res.srcDir(generatedI18nRes)
    }

    defaultConfig {
        applicationId = "com.grabbit"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String[]",
            "SUPPORTED_LANGUAGES",
            localeCodes.joinToString(prefix = "new String[]{", postfix = "}") { "\"$it\"" },
        )

        // The bundled yt-dlp / python / ffmpeg binaries ship for these ABIs only.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
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
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/DEPENDENCIES",
                    "/META-INF/LICENSE*",
                    "/META-INF/NOTICE*",
                    "META-INF/*.kotlin_module",
                )
        }
        // youtubedl-android executes the bundled binaries from the native library dir,
        // which requires them to be extracted on install.
        jniLibs.useLegacyPackaging = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    lint {
        error += listOf("MissingTranslation", "ExtraTranslation", "PluralsCandidate")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("config/detekt.yml"))
}

ktlint {
    version.set(libs.versions.ktlint.get())
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
