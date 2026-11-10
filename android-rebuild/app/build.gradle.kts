plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val startupRes = layout.buildDirectory.dir("generated/startup-res")
val copyStartupAudio = tasks.register<Copy>("copyStartupAudio") {
    from(rootProject.file("../mobile-app/assets/high-tech-startup.mp3"))
    into(startupRes.map { it.dir("raw") })
    rename { "hte_startup.mp3" }
}
android {
    namespace = "com.hightechentertainment.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.hightechentertainment.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "4.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main").res.srcDir(startupRes.get().asFile)
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(bom)
    androidTestImplementation(bom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
tasks.named("preBuild").configure { dependsOn(copyStartupAudio) }
