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

    // Register the mod's source set so NeoGradle moddev adds compiled
    // classes and resources to the development run classpath.
    mods {
        create("stylizeddamage") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // NeoForge dependency is automatically handled by the moddev plugin.

    // Shared logic from common module
    implementation(project(":common"))
}

// Copy common module classes into neoforge build output so they are visible
// during development runs (same strategy used in forge-1.20.1).
tasks.register<Copy>("copyCommonClasses") {
    from(project(":common").sourceSets.main.get().output)
    into(layout.buildDirectory.dir("classes/java/main"))
}

tasks.named("compileJava") {
    finalizedBy("copyCommonClasses")
}

// Ensure common classes are available before any run or build task.
// Gradle requires an explicit dependency because copyCommonClasses writes
// to the same output directory as compileJava.
tasks.named("classes") {
    dependsOn("copyCommonClasses")
}

// Bundle common module classes into the mod jar
tasks.jar {
    from(project(":common").sourceSets.main.get().output) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    dependsOn("copyCommonClasses")
}

// Ensure common classes are on the compile classpath
sourceSets.main.get().compileClasspath += project(":common").sourceSets.main.get().output

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
