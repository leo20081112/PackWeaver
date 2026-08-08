// Root build script: only project coordinates and shared repositories.
// No java plugin applied here; submodules apply their own plugins.
allprojects {
    group = "com.dpe"
    version = "0.4.0"
}

subprojects {
    repositories {
        mavenCentral()
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
