plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktech.mavenPublish)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        iosMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}

mavenPublishing {
    coordinates(artifactId = "swiftui2composable-runtime")
    pom { name = "SwiftUI2Composable runtime" }
}
