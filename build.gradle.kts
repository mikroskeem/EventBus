import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.junit.platform.gradle.plugin.FiltersExtension
import org.junit.platform.gradle.plugin.EnginesExtension
import org.junit.platform.gradle.plugin.JUnitPlatformExtension

buildscript {
    dependencies {
        classpath("org.junit.platform:junit-platform-gradle-plugin:1.0.0")
    }
}

plugins {
    kotlin("jvm") version "1.2.71"
    id("net.minecrell.licenser") version "0.3"
    `maven-publish`
}

apply {
    plugin("org.junit.platform.gradle.plugin")
}

group = "eu.mikroskeem"
version = "0.0.1"

val asmVersion = "6.2"
val spekVersion = "1.1.5"
val slf4jVersion = "1.8.0-beta2"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    testImplementation("org.jetbrains.spek:spek-api:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntime("org.jetbrains.spek:spek-junit-platform-engine:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntime(kotlin("reflect"))
    testRuntime("org.slf4j:slf4j-simple:$slf4jVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.javaParameters = true
}

license {
    header = rootProject.file("etc/HEADER")
    filter.include("**/*.kt")
}

val test by tasks.getting(Test::class) {
    // Set working directory
    workingDir = this.temporaryDir

    // Show output
    testLogging {
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }

    // Verbose
    beforeTest(closureOf<Any> { logger.lifecycle("Running test: $this") })
}


configure<JUnitPlatformExtension> {
    filters {
        engines {
            include("spek")
        }
    }
}

val sourceJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(sourceSets["main"].allJava)
}

publishing {
    (publications) {
        create("maven", MavenPublication::class) {
            from(components["java"])
            artifact(sourceJar)
        }
    }
}

val wrapper by tasks.creating(Wrapper::class) {
    gradleVersion = "4.10.1"
}

// extension for configuration
fun JUnitPlatformExtension.filters(setup: FiltersExtension.() -> Unit) {
    when (this) {
        is ExtensionAware -> extensions.getByType(FiltersExtension::class.java).setup()
        else -> throw Exception("${this::class} must be an instance of ExtensionAware")
    }
}

fun FiltersExtension.engines(setup: EnginesExtension.() -> Unit) {
    when (this) {
        is ExtensionAware -> extensions.getByType(EnginesExtension::class.java).setup()
        else -> throw Exception("${this::class} must be an instance of ExtensionAware")
    }
}