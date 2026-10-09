plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17.17"
    kotlin("jvm") version "2.4.10"
}

group = "com.skyziyou"
version = "1.1.0"

base {
    archivesName.set("Skyziyou-Fabric+mc26.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

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

dependencies {
    minecraft("com.mojang:minecraft:26.1.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.155.2+26.1.2")
    implementation("net.fabricmc:fabric-language-kotlin:1.13.13+kotlin.2.4.10")
}
