plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val startupJingleResDir = layout.buildDirectory.dir("generated/res/startupJingle")

val prepareStartupJingle = tasks.register<Copy>("prepareStartupJingle") {
    from(rootProject.file("../mobile-app/assets/high-tech-startup.mp3"))
    into(startupJingleResDir.map { it.dir("raw") })
    rename { "high_tech_startup.mp3" }
}

android {
    namespace = "com.hightechentertainment.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hightechentertainment.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "3.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("main").res.srcDir(startupJingleResDir.get().asFile)

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Compose 1.11 remains compatible with the stable Android API 36 SDK.
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(project(":flutter"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.elevenlabs:elevenlabs-android:0.13.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Bundle the existing production startup MP3 as a native Android raw resource.
tasks.named("preBuild").configure { dependsOn(prepareStartupJingle) }
