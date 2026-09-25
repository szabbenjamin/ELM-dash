plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android {
    namespace = "hu.elmdash.media"
    compileSdk = 36
    buildFeatures { compose = true }
    defaultConfig { minSdk = 26 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":connection"))
    implementation(project(":dashboard-graphics"))
    implementation(libs.car.app)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.androidx.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material)
    implementation(libs.androidx.lifecycle.compose)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
}
