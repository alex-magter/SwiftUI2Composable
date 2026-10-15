package io.github.alexmagter.swiftui2composable

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class SwiftUIImplemented(val swiftName: String = "")