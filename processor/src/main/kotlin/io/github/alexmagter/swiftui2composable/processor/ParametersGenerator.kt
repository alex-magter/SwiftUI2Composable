package io.github.alexmagter.swiftui2composable.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSCallableReference
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

data class Component(
    val name: String,
    val props: ClassName,
    val callbacks: ClassName,
    val swiftView: String,
    val file: KSFile,
)

fun generateParameters(
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
    resolver: Resolver,
    bridgePackage: String
): List<Component>{
    return ParametersGenerator(
        codeGenerator = codeGenerator,
        logger = logger,
        resolver = resolver,
        bridgePackage = bridgePackage
    ).generate()
}

private class ParametersGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val resolver: Resolver,
    bridgePackage: String
) {
    private val bridgeClass = ClassName(bridgePackage, "SwiftBridge")

    fun generate(): List<Component>{
        return resolver
            .getSymbolsWithAnnotation(ANNOTATION)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { validate(it) }
            .map { generateComponents(it) }
            .toList()
    }

    private fun validate(fn: KSFunctionDeclaration): Boolean{
        var ok = true

        if (!fn.isComposable()) {
            logger.error("@SwiftUIImplemented can only be used in @Composable functions", fn)
            ok = false
        }

        // "= XSwiftUI(...)" can't be resolved yet because we generate it, the compiler checks it later
        val returnType = fn.returnType?.resolve()
        if (returnType != null && !returnType.isError && returnType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            logger.error("@SwiftUIImplemented functions must return Unit", fn)
            ok = false
        }

        fn.parameters.forEach { p ->
            val resolved = p.type.resolve()

            if (p.type.annotations.any { it.isComposable() }) {
                logger.error("@Composable parameters can't be passed to SwiftUI", p)
                ok = false
            }
            if (resolved.isSuspendFunctionType) {
                logger.error("suspend callbacks are not supported", p)
                ok = false
            }
            if (p.isCallback() && resolved.isMarkedNullable) {
                logger.error("Nullable callbacks are not supported, use a default value like {} instead", p)
                ok = false
            }
            if ((p.type.element as? KSCallableReference)?.receiverType != null) {
                logger.error("Callbacks with receiver (T.() -> Unit) are not supported", p)
                ok = false
            }
        }

        return ok
    }

    private fun generateComponents(fn: KSFunctionDeclaration): Component{
        val pkg = fn.packageName.asString()
        val name = fn.simpleName.asString()
        val propsClass = ClassName(pkg, "${name}Props")
        val callbacksClass = ClassName(pkg, "${name}Callbacks")
        val file = fn.containingFile!!

        FileSpec.builder(pkg, "${name}SwiftUI")
            .addType(buildProps(fn, propsClass))
            .addType(buildCallbacks(fn, callbacksClass))
            .addFunction(buildHost(fn, propsClass, callbacksClass))
            .build()
            .writeTo(codeGenerator, Dependencies(aggregating = false, file))

        return Component(name, propsClass, callbacksClass, fn.swiftName(), file)
    }

    private fun buildProps(fn: KSFunctionDeclaration, className: ClassName): TypeSpec{
        val props = fn.parameters.filter { it.isProp() }

        // We create an object if we have no parameters
        if(props.isEmpty()){
            return TypeSpec.objectBuilder(className).addModifiers(KModifier.DATA).build()
        }

        val constructor = FunSpec.constructorBuilder()
        val builder = TypeSpec.classBuilder(className)

        props.forEach { prop ->
            val name = prop.name!!.asString()
            val type = prop.type.toTypeName()
            constructor.addParameter(name, type)
            builder.addProperty(PropertySpec.builder(name, type).initializer(name).build())
        }

        return builder
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                constructor.build()
            )
            .build()
    }

    private fun buildCallbacks(fn: KSFunctionDeclaration, className: ClassName): TypeSpec{
        val callbacks = fn.parameters.filter { it.isCallback() }

        val builder = TypeSpec.interfaceBuilder(className)

        callbacks.forEach { p ->
            val signature = p.callbackSignature()
            val method = FunSpec.builder(p.name!!.asString())
                .addModifiers(KModifier.ABSTRACT)
                .returns(signature.returnType)

            signature.params.forEach { (paramName, paramType) ->
                method.addParameter(paramName, paramType)
            }
            builder.addFunction(method.build())
        }

        return builder.build()
    }

    private fun buildHost(
        fn: KSFunctionDeclaration,
        propsClass: ClassName,
        callbacksClass: ClassName
    ): FunSpec{
        val name = fn.simpleName.asString()
        val callbacks = fn.parameters.filter { it.isCallback() }
        val props = fn.parameters.filter { it.isProp() }

        val builder = FunSpec.builder("${name}SwiftUI")
            .addAnnotation(COMPOSABLE_ANNOTATION)
            .addModifiers(KModifier.INTERNAL)
            .apply {
                fn.parameters.forEach {
                    addParameter(ParameterSpec(it.name!!.asString(), it.type.toTypeName()))
                }
            }

        // keeps the latest lambda, swift holds nativeCallbacks forever
        callbacks.forEach { p ->
            val paramName = p.name!!.asString()
            builder.addStatement("val %N = %M(%N)", currentName(paramName), REMEMBER_UPDATED_STATE, paramName)
        }

        val callbacksObject = TypeSpec.anonymousClassBuilder()
            .addSuperinterface(callbacksClass)
            .apply { callbacks.forEach { addFunction(buildCallbackOverride(it)) } }
            .build()

        builder
            .beginControlFlow("val nativeCallbacks = %M", REMEMBER)
            .addCode("%L\n", callbacksObject)
            .endControlFlow()

        val stateExpr = if (props.isEmpty()) {
            CodeBlock.of("%T", propsClass)
        } else {
            val args = props.map {
                val n = it.name!!.asString()
                CodeBlock.of("%N = %N", n, n)
            }.joinToCode()
            CodeBlock.of("%T(%L)", propsClass, args)
        }

        val modifierExpr = fn.parameters.firstOrNull { it.isModifier() }
            ?.let { CodeBlock.of("%N", it.name!!.asString()) }
            ?: CodeBlock.of("%T", MODIFIER_CLASS)

        builder.addCode(
            CodeBlock.builder()
                .add("%M(\n", COMPOSE_NATIVE_VIEW)
                .indent()
                .add("state = %L,\n", stateExpr)
                .add("modifier = %L,\n", modifierExpr)
                .add("create = { initial -> %T.registry.%N(initial, nativeCallbacks) },\n", bridgeClass, "create$name")
                .unindent()
                .add(")\n")
                .build()
        )

        return builder.build()
    }

    private fun buildCallbackOverride(p: KSValueParameter): FunSpec {
        val paramName = p.name!!.asString()
        val signature = p.callbackSignature()
        val args = signature.params.map { CodeBlock.of("%N", it.first) }.joinToCode()

        return FunSpec.builder(paramName)
            .addModifiers(KModifier.OVERRIDE)
            .apply { signature.params.forEach { (n, type) -> addParameter(n, type) } }
            .returns(signature.returnType)
            .apply {
                if (signature.returnType == UNIT) {
                    addStatement("%N.value(%L)", currentName(paramName), args)
                } else {
                    addStatement("return %N.value(%L)", currentName(paramName), args)
                }
            }
            .build()
    }

    private fun currentName(paramName: String) =
        "current" + paramName.replaceFirstChar { it.uppercase() }

    private data class CallbackSignature(
        val params: List<Pair<String, TypeName>>,
        val returnType: TypeName,
    )

    private fun KSValueParameter.callbackSignature(): CallbackSignature {
        // When the lamda has named parameters
        val element = type.element
        if (element is KSCallableReference) {
            val params = element.functionParameters.mapIndexed { i, param ->
                // ksp2 reports unnamed lambda params as "_"
                val name = param.name?.asString()?.takeUnless { it.isBlank() || it == "_" } ?: "p$i"
                name to param.type.toTypeName()
            }
            return CallbackSignature(params, element.returnType.toTypeName())
        }

        // Without lambda names (usually the case)
        val args = type.resolve().arguments
        val params = args
            // In this notation, lambdas are notated like Function2<Int, String, Unit>
            // so we have to delete the last type, because it is the return type, not a parameter
            .dropLast(1)
            .mapIndexed { i, param ->
                "p$i" to param.type!!.toTypeName()
            }
        return CallbackSignature(params, args.last().type!!.toTypeName())
    }

    private fun KSFunctionDeclaration.isComposable(): Boolean{
        return this.annotations.any{
            it.isComposable()
        }
    }

    private fun KSAnnotation.isComposable(): Boolean{
        return this.shortName.asString() == "Composable" &&
                this.annotationType.resolve().declaration.qualifiedName?.asString() == COMPOSABLE
    }

    private fun KSFunctionDeclaration.swiftName(): String {
        val annotation = annotations.first { it.shortName.asString() == "SwiftUIImplemented" }
        val value = annotation.arguments
            .firstOrNull { it.name?.asString() == "swiftName" }
            ?.value as? String
        return if (value.isNullOrEmpty()) "${simpleName.asString()}View" else value
    }

    private fun KSValueParameter.isModifier(): Boolean =
        type.resolve().declaration.qualifiedName?.asString() == MODIFIER

    private fun KSValueParameter.isCallback(): Boolean =
        type.resolve().isFunctionType

    private fun KSValueParameter.isProp(): Boolean =
        !isModifier() && !isCallback()
}
