plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta8"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.14"
}

group = "com.jacky8399"
version = "1.14"

repositories {
    mavenCentral()
    maven {
        name = "spigotmc-repo"
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.extendedclip.com/releases/")
    mavenLocal()
}

configurations {
    testImplementation {
        extendsFrom(configurations.compileOnly.get())
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:23.0.0")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.6")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("org.bstats:bstats-bukkit:2.2.1")

    implementation("net.kyori:adventure-api:4.20.0")
    implementation("net.kyori:adventure-text-serializer-bungeecord:4.3.4")
//    implementation("net.kyori:adventure-platform-bukkit:4.3.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.5.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        relocate("org.bstats", "com.jacky8399.portablebeacons.bstats")
        relocate("net.kyori", "com.jacky8399.portablebeacons.adventure")
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}