plugins {
    kotlin("jvm") version "1.4.21"
    id("org.jetbrains.dokka") version "0.9.18"
    id("io.gitlab.arturbosch.detekt") version "1.15.0"
    `maven-publish`
    signing
}

group = "com.tyntec"
version = "0.7"

val ktorVersion = "1.5.0"
val jacksonVersion = "2.12.0"
val junitVersion = "5.4.2"
val ossUsername: String? by project
val ossPassword: String? by project

detekt {
    input = files("src/main/kotlin")
    config = files("config/detekt/config.yml")
}

repositories {
    jcenter()
}

dependencies {
    implementation(ktor("jackson"))
    implementation(ktor("gson"))
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



tasks.register<Jar>("sourcesJar") {
    dependsOn(JavaPlugin.CLASSES_TASK_NAME)
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

tasks.register<Jar>("javadocJar") {
    dependsOn("dokka")
    from(tasks["dokka"])
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set("Ktor Problem")
                description.set("A RFC7807 implementation for ktor")
                url.set("https://github.com/tyntec/ktor-problem")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("coderskitchen")
                        name.set("Peter Daum")
                        email.set("daum@tyntec.com")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:tyntec/ktor-problem.git")
                    url.set("https://github.com/tyntec/ktor-problem")
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = ossUsername
                password = ossPassword
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

tasks.withType<Test> {
    useJUnitPlatform {

    }
}