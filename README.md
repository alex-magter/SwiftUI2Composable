# SwiftUI2Composable

SwiftUI2Composable helps you to implement your Composable functions in SwiftUI without worrying about
bridges, UIKit or reactivity. It automatically generates all the boilerplate for you so you can focus
on implementing your real UI logic.

> Work in progress

## Installation

In the `build.gradle.kts` of your shared module:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.7"
}

kotlin {
    sourceSets {
        iosMain.dependencies {
            implementation("io.github.alex-magter:swiftui2composable-runtime:<version>")
        }
    }
}

dependencies {
    add("kspIosArm64", "io.github.alex-magter:swiftui2composable-processor:<version>")
    add("kspIosSimulatorArm64", "io.github.alex-magter:swiftui2composable-processor:<version>")
}

ksp {
    arg("swiftui2composable.swiftOut", "$rootDir/iosApp/iosApp/Generated")
    arg("swiftui2composable.framework", "ComposeApp")
}
```

## Usage

First of all, annotate the `@Composable` function that will be implemented in SwiftUI with
`@SwiftUIImplemented` like in the example.

```kotlin
// iosMain
@SwiftUIImplemented
@Composable
actual fun MyButton(modifier: Modifier, text: String, onClick: () -> Unit) {
    
}
```

After compiling, a function called `MyButtonSwiftUI` is generated. Call it passing all the
arguments of your original composable, in the same order:

```kotlin
@SwiftUIImplemented
@Composable
actual fun MyButton(modifier: Modifier, text: String, onClick: () -> Unit) =
    MyButtonSwiftUI(modifier, text, onClick)
```

In your iosApp (or swift app of your kmp project), you will find a scaffolded view
called `MyButtonView`, with a parameter `props` which is an object with all the parameters
of your original function, in this case `text`. And an object `callbacks` with all the lambdas
of your original composable.

```swift
struct MyButtonView: View {
    let props: MyButtonProps
    let callbacks: any MyButtonCallbacks

    var body: some View { 
        Button(props.text){ callbacks.onClick() }
    }
}
```

In case you want to use a different name for your view, you can specify it in the annotation.

This code:
```kotlin
@SwiftUIImplemented("MySwiftButton")
@Composable
actual fun MyButton(modifier: Modifier, text: String, onClick: () -> Unit) =
    MyButtonSwiftUI(modifier, text, onClick)
```

Will create this view:

```swift
struct MySwiftButton: View {
    let props: MyButtonProps
    let callbacks: any MyButtonCallbacks

    var body: some View { 
        Button(props.text){ callbacks.onClick() }
    }
}
```

This is useful to reuse your already made views.

To disable scaffolding, add this parameter to your `build.gradle.kts`:

```kotlin
ksp {
    arg("swiftui2composable.scaffold", "false")
}
```

Finally, to connect everything, add this line to the entry point of your swift app:

```swift
SwiftBridge.shared.install(registry: GeneratedSwiftBridge())
```

### KSP arguments

| Argument | Required | Default | Description |
|---|---|---|---|
| `swiftui2composable.swiftOut` | yes | — | Folder of your iOS app where the Swift code is generated |
| `swiftui2composable.framework` | yes | — | Name of your Kotlin framework |
| `swiftui2composable.package` | no | `io.github.alexmagter.swiftui2composable.generated` | Package of the generated Kotlin code |
| `swiftui2composable.viewsOut` | no | `<swiftOut parent>/SwiftUI2Composable` | Folder where the scaffolded views are created |
| `swiftui2composable.scaffold` | no | `true` | Generate a view for each annotated composable |

## License

MIT
