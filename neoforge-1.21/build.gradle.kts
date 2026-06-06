// NeoForge 1.21.1 platform module.
// Uses NeoGradle moddev plugin for MDK setup.

plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.115"
}

version = project.property("neoforge_1_21_1_version") as String
base {
    archivesName = project.property("mod_id") as String
}

java {
    toolchain {
        // Java 21 required for Minecraft 1.21.x
        languageVersion = JavaLanguageVersion.of(21)
    }
}

neoForge {
    // NeoForge loader version for Minecraft 1.21.1
    version = project.property("neoforge_version_1_21_1") as String

    runs {
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
        }

        create("client") {
            client()
        }
        create("server") {
            server()
        }
    }
}

dependencies {
    // NeoForge dependency is automatically handled by the moddev plugin.

    // Shared logic from common module
    implementation(project(":common"))
}

// Bundle common module classes into the mod jar
tasks.jar {
    from(project(":common").sourceSets.main.get().output) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

// Ensure common classes are on the classpath during development runs
sourceSets.main.get().compileClasspath += project(":common").sourceSets.main.get().output
sourceSets.main.get().runtimeClasspath += project(":common").sourceSets.main.get().output

// Expand ${...} placeholders in neoforge.mods.toml at build time
tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf(
        "mod_id" to project.property("mod_id") as String,
        "mod_name" to project.property("mod_name") as String,
        "mod_authors" to project.property("mod_authors") as String,
        "mod_description" to project.property("mod_description") as String,
        "neoforge_1_21_1_version" to project.property("neoforge_1_21_1_version") as String
    )
    inputs.properties(replaceProperties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replaceProperties)
    }
}
