package com.lightphone.audiobooks.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.input.rememberTextFieldState
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import kotlinx.coroutines.flow.MutableStateFlow

/** SDK keyboard surface; text is returned only on explicit DONE. */
internal class BookTextInputScreen(
    sealedActivity: SealedLightActivity,
    private val fieldName: String,
    private val initialValue: String,
) : SimpleLightScreen<String>(sealedActivity) {
    private val keyboardOptions = MutableStateFlow(defaultKeyboardOptions())

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val input = rememberTextFieldState(initialValue)
        LightTheme(colors = colors) {
            LightTextInputEditor(
                title = fieldName,
                state = input,
                onSubmit = { goBack(it.toString()) },
                onBack = { goBack() },
                keyboardOptionsFlow = keyboardOptions,
                modifier = Modifier.fillMaxSize().padding(top = 1f.gridUnitsAsDp()),
                submitLabel = "DONE",
                singleLine = true,
            )
        }
    }
}
