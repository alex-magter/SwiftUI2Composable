package io.github.alexmagter.swiftui2composable.processor

import java.io.File

fun generateScaffolds(
    viewsOut: File,
    sourcesRoot: File,
    framework: String,
    components: List<Component>
){
    ScaffoldGenerator(
        viewsOut = viewsOut,
        sourcesRoot = sourcesRoot,
        framework = framework,
        components = components
    ).generate()
}

// creates a starting point for each missing swift view, user code is never overwritten
private class ScaffoldGenerator(
    private val viewsOut: File,
    private val sourcesRoot: File,
    private val framework: String,
    private val components: List<Component>
){
    fun generate(){
        val existing = existingStructs()
        val missing = components.filter { it.swiftView !in existing }
        if (missing.isEmpty()) return

        viewsOut.mkdirs()
        missing.forEach { c ->
            File(viewsOut, "${c.swiftView}.swift").writeText(buildView(c))
        }
    }

    // looks in every swift file, the user may have moved or renamed the scaffold
    private fun existingStructs(): Set<String> =
        sourcesRoot.walkTopDown()
            .filter { it.isFile && it.extension == "swift" }
            .flatMap { STRUCT_REGEX.findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()

    private fun buildView(c: Component) = """
        |import SwiftUI
        |import $framework
        |
        |struct ${c.swiftView}: View {
        |    let props: ${c.props.simpleName}
        |    let callbacks: any ${c.callbacks.simpleName}
        |
        |    var body: some View {
        |        // todo: implement, generated once by SwiftUI2Composable
        |        Text("${c.swiftView}")
        |    }
        |}
        |
    """.trimMargin()

    companion object {
        val STRUCT_REGEX = Regex("""\bstruct\s+(\w+)""")
    }
}
