// Shared build script for every Stonecutter version node (":26.1", ":26.2").
// Node project directories live under versions/<node> and only hold version-specific
// files (fabric.mod.json); all shared sources stay in the root src/ folder.
val node = project.path.removePrefix(":")

// Exact Maven versions per node. 26.1 publishes patch versions (26.1.2), while the
// 26.2 line is referenced without a patch suffix.
val minecraftVersion = when (node) {
    "26.1" -> "26.1.2"
    "26.2" -> "26.2"
    else -> error("Unknown Stonecutter version node: '${project.path}'")
}
val fabricApiVersion = "0.155.2+$minecraftVersion"

plugins {
    java
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
}

group = "com.skyziyou"
version = "1.2.1"

base {
    archivesName.set("Skyziyou-Fabric+mc$node")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:1.13.13+kotlin.2.4.10")
}
