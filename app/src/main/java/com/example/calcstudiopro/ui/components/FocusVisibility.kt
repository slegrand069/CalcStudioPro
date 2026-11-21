package com.example.calcstudiopro.ui.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Scrolls a focused input above the Android IME after the window has resized. */
@Composable
fun Modifier.bringFocusedFieldIntoView(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return bringIntoViewRequester(requester).onFocusChanged { focus ->
        if (focus.isFocused) scope.launch {
            delay(180)
            requester.bringIntoView()
        }
    }
}
