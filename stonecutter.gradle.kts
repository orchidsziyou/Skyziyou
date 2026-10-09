// Stonecutter controller build.
// The root project itself never applies Loom/Kotlin; every Minecraft version node
// (":26.1", ":26.2") is configured by the shared /build.gradle.kts at the repository root.
plugins {
    id("dev.kikugie.stonecutter")
    // Declare plugin versions once so the node build scripts can apply them without versions.
    id("net.fabricmc.fabric-loom") version "1.17.17" apply false
    kotlin("jvm") version "2.4.10" apply false
}

allprojects {
    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://maven.fabricmc.net") }
            filter { includeGroupAndSubgroups("net.fabricmc") }
        }
        exclusiveContent {
            forRepository { maven("https://repo.spongepowered.org/maven/") }
            filter { includeGroup("org.spongepowered") }
        }
    }
}

// Version used by IDE runs and by root-level Gradle tasks. The root source tree is
// compiled AS-IS for this version (no comment processing), so it must stay the
// newest/primary flavour; the other nodes get version comments applied.
// Switch it here when developing primarily against 26.1.
stonecutter active "26.2"
