pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven("https://repo.papermc.io/repository/maven-public/")
    }
    plugins {
        id("fabric-loom") version "1.7-SNAPSHOT"
        id("io.papermc.paperweight.userdev") version "1.7.3"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "in-game-datapack-editor"

include("common", "mod", "plugin")
