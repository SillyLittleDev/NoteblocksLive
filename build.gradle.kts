plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.3.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation("com.github.retrooper:packetevents-spigot:2.13.0")
    implementation("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    shadowJar {
        relocate(
            "com.github.retrooper.packetevents",
            "com.dev.nbl.libs.packetevents.api"
        )

        relocate(
            "io.github.retrooper.packetevents",
            "com.dev.nbl.libs.packetevents.impl"
        )

        relocate(
            "com.google.gson",
            "com.dev.nbl.libs.packetevents.gson"
        )

        archiveClassifier.set("")
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    runServer {
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }
}