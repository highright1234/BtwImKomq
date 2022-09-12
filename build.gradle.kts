plugins {
    kotlin("jvm") version "1.7.10"
}

group = "io.github.highright1234"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    // for bungeecord-proxy
    compileOnly(files("libs/bungeecord.jar"))
//    compileOnly("net.md-5:bungeecord-api:1.17-R0.1-SNAPSHOT")
    compileOnly(kotlin("stdlib"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}
