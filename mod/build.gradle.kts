plugins {
    id("fabric-loom")
    `java`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val minecraftVersion = property("minecraft_version") as String
val yarnMappings = property("yarn_mappings") as String
val fabricLoaderVersion = property("fabric_loader_version") as String
val fabricApiVersion = property("fabric_api_version") as String

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")
    mappings("net.fabricmc:yarn:${yarnMappings}:v2")
    modImplementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricApiVersion}")

    // Bundle common into the mod jar (compile + runtime + bundled into remapped jar).
    include(implementation(project(":common"))!!)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.withType<Jar>().configureEach {
    archiveBaseName = "packweaver-mod"
}

// 确保 remapJar 也用 packweaver-mod 前缀
tasks.named("remapJar") {
    archiveBaseName = "packweaver-mod"
}

tasks.processResources {
    val projectVersion = project.version as String
    inputs.property("version", projectVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to projectVersion)
    }
}
