// Root project: shared configuration for all submodules.
// Platform-specific plugins (ForgeGradle, NeoGradle) are applied per-submodule.

subprojects {
    // All submodules use java (common uses java-library for api exposure)
    apply(plugin = "java")

    group = "com.stylizeddamage"

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
