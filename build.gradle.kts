plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.3.1"
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    implementation("com.github.retrooper:packetevents-spigot:2.14.0")
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
        minecraftVersion("26.3")
        jvmArgs("-Xms2G", "-Xmx2G")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = System.getenv("GROUP") ?: project.group.toString()
            artifactId = "NoteblocksLive"
            version = System.getenv("VERSION") ?: project.version.toString()

            from(components["shadow"])
        }
    }
}