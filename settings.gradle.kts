pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ElmDashboard"
include(":app", ":elm", ":obd", ":trip", ":connection", ":dashboard", ":auto", ":dashboard-graphics", ":auto-media")
