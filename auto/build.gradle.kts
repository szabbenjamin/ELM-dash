plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android {
    namespace = "hu.elmdash.auto"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":connection"))
    implementation(project(":dashboard-graphics"))
    implementation(libs.car.app)
    implementation(libs.car.projected)
    implementation(libs.androidx.lifecycle.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.car.testing)
}
