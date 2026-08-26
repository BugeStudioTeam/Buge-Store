import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

val releaseKeystorePath = providers.environmentVariable("BUGE_STORE_KEYSTORE").orNull
    ?: keystoreProperties.getProperty("storeFile")
val releaseStorePassword = providers.environmentVariable("BUGE_STORE_STORE_PASSWORD").orNull
    ?: keystoreProperties.getProperty("storePassword")
val releaseKeyPassword = providers.environmentVariable("BUGE_STORE_KEY_PASSWORD").orNull
    ?: keystoreProperties.getProperty("keyPassword")
val releaseKeyAlias = providers.environmentVariable("BUGE_STORE_KEY_ALIAS").orNull
    ?: keystoreProperties.getProperty("keyAlias")

val releaseStoreFile = releaseKeystorePath?.let { path ->
    val moduleRelativeFile = project.file(path)
    when {
        moduleRelativeFile.isAbsolute -> moduleRelativeFile
        rootProject.file(path).isFile -> rootProject.file(path)
        else -> moduleRelativeFile
    }
}
val hasReleaseSigning = releaseStoreFile?.isFile == true &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank()
val releaseBuildRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }

android {
    namespace = "com.buge.store"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.buge.store"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"https://raw.githubusercontent.com/BugeStudioTeam/Buge-Store-API/main/api/v1/\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else if (releaseBuildRequested) {
                error(
                    "Release signing is not configured. Set BUGE_STORE_KEYSTORE, " +
                        "BUGE_STORE_STORE_PASSWORD, BUGE_STORE_KEY_PASSWORD, and BUGE_STORE_KEY_ALIAS, " +
                        "or provide a valid keystore.properties file."
                )
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.material)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
