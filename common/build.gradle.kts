// Common module: shared, platform-independent logic.
// Uses java-library to expose api dependencies to platform modules.

plugins {
    id("java-library")
}

java {
    toolchain {
        // Java 17 for maximum compatibility (all target platforms support it)
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // JSON parsing for config files
    api("com.google.code.gson:gson:2.10.1")
}
