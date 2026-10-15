package io.github.alexmagter.swiftui2composable.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

fun generateBridge(
    codeGenerator: CodeGenerator,
    components: List<Component>,
    bridgePackage: String
){
    BridgeGenerator(
        codeGenerator = codeGenerator,
        components = components,
        bridgePackage = bridgePackage
    ).generate()
}

private class BridgeGenerator(
    private val codeGenerator: CodeGenerator,
    private val components: List<Component>,
    bridgePackage: String
){
    private val registryClass = ClassName(bridgePackage, REGISTRY)
    private val bridgeClass = ClassName(bridgePackage, BRIDGE)

    fun generate(){
        FileSpec.builder(bridgeClass)
            .addType(buildRegistry())
            .addType(buildBridge())
            .build()
            // depends on every annotated file, so any change regenerates it
            .writeTo(codeGenerator, Dependencies(aggregating = true, *components.map { it.file }.toTypedArray()))
    }

    private fun buildRegistry(): TypeSpec{
        val builder = TypeSpec.interfaceBuilder(registryClass)

        components.forEach { c ->
            // "create" prefix avoids clashes with NSObject methods in swift (e.g. description)
            builder.addFunction(
                FunSpec.builder("create${c.name}")
                    .addModifiers(KModifier.ABSTRACT)
                    .addParameter("props", c.props)
                    .addParameter("callbacks", c.callbacks)
                    .returns(NATIVE_VIEW.parameterizedBy(c.props))
                    .build()
            )
        }

        return builder.build()
    }

    private fun buildBridge(): TypeSpec{
        val installed = PropertySpec.builder("installed", registryClass.copy(nullable = true), KModifier.PRIVATE)
            .mutable()
            .initializer("null")
            .build()

        val registry = PropertySpec.builder("registry", registryClass)
            .getter(
                FunSpec.getterBuilder()
                    .addStatement("return %N ?: error(%S)", installed, NOT_INSTALLED_ERROR)
                    .build()
            )
            .build()

        val install = FunSpec.builder("install")
            .addParameter("registry", registryClass)
            .addStatement("%N = registry", installed)
            .build()

        return TypeSpec.objectBuilder(bridgeClass)
            .addProperty(installed)
            .addProperty(registry)
            .addFunction(install)
            .build()
    }

    companion object {
        const val REGISTRY = "SwiftBridgeRegistry"
        const val BRIDGE = "SwiftBridge"
        const val NOT_INSTALLED_ERROR =
            "SwiftBridge is not installed. Call SwiftBridge.shared.install(registry: GeneratedSwiftBridge()) when the iOS app starts"
    }
}
