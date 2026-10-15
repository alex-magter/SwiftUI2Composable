package io.github.alexmagter.swiftui2composable.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import java.io.File

class SwiftUI2ComposableProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    options: Map<String, String>,
): SymbolProcessor {
    private val bridgePackage = options[OPTION_PACKAGE] ?: DEFAULT_BRIDGE_PACKAGE
    private val swiftOut = options[OPTION_SWIFT_OUT]
    private val framework = options[OPTION_FRAMEWORK]
    private val viewsOut = options[OPTION_VIEWS_OUT]
    private val scaffold = options[OPTION_SCAFFOLD]?.toBooleanStrict() ?: true

    // accumulated across rounds, consumed in finish()
    private val components = mutableListOf<Component>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        components += generateParameters(
            codeGenerator,
            logger,
            resolver,
            bridgePackage
        )

        return emptyList()
    }

    // not called by ksp if any error was logged
    override fun finish() {
        generateBridge(
            codeGenerator,
            components,
            bridgePackage
        )

        if (swiftOut == null || framework == null) {
            logger.error("SwiftUI2Composable needs the ksp args \"$OPTION_SWIFT_OUT\" and \"$OPTION_FRAMEWORK\"")
            return
        }

        generateSwift(
            File(swiftOut),
            framework,
            components
        )

        if (scaffold) {
            // the generated folder lives inside the app sources
            val sourcesRoot = File(swiftOut).parentFile
            generateScaffolds(
                viewsOut?.let(::File) ?: File(sourcesRoot, DEFAULT_VIEWS_DIR),
                sourcesRoot,
                framework,
                components
            )
        }
    }
}
