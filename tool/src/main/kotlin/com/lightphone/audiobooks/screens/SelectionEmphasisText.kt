package com.lightphone.audiobooks.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LocalLightTypography

/** Changes only the Copy weight. No replacement font scale or UI controls. */
internal fun emphasizedSelectionStyle(base: TextStyle, selected: Boolean): TextStyle =
    if (selected) base.copy(fontWeight = FontWeight.Bold) else base

/** Current item is bold and underlined. Non-current items remain regular. */
@Composable
internal fun SelectionEmphasisText(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        val typography = LightThemeTokens.typography
        val selectionTypography = typography.copy(
            copy = emphasizedSelectionStyle(typography.copy, selected = true),
        )
        // Scope the weight to this one label; surrounding screens keep LightOS typography.
        CompositionLocalProvider(LocalLightTypography provides selectionTypography) {
            LightText(
                text = text,
                variant = LightTextVariant.Copy,
                underline = true,
                modifier = modifier,
            )
        }
    } else {
        LightText(text = text, variant = LightTextVariant.Copy, modifier = modifier)
    }
}
