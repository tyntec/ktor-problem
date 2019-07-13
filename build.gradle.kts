plugins {
    kotlin("jvm") version "1.3.41"
}

group = "com.tyntec"
version = "0.1"

val ktorVersion = "1.2.2"
val jacksonVersion = "2.9.9"

repositories {
    mavenCentral()
}

dependencies {
    implementation(jackson("databind"))
    implementation(kotlin("stdlib-jdk8"))
    
    implementation(ktorServer("core"))
    //testImplementation(ktorServer("tests"))
}

fun DependencyHandler.ktor(module: String, version: String = ktorVersion): Any =
    "io.ktor:ktor-$module:$version"


fun DependencyHandler.ktorServer(module: String, version: String = ktorVersion): Any =
    "io.ktor:ktor-server-$module:$version"

fun DependencyHandler.ktorClient(module: String, version: String = ktorVersion): Any =
    "io.ktor:ktor-client-$module:$version"

fun DependencyHandler.jackson(module: String, version: String = jacksonVersion): Any =
    "com.fasterxml.jackson.core:jackson-$module:$version"