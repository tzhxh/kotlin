import org.jetbrains.kotlin.pill.PillExtension

description = "Simple Annotation Processor for testing kapt"

plugins {
    kotlin("jvm")
    `maven-publish` // only used for installing to mavenLocal()
    id("jps-compatible")
}

pill {
    variant = PillExtension.Variant.FULL
}

dependencies {
    implementation(kotlinStdlib())
}

sourceSets {
    "test" {}
}

publishing {
    repositories {
        mavenLocal() // to workaround configuration cache issues with 'publishToMavenLocal' task
    }
}

tasks.register("install") {
    dependsOn(tasks.named("publishAllPublicationsToMavenLocalRepository"))
}