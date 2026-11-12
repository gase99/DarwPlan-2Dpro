plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "ir.cabinetplan.fa"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.cabinetplan.fa"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.environmentVariable("CABINET_VERSION_CODE").orElse("2").get().toInt()
        versionName = providers.environmentVariable("CABINET_VERSION").orElse("0.1.0-preview.1").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("fa")
    }
    signingConfigs {
        create("production") {
            val path = System.getenv("CABINET_KEYSTORE_PATH")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("CABINET_STORE_PASSWORD")
                keyAlias = System.getenv("CABINET_KEY_ALIAS")
                keyPassword = System.getenv("CABINET_KEY_PASSWORD")
                storeType = System.getenv("CABINET_STORE_TYPE") ?: "JKS"
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("production")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// Robolectric's Android runtime is fetched from the same reliable Maven Central host as Gradle.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    testLogging { events("failed", "skipped") }
}
