plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "hu.elmdash.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "hu.elmdash.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.13.0"
    }
    flavorDimensions += "surface"
    productFlavors {
        create("phone") { dimension = "surface" }
        create("media") {
            dimension = "surface"
            applicationIdSuffix = ".media"
            versionNameSuffix = "-aa-media"
        }
        create("unsupported") {
            dimension = "surface"
            applicationIdSuffix = ".unsupported"
            versionNameSuffix = "-auto-lab"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
// The experimental category declaration must never ship in a release APK.
androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        if (it.productFlavors.any { pair -> pair.second in setOf("unsupported", "media") }) it.enable = false
    }
}
configurations.maybeCreate("unsupportedDebugImplementation")
configurations.maybeCreate("mediaDebugImplementation")
dependencies {
    implementation(project(":connection"))
    implementation(project(":dashboard"))
    "unsupportedDebugImplementation"(project(":auto"))
    "mediaDebugImplementation"(project(":auto-media"))
    implementation(libs.androidx.activity)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material)
}
