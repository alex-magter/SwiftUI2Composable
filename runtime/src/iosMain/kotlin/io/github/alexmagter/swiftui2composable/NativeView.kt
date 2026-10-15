package io.github.alexmagter.swiftui2composable

import platform.UIKit.UIViewController

interface NativeView<S : Any> {
    val viewController: UIViewController
    fun update(state: S)
}