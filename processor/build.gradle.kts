import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.vanniktech.mavenPublish)
}

// runs inside the consumer's gradle, so don't require a recent jdk
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}

mavenPublishing {
    coordinates(artifactId = "swiftui2composable-processor")
    pom { name = "SwiftUI2Composable processor" }
}
