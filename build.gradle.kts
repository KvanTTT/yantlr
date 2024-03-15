plugins {
    kotlin("jvm") version "2.0.0-Beta4"
    id("org.jetbrains.intellij") version "1.13.0"
    application
}

group = "org.yantlr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
}

intellij {
    version.set("2023.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}