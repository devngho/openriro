plugins {
    kotlin("jvm") version libs.versions.kotlin
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

dependencies {
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.websockets)

    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    api(libs.bignum)
    implementation(libs.bignum.serialization.kotlinx)
    implementation(libs.ktor.client.cio)

    implementation(libs.skrapeit) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "org.jsoup", module = "jsoup")
        exclude(group = "xalan", module = "xalan")
        exclude(group = "org.apache.commons", module = "commons-text")
        exclude(group = "commons-io", module = "commons-io")
        exclude(group = "commons-net", module = "commons-net")
        exclude(group = "commons-codec", module = "commons-codec")
        exclude(group = "org.apache.commons", module = "commons-lang3")
    }

    implementation(libs.logback.classic)
    implementation(libs.jsoup)
    implementation(libs.xalan)
    implementation(libs.commons.net)
    implementation(libs.commons.text)
    implementation(libs.commons.io)
    implementation(libs.commons.codec)
    implementation(libs.commons.lang3)
    implementation("io.ktor:ktor-client-logging:3.4.0")

    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.assertions.core)

    testImplementation(libs.kotlin.test.common)
    testImplementation(libs.kotlin.test.annotations.common)
    testImplementation(libs.kotlin.reflect)

    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.websockets)

    testImplementation(libs.kotest.runner.junit5)

    testImplementation(libs.mockk)
}

tasks {
    named<Test>("test") {
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