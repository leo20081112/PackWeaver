plugins {
    id("io.papermc.paperweight.userdev")
    `java`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
    implementation(project(":common"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    val projectVersion = project.version as String
    inputs.property("version", projectVersion)
    filesMatching("plugin.yml") {
        expand("version" to projectVersion)
    }
}

tasks.withType<Jar>().configureEach {
    archiveBaseName = "packweaver-plugin"
}

// 把 common（纯 Java，无 Mojang 映射引用）内联进产物 jar，
// 使 reobfJar 产出的插件 jar 自包含、可在服务端独立加载。
tasks.jar {
    from(project(":common").sourceSets.main.get().output)
}
