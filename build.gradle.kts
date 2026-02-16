@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version libs.versions.kotlin
    kotlin("plugin.serialization") version libs.versions.kotlin
    id("org.jetbrains.dokka") version libs.versions.dokka
    id("io.kotest") version libs.versions.kotest
    id("com.google.devtools.ksp") version libs.versions.ksp
    `maven-publish`
    signing
}

group = "io.github.devngho"
version = "0.1.0"

repositories {
    mavenCentral()
}

val dokkaHtmlJar by tasks.registering(Jar::class) {
    description = "A HTML Documentation JAR containing Dokka HTML"
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

signing {
    sign(publishing.publications)
}

publishing {
    repositories {
        val id: String =
            if (project.hasProperty("repoUsername")) project.property("repoUsername") as String
            else System.getenv("repoUsername")
        val pw: String =
            if (project.hasProperty("repoPassword")) project.property("repoPassword") as String
            else System.getenv("repoPassword")
        if (!version.toString().endsWith("SNAPSHOT")) {
            maven("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/") {
                name = "ossrh-staging-api"
                credentials {
                    username = id
                    password = pw
                }
            }
        } else {
            maven("https://central.sonatype.com/repository/maven-snapshots/") {
                name = "ossrh-staging-api"
                credentials {
                    username = id
                    password = pw
                }
            }
        }
    }

    publications.withType(MavenPublication::class) {
        groupId = project.group as String?
        version = project.version as String?

        artifact(dokkaHtmlJar)

        pom {
            name.set("openriro")
            description.set("An scrapper for Riroschool")
            url.set("https://github.com/devngho/openriro")


            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://github.com/devngho/openriro/blob/master/LICENSE")
                }
            }
            developers {
                developer {
                    id.set("devngho")
                    name.set("devngho")
                    email.set("yjh135908@gmail.com")
                }
            }
            scm {
                connection.set("https://github.com/devngho/openriro.git")
                developerConnection.set("https://github.com/devngho/openriro.git")
                url.set("https://github.com/devngho/openriro")
            }
        }
    }
}

kotlin {
    // copied from ionspin/kotlin-multiplatform-bignum (at build.gradle.kts), Apache 2.0
    // removed watchosDeviceArm64 and modified js
    js {
        nodejs()
        browser()
    }
    linuxX64()
    linuxArm64()
    androidNativeX64()
    androidNativeX86()
    androidNativeArm32()
    androidNativeArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()
    mingwX64()
    // copy end

    jvm()

    wasmJs {
        browser()
        nodejs()
        d8()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib"))

            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)

            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.ktor.client.cio)

            implementation(libs.ksoup)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)

            implementation(libs.kotlin.test.common)
            implementation(libs.kotlin.test.annotations.common)
            implementation(libs.kotlin.reflect)

            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)

            implementation(libs.kotest.runner.junit5)

            implementation(libs.mockk)
        }

        applyDefaultHierarchyTemplate()
    }
}

tasks {
    // copied from ionspin/kotlin-multiplatform-bignum (at build.gradle.kts), Apache 2.0
    // fixed for correct task dependencies in this project
    all {
        val targets = listOf(
            "AndroidNativeArm32",
            "AndroidNativeArm64",
            "AndroidNativeX64",
            "AndroidNativeX86",
            "Js",
            "Jvm",
            "KotlinMultiplatform",
            "LinuxArm64",
            "LinuxX64",
            "WasmJs",
            "MingwX64",
            "IosArm64",
            "IosSimulatorArm64",
            "IosX64",
            "MacosArm64",
            "MacosX64",
            "TvosArm64",
            "TvosSimulatorArm64",
            "TvosX64",
        )

//        targets.dropLast(1).forEachIndexed { index, target ->
//            if (this.name.startsWith("sign${target}Publication")) {
//                this.mustRunAfter("sign${targets[index + 1]}Publication")
//            }
//        }

        if (this.name.startsWith("publish") || this.name.startsWith("linkDebugTest") || this.name.startsWith("compileTest")) {
            targets.forEach {
                this.mustRunAfter("sign${it}Publication")
            }
        }
    }
    // copy end

    named<Test>("jvmTest") {
        useJUnitPlatform()
        filter {
            isFailOnNoMatchingTests = false
        }
        testLogging @ExperimentalStdlibApi {
            showExceptions = true
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}