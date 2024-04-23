import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
}

kotlin {
    jvm {
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.desktop.currentOs)
                implementation(project(":common"))
            }
        }
    }
}

val appId = "com.artemchep.keyguard"

val macExtraPlistKeys: String
    get() = """
      <key>CFBundleLocalizations</key>
      <array>
        <string>af_ZA</string>
        <string>ca_ES</string>
        <string>de_DE</string>
        <string>es_ES</string>
        <string>ja_JP</string>
        <string>no_NO</string>
        <string>pt_PT</string>
        <string>sr_SP</string>
        <string>uk_UA</string>
        <string>zh_TW</string>
        <string>ar_SA</string>
        <string>cs_CZ</string>
        <string>el_GR</string>
        <string>fr_FR</string>
        <string>it_IT</string>
        <string>ko_KR</string>
        <string>pl_PL</string>
        <string>ro_RO</string>
        <string>sv_SE</string>
        <string>vi_VN</string>
        <string>da_DK</string>
        <string>en_US</string>
        <string>en_GB</string>
        <string>fi_FI</string>
        <string>hu_HU</string>
        <string>iw_IL</string>
        <string>nl_NL</string>
        <string>pt_BR</string>
        <string>ru_RU</string>
        <string>tr_TR</string>
        <string>zh_CN</string>
      </array>
    """

compose.desktop {
    application {
        mainClass = "com.artemchep.keyguard.MainKt"
        nativeDistributions {
            macOS {
                iconFile.set(project.file("icon.icns"))
                entitlementsFile.set(project.file("default.entitlements"))
                infoPlist {
                    this.extraKeysRawXml = macExtraPlistKeys
                }
            }
            windows {
                iconFile.set(project.file("icon.ico"))
            }
            linux {
                iconFile.set(project.file("icon.png"))
            }

            // Try to go for native appearance as per:
            // https://stackoverflow.com/a/70902920/1408535
            jvmArgs(
                "-Dapple.awt.application.appearance=system",
            )
            includeAllModules = true
            val formats = listOfNotNull(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
                // Because of this bug you can not build for macOS and
                // have the app image distribution format enabled.
                // See:
                // https://github.com/JetBrains/compose-multiplatform/issues/3814
                TargetFormat.AppImage.takeUnless { Os.isFamily(Os.FAMILY_MAC) },
            ).toTypedArray()
            targetFormats(*formats)

            packageName = "Keyguard"
            packageVersion = libs.versions.appVersionName.get()

            macOS {
                bundleID = "com.artemchep.keyguard"
                signing {
                    val certIdentity = findProperty("cert_identity") as String?
                    if (certIdentity != null) {
                        println("Signing identity ${certIdentity.take(2)}****")
                        sign.set(true)
                        identity.set(certIdentity)
                        // The certificate should be added to the
                        // keychain by this time.
                    } else {
                        println("No signing identity!")
                    }
                }
                notarization {
                    val notarizationAppleId = findProperty("notarization_apple_id") as String?
                        ?: "stub_apple_id"
                    val notarizationPassword = findProperty("notarization_password") as String?
                        ?: "stub_password"
                    val notarizationAscProvider =
                        findProperty("notarization_asc_provider") as String?
                            ?: "stub_asc_provider"
                    println("Notarization Apple Id ${notarizationAppleId.take(2)}****")
                    println("Notarization Password ${notarizationPassword.take(2)}****")
                    println("Notarization ASC Provider ${notarizationAscProvider.take(2)}****")
                    appleID.set(notarizationAppleId)
                    teamID.set(notarizationAscProvider)
                    password.set(notarizationPassword)
                }
            }
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

//
// Flatpak
//

val flatpakDir = "$buildDir/flatpak"
val resourcesDir = "$projectDir/src/jvmMain/resources"

tasks.register("prepareFlatpak") {
    dependsOn("packageAppImage")
    doLast {
        delete {
            delete("$flatpakDir/bin/")
            delete("$flatpakDir/lib/")
        }
        copy {
            from("$buildDir/compose/binaries/main/app/Keyguard/")
            into("$flatpakDir/")
            exclude("$buildDir/compose/binaries/main/app/Keyguard/lib/runtime/legal")
        }
        copy {
            from("$resourcesDir/icon.png")
            into("$flatpakDir/")
        }
        copy {
            from("$resourcesDir/flatpak/manifest.yml")
            into("$flatpakDir/")
            rename {
                "$appId.yml"
            }
        }
        copy {
            from("$projectDir/src/jvmMain/resources/flatpak/icon.desktop")
            into("$flatpakDir/")
            rename {
                "$appId.desktop"
            }
        }
    }
}

tasks.register("bundleFlatpak") {
    dependsOn("prepareFlatpak")
    doLast {
        exec {
            workingDir(flatpakDir)
            val buildCommand = listOf(
                "flatpak-builder",
                "--force-clean",
                "--state-dir=build/flatpak-builder",
                "--repo=build/flatpak-repo",
                "build/flatpak-target",
                "$appId.yml",
            )
            commandLine(buildCommand)
        }
        exec {
            workingDir(flatpakDir)
            val bundleCommand = listOf(
                "flatpak",
                "build-bundle",
                "build/flatpak-repo",
                "Keyguard.flatpak",
                appId,
            )
            commandLine(bundleCommand)
        }
    }
}

tasks.register("installFlatpak") {
    dependsOn("prepareFlatpak")
    doLast {
        exec {
            workingDir(flatpakDir)
            val installCommand = listOf(
                "flatpak-builder",
                "--install",
                "--user",
                "--force-clean",
                "--state-dir=build/flatpak-builder",
                "--repo=build/flatpak-repo",
                "build/flatpak-target",
                "$appId.yml",
            )
            commandLine(installCommand)
        }
    }
}

tasks.register("runFlatpak") {
    dependsOn("installFlatpak")
    doLast {
        exec {
            val runCommand = listOf(
                "flatpak",
                "run",
                appId,
            )
            commandLine(runCommand)
        }
    }
}
