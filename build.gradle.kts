plugins {
    kotlin("jvm") version "2.0.0-Beta5"
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
    testImplementation(files("libs/idea_rt.jar", "libs/junit.jar"))
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