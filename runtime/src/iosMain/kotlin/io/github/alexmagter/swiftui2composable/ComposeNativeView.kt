package io.github.alexmagter.swiftui2composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

// only called from generated kotlin, swift doesn't need it
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
fun <S : Any> ComposeNativeView(
    state: S,
    modifier: Modifier = Modifier,
    create: (initial: S) -> NativeView<S>,
) {
    val view = remember { create(state) }
    val currentState = rememberUpdatedState(state)
    val lastState = remember { StateHolder(state) }

    UIKitViewController(
        factory = { view.viewController },
        modifier = modifier,
        update = {
            val newState = currentState.value
            if (newState != lastState.value) {
                lastState.value = newState
                view.update(newState)
            }
        },
    )
}

private class StateHolder<S>(var value: S)