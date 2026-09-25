plugins { `java-library`; alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies { api(project(":obd")); testImplementation(libs.junit) }
