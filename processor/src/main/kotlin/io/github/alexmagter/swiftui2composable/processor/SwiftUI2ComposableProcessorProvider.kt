package io.github.alexmagter.swiftui2composable.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class SwiftUI2ComposableProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        SwiftUI2ComposableProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
}