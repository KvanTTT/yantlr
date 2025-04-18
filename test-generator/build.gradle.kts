plugins {
    kotlin("jvm")
    application
}

group = "org.yantlr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}
