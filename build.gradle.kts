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
    kotlin("jvm") version "1.2.50"
    id("net.minecrell.licenser") version "0.3"
}

apply {
    plugin("org.junit.platform.gradle.plugin")
}

group = "eu.mikroskeem"
version = "0.0.1-SNAPSHOT"

val asmVersion = "6.2"
val spekVersion = "1.1.5"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.ow2.asm:asm:$asmVersion")

    testImplementation("org.jetbrains.spek:spek-api:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntime("org.jetbrains.spek:spek-junit-platform-engine:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntime(kotlin("reflect"))
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

val wrapper by tasks.getting(Wrapper::class) {
    gradleVersion = "4.8"
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