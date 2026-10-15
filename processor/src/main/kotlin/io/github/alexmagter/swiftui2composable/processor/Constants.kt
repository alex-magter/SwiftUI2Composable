package io.github.alexmagter.swiftui2composable.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

// strings to compare against ksp symbols
internal const val ANNOTATION = "io.github.alexmagter.swiftui2composable.SwiftUIImplemented"
internal const val COMPOSABLE = "androidx.compose.runtime.Composable"
internal const val MODIFIER = "androidx.compose.ui.Modifier"

// ksp options
internal const val OPTION_PACKAGE = "swiftui2composable.package"
internal const val OPTION_SWIFT_OUT = "swiftui2composable.swiftOut"
internal const val OPTION_FRAMEWORK = "swiftui2composable.framework"
internal const val OPTION_VIEWS_OUT = "swiftui2composable.viewsOut"
internal const val OPTION_SCAFFOLD = "swiftui2composable.scaffold"

internal const val DEFAULT_BRIDGE_PACKAGE = "io.github.alexmagter.swiftui2composable.generated"
internal const val DEFAULT_VIEWS_DIR = "SwiftUI2Composable"

private const val RUNTIME_PACKAGE = "io.github.alexmagter.swiftui2composable"

// kotlinpoet objects to generate code
internal val COMPOSABLE_ANNOTATION = ClassName("androidx.compose.runtime", "Composable")
internal val MODIFIER_CLASS = ClassName("androidx.compose.ui", "Modifier")
internal val NATIVE_VIEW = ClassName(RUNTIME_PACKAGE, "NativeView")

internal val REMEMBER = MemberName("androidx.compose.runtime", "remember")
internal val REMEMBER_UPDATED_STATE = MemberName("androidx.compose.runtime", "rememberUpdatedState")
internal val COMPOSE_NATIVE_VIEW = MemberName(RUNTIME_PACKAGE, "ComposeNativeView")
