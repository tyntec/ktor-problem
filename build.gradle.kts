plugins {
    kotlin("jvm") version "1.3.41"
    id("io.gitlab.arturbosch.detekt") version "1.0.0-RC16"
}

group = "com.tyntec"
version = "0.1"

val ktorVersion = "1.2.2"
val jacksonVersion = "2.9.9"
val junitVersion = "5.4.2"

detekt {
    input = files("src/main/kotlin")
    filters  = ".*/resources/.*,.*/build/.*"
    config = files("config/detekt/config.yml")
}

repositories {
    jcenter()
}

dependencies {
    implementation(jackson("databind"))
    implementation(kotlin("stdlib-jdk8"))
    
    api(ktorServer("core"))
    testImplementation(ktorServer("tests"))
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.17")
    testImplementation(junit("api"))
    testImplementation(junit("params"))
    testRuntime(junit("engine", "5.3.2"))
}

fun DependencyHandler.ktor(module: String, version: String = ktorVersion): Any =
    "io.ktor:ktor-$module:$version"


fun DependencyHandler.ktorServer(module: String, version: String = ktorVersion): Any =
    "io.ktor:ktor-server-$module:$version"

fun DependencyHandler.ktorClient(module: String, version: String = ktorVersion): Any =
    "io.ktor:ktor-client-$module:$version"

fun DependencyHandler.jackson(module: String, version: String = jacksonVersion): Any =
    "com.fasterxml.jackson.core:jackson-$module:$version"

fun DependencyHandler.junit(module: String, version: String = junitVersion): Any =
    "org.junit.jupiter:junit-jupiter-$module:$version"