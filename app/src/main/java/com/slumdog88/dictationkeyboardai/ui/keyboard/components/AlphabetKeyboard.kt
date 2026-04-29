package com.slumdog88.dictationkeyboardai.ui.keyboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardScreen
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardSizing
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardViewModel
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardMode
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.CharacterKeySpec
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.KeyboardRowSpec
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.SpacerKeySpec
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.TemplateKey
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.TemplateKeySpec
import com.slumdog88.dictationkeyboardai.ui.keyboard.components.Key
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette

import com.slumdog88.dictationkeyboardai.ui.keyboard.CharacterKey
import com.slumdog88.dictationkeyboardai.ui.keyboard.TemplateKey

@Composable
fun AlphabetKeyboard(viewModel: KeyboardViewModel) {
    val colors = KeyboardPalette.colors
    val layout = when (viewModel.keyboardMode) {
        KeyboardMode.Symbols -> viewModel.currentSymbolsLayout
        KeyboardMode.SymbolsAlt -> viewModel.currentAltSymbolsLayout
        else -> viewModel.currentAlphabetLayout
    }
    val showNumberRow = when (viewModel.keyboardMode) {
        KeyboardMode.Symbols -> viewModel.currentSymbolsLayout.includeDefaultNumberRow
        KeyboardMode.SymbolsAlt -> viewModel.currentAltSymbolsLayout.includeDefaultNumberRow
        else -> viewModel.showNumberRow
    }

    val rows = remember(layout, showNumberRow) {
        layout.effectiveRows(
            showNumberRow = showNumberRow,
            showBottomRow = true
        )
    }
    
    val keyHeight = KeyboardSizing.calculateKeyHeight()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBackground) // Ensure background is filled
            .padding(4.dp)
    ) {
        rows.forEach { row ->
            KeyboardRow(
                rowSpec = row,
                viewModel = viewModel,
                rowHeight = keyHeight
            )
        }
    }
}

// Copied helpers from KeyboardScreen to make them accessible here
// Ideally these should be in a shared component file or public
@Composable
private fun KeyboardRow(
    rowSpec: KeyboardRowSpec,
    viewModel: KeyboardViewModel,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    Row(Modifier.fillMaxWidth()) {
        rowSpec.keys.forEach { keySpec ->
            when (keySpec) {
                is CharacterKeySpec -> CharacterKey(keySpec, viewModel, rowHeight)
                is TemplateKeySpec -> TemplateKey(keySpec, viewModel, rowHeight)
                is SpacerKeySpec -> Spacer(modifier = Modifier.weight(keySpec.weight))
            }
        }
    }
}
