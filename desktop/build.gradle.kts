import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.alessandro.falco"
version = "0.1.0"

kotlin { jvmToolchain(17) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("uk.co.caprica:vlcj:4.11.0")
}

compose.desktop {
    application {
        mainClass = "com.alessandro.falco.desktop.MainKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            modules(
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.net.http",
                "java.sql",
                "java.xml",
                "jdk.unsupported"
            )
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            packageName = "FALCO"
            packageVersion = "0.1.0"
            description = "Fast Audio Library Catalog Organizer"
            vendor = "Alessandro Di Falco"
            windows {
                menuGroup = "FALCO"
                shortcut = true
                perUserInstall = true
                upgradeUuid = "0f2eb1f6-4303-4cb1-98ec-5c927936ac7a"
            }
        }
    }
}
