pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net")
        maven("https://repo.spongepowered.org/maven/")
        // Stonecutter multi-version build plugin
        maven("https://maven.kikugie.dev/snapshots") {
            content {
                includeGroupAndSubgroups("dev.kikugie")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.7"
}

rootProject.name = "Skyziyou"

stonecutter {
    create(rootProject) {
        versions("26.1", "26.2")
        // Version a fresh checkout / CI checkout is activated to.
        vcsVersion = "26.2"
    }
}
