import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

// ========================================
// Project
// ========================================

val gitCommit = providers.gradleProperty("gitCommit").orNull

val buildTime = LocalDateTime.now().format(
    DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
)

version = if (!gitCommit.isNullOrBlank()) {
    "$buildTime-$gitCommit"
} else {
    buildTime
}

group = "com.mc1510ty"

// ========================================
// Dependency versions
// ========================================

val swtVersion = "3.135.0"
//val lwjglVersion = "3.4.3"
//val jomlVersion = "1.10.9"
//val jomlPrimitivesVersion = "1.10.0"

// ========================================
// Supported platforms
//
// Pair.first  = SWT artifact suffix
// Pair.second = LWJGL native classifier
// ========================================

val supportedPlatforms = linkedMapOf(
    "windows-x86_64" to (
            "win32.win32.x86_64" to "natives-windows"
            ),
    "windows-aarch64" to (
            "win32.win32.aarch64" to "natives-windows-arm64"
            ),

    "linux-x86_64" to (
            "gtk.linux.x86_64" to "natives-linux"
            ),
    "linux-aarch64" to (
            "gtk.linux.aarch64" to "natives-linux-arm64"
            ),
    "linux-ppc64le" to (
            "gtk.linux.ppc64le" to "natives-linux-ppc64le"
            ),
    "linux-riscv64" to (
            "gtk.linux.riscv64" to "natives-linux-riscv64"
            ),

    "macos-x86_64" to (
            "cocoa.macosx.x86_64" to "natives-macos"
            ),
    "macos-aarch64" to (
            "cocoa.macosx.aarch64" to "natives-macos-arm64"
            )
)

// ========================================
// LWJGL modules
// ========================================
//
//val lwjglModules = listOf(
//    "lwjgl",
//    "lwjgl-assimp",
//    "lwjgl-freetype",
//    "lwjgl-harfbuzz",
//    "lwjgl-openal",
//    "lwjgl-opengl",
//    "lwjgl-sdl",
//    "lwjgl-spng",
//    "lwjgl-vma",
//    "lwjgl-vulkan",
//    "lwjgl-xxhash",
//    "lwjgl-zstd"
//)

// ========================================
// Platform-specific configurations
// ========================================

fun createPlatformConfiguration(name: String) =
    configurations.create(name) {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = true
    }

// SWT nested JARs
val swtConfigurations = supportedPlatforms.keys.associateWith {
        platform ->
    createPlatformConfiguration(
        "swt_${platform.replace('-', '_')}"
    )
}

//// LWJGL native nested JARs
//val lwjglNativeConfigurations = supportedPlatforms.keys.associateWith {
//        platform ->
//    createPlatformConfiguration(
//        "lwjglNatives_${platform.replace('-', '_')}"
//    )
//}

// ========================================
// Repositories
// ========================================

repositories {
    mavenCentral()
}

// ========================================
// Dependencies
// ========================================

dependencies {

    // ------------------------------------
    // SWT
    // ------------------------------------

    // Compile-time API reference
    // Runtime SWT is loaded from the platform-specific nested JAR.
    compileOnly(
        "org.eclipse.platform:org.eclipse.swt.win32.win32.x86_64:$swtVersion"
    )

    // Register SWT for every supported platform.
    supportedPlatforms.forEach { (platform, artifacts) ->

        add(
            swtConfigurations.getValue(platform).name,
            "org.eclipse.platform:org.eclipse.swt.${artifacts.first}:$swtVersion"
        )
    }


    // ------------------------------------
    // LWJGL Java API
    // ------------------------------------
//
//    implementation(
//        platform("org.lwjgl:lwjgl-bom:$lwjglVersion")
//    )
//
//    lwjglModules.forEach { module ->
//        implementation("org.lwjgl:$module")
//    }


    // ------------------------------------
    // LWJGL native libraries
    // ------------------------------------
//
//    supportedPlatforms.forEach { (platform, artifacts) ->
//
//        val nativeConfiguration =
//            lwjglNativeConfigurations.getValue(platform)
//
//        val nativeClassifier = artifacts.second
//
//        lwjglModules.forEach { module ->
//
//            // Vulkan native artifacts are only published for macOS.
//            if (module != "lwjgl-vulkan" ||
//                platform.startsWith("macos-")) {
//
//                add(
//                    nativeConfiguration.name,
//                    "org.lwjgl:$module:$lwjglVersion:$nativeClassifier"
//                )
//            }
//        }
//    }


    // ------------------------------------
    // JOML
    // ------------------------------------
//
//    implementation("org.joml:joml:$jomlVersion")
//
//    implementation(
//        "org.joml:joml-primitives:$jomlPrimitivesVersion"
//    )
}

// ========================================
// Shadow JAR
// ========================================

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>(
    "shadowJar"
) {

    archiveFileName.set(
        "TekitouAIStudio-${project.version}.jar"
    )

    // ------------------------------------
    // SWT nested JARs
    // ------------------------------------

    swtConfigurations.forEach { (platform, configuration) ->

        from(configuration) {
            into("META-INF/swt/$platform")
        }
    }

    // ------------------------------------
    // LWJGL native nested JARs
    // ------------------------------------

//    lwjglNativeConfigurations.forEach { (platform, configuration) ->
//
//        from(configuration) {
//            into("META-INF/lwjgl/$platform")
//        }
//    }

    manifest {
        attributes["Main-Class"] =
            "com.mc1510ty.TekitouAIStudio.Launcher"
    }
}

// ========================================
// Run task
// ========================================

val shadowJarTask =
    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>(
        "shadowJar"
    )

tasks.register<Exec>("run") {

    dependsOn(shadowJarTask)

    doFirst {

        val jarFile = shadowJarTask.get()
            .archiveFile.get().asFile.absolutePath

        if (System.getProperty("os.name")
                .lowercase().contains("windows")) {

            val command =
                "java --enable-native-access=ALL-UNNAMED " +
                        "--sun-misc-unsafe-memory-access=allow " +
                        "-Xmx12G -Xmx1G " +
                        "-XX:+UseStringDeduplication -XX:+UseZGC " +
                        "-jar \"$jarFile\" && pause && exit"

            commandLine(
                "cmd",
                "/c",
                "start",
                "cmd",
                "/k",
                command
            )

        } else {

            commandLine(
                "java",
                "--enable-native-access=ALL-UNNAMED",
                "--sun-misc-unsafe-memory-access=allow",
                "-Xmx12G -Xmx1G",
                "-XX:+UseStringDeduplication -XX:+UseZGC",
                "-jar",
                jarFile
            )
        }
    }
}
