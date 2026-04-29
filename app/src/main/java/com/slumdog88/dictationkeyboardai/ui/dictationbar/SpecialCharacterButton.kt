package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette

/**
 * Tap-to-reveal special character picker button.
 *
 * Gesture mechanics:
 * - **Tap button:** Shows popup with two rows of characters
 * - **Tap character:** Inserts that character, popup disappears
 * - **Tap button again (while popup showing):** Hides popup without inserting
 * - **Long-press button:** Triggers onLongClick callback (for number row toggle)
 *
 * Popup layout:
 *   - Top row (symbols): @ # $ % & * ( ) (8 chars)
 *   - Bottom row (punctuation): - ' , . ! ? " : (8 chars)
 *
 * @param onInsertCharacter Callback to insert the selected character at cursor position
 * @param onLongClick Optional callback for long-press action (e.g., toggle number row)
 * @param modifier Optional modifier for layout (supports weight distribution)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpecialCharacterButton(
    onInsertCharacter: (String) -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = KeyboardPalette.colors
    val density = LocalDensity.current

    // Character definitions - 8 chars each row
    val bottomRow = listOf("-", "'", ",", ".", "!", "?", "\"", ":")  // Punctuation (8)
    val topRow = listOf("@", "#", "$", "%", "&", "*", "(", ")")      // Symbols (8)

    // State
    var showPopup by remember { mutableStateOf(false) }
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }

    // Outer Box receives weight from parent Row
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                buttonSize = coords.size
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // No ripple, we handle visual feedback
                onClick = {
                    HapticUtils.performKeyClick(context)
                    showPopup = !showPopup
                },
                onLongClick = {
                    HapticUtils.performGesturalFeedback(context)
                    onLongClick?.invoke()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner Box for visual sizing - text labels within touch area
        Box(
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Main label - primary function (special chars/punctuation)
            Text(
                text = ",!?",
                color = if (showPopup) colors.accent else colors.keyTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            // Secondary label - hints at long-press function (number row toggle)
            Text(
                text = "123",
                color = colors.keyTextPrimary.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 2.dp)
            )
        }

        if (showPopup) {
            CharacterPickerPopup(
                topRow = topRow,
                bottomRow = bottomRow,
                buttonSize = buttonSize,
                itemWidth = 44.dp,
                itemHeight = 48.dp,
                onCharacterSelected = { char ->
                    HapticUtils.performKeyClick(context)
                    onInsertCharacter(char)
                    showPopup = false
                }
            )
        }
    }
}

/**
 * Popup overlay showing two rows of clickable characters.
 * Positioned centered above the button.
 */
@Composable
private fun CharacterPickerPopup(
    topRow: List<String>,
    bottomRow: List<String>,
    buttonSize: IntSize,
    itemWidth: Dp,
    itemHeight: Dp,
    onCharacterSelected: (String) -> Unit
) {
    val colors = KeyboardPalette.colors
    val density = LocalDensity.current

    // Calculate dimensions
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val numCols = maxOf(topRow.size, bottomRow.size)

    val popupWidth = numCols * itemWidthPx
    val popupHeight = 2 * itemHeightPx

    // Center popup above button
    val offsetX = ((buttonSize.width / 2f) - (popupWidth / 2f)).toInt()
    val offsetY = (-popupHeight - gapPx).toInt()

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(offsetX, offsetY),
        properties = PopupProperties(
            focusable = false, // MUST be false in IME context to prevent keyboard hiding
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = colors.functionalKeyBackground,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            // Top row (symbols)
            ClickableCharacterRow(
                characters = topRow,
                itemWidth = itemWidth,
                itemHeight = itemHeight,
                onCharacterClick = onCharacterSelected
            )

            // Bottom row (punctuation)
            ClickableCharacterRow(
                characters = bottomRow,
                itemWidth = itemWidth,
                itemHeight = itemHeight,
                onCharacterClick = onCharacterSelected
            )
        }
    }
}

/**
 * Single row of clickable characters in the picker popup.
 */
@Composable
private fun ClickableCharacterRow(
    characters: List<String>,
    itemWidth: Dp,
    itemHeight: Dp,
    onCharacterClick: (String) -> Unit
) {
    val colors = KeyboardPalette.colors

    Row {
        characters.forEach { char ->
            Box(
                modifier = Modifier
                    .size(width = itemWidth, height = itemHeight)
                    .clickable { onCharacterClick(char) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char,
                    color = colors.keyTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
