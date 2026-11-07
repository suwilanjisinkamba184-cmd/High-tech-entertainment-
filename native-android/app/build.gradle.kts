plugins {
    id("com.android.application")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":flutter"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.elevenlabs:elevenlabs-android:0.13.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

// Bundle the existing production startup MP3 as a native Android raw resource.
tasks.named("preBuild").configure { dependsOn(prepareStartupJingle) }
