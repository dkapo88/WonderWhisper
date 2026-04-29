package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import android.content.Context
import android.view.inputmethod.EditorInfo
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardTheme

/**
 * Main dictation bar screen composable with the complete 4-4 button layout.
 *
 * Layout structure:
 * ```
 * Row (fillMaxWidth, height = 56.dp, translucent background)
 *   |-- Left buttons (weight 0.40, equal weight distribution)
 *   |     |-- Keyboard Switch button (weight 1f)
 *   |     |-- SelectAll/Reprocess morphing button (weight 1f)
 *   |     |-- Dynamic Button (Undo/Cancel/Retry) (weight 1f)
 *   |     |-- AI Toggle button with glow (weight 1f)
 *   |-- Center mic (weight 0.20, Center aligned)
 *   |     |-- MicButton
 *   |-- Right buttons (weight 0.40, equal weight distribution)
 *         |-- Paste Last button (weight 1f)
 *         |-- Special Characters button with hold-and-slide picker (weight 1f)
 *         |-- Backspace button with accelerating delete (weight 1f)
 *         |-- Return button (weight 1f)
 * ```
 *
 * Features:
 * - Fixed 112dp height with navigation bar insets
 * - Translucent background (0.96 alpha)
 * - Button morphing with Crossfade animations
 * - AI toggle with radial gradient glow effect
 * - All buttons have press animation and haptic feedback
 * - All 8 button slots use equal weight(1f) for uniform sizing
 * - Placeholders are invisible Spacers reserving space for future features
 *
 * @param viewModel State holder providing isRecording, isProcessing, currentAmplitude, etc.
 * @param onRecordToggle Callback when recording state should be toggled
 * @param onSwitchKeyboard Callback to switch to next input method
 * @param onSelectAll Callback to select all text
 * @param onReprocess Callback to reprocess selected text through AI
 * @param onAiToggle Callback to toggle AI post-processing
 * @param onTypedSentenceEndAutocorrectToggle Callback to toggle typed sentence-end autocorrect
 * @param onScreenContextToggle Callback to toggle include-screen-context setting
 * @param onReturn Callback to send enter/return key
 * @param onPasteLast Callback to paste last transcription
 * @param onBackspace Callback to delete single character
 * @param onBackspaceWord Callback to delete entire word
 * @param onMicDoubleTap Callback when mic button is double-tapped (for streaming mode)
 * @param onMicHoldStart Callback when mic hold-to-talk activates (300ms threshold)
 * @param onMicHoldEnd Callback when user releases after hold-to-talk
 * @param onUndo Callback to undo last transcription
 * @param onCancelRecording Callback to cancel current recording without transcribing
 * @param onRetry Callback to retry last failed transcription
 * @param onInsertCharacter Callback to insert a special character at cursor position
 * @param modifier Optional modifier for the screen
 */
@Composable
fun DictationBarScreen(
    viewModel: DictationBarViewModel,
    onRecordToggle: (Boolean) -> Unit,
    onSwitchKeyboard: () -> Unit,
    onSelectAll: () -> Unit,
    onReprocess: () -> Unit,
    onAiToggle: () -> Unit,
    onTypedSentenceEndAutocorrectToggle: (Boolean) -> Unit,
    onScreenContextToggle: (Boolean) -> Unit,
    onReturn: () -> Unit,
    onPasteLast: () -> Unit,
    onBackspace: () -> Unit,
    onBackspaceWord: () -> Unit,
    onMicDoubleTap: () -> Unit = {},
    onMicHoldStart: () -> Unit = {},
    onMicHoldEnd: () -> Unit = {},
    onUndo: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onRetry: () -> Unit = {},
    onInsertCharacter: (String) -> Unit = {},
    onKeyboardLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    KeyboardTheme {
        val colors = KeyboardPalette.colors
        val context = LocalContext.current

        // Read preferences from SharedPreferences
        val keyboardPrefs = remember {
            context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
        }
        val opacity = remember { keyboardPrefs.getFloat("keyboard_opacity", 0.85f) }
        
        // Read keyboard button behavior preference (false = default, true = reversed)
        val keyboardButtonReversed = remember {
            keyboardPrefs.getBoolean("keyboard_button_reversed", false)
        }

        // Main bar content - always fills full width
        // Note: Navigation bar padding is handled by parent (StreamingDictationBar) so it's at bottom of entire keyboard
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.keyboardBackground.copy(alpha = opacity))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: 4 utility buttons (weight 0.40, equal weight distribution)
            Row(
                modifier = Modifier.weight(0.40f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Keyboard Switch button
                // Behavior depends on keyboardButtonReversed preference:
                // Default (false): Tap = switch IME, Long-press = expand keyboard
                // Reversed (true): Tap = expand keyboard, Long-press = switch IME
                UtilityButton(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_keyboard,
                    contentDescription = if (keyboardButtonReversed) 
                        "Open built-in keyboard (long-press to switch to other keyboard)" 
                    else 
                        "Switch to other keyboard (long-press to open built-in keyboard)",
                    onClick = {
                        if (keyboardButtonReversed) {
                            // Reversed: tap toggles expanded keyboard
                            onKeyboardLongPress()
                        } else {
                            // Default: tap switches IME (or collapses if expanded)
                            if (viewModel.isKeyboardExpanded) {
                                viewModel.collapseKeyboard()
                            } else {
                                onSwitchKeyboard()
                            }
                        }
                    },
                    onLongClick = {
                        if (keyboardButtonReversed) {
                            // Reversed: long-press switches IME
                            if (viewModel.isKeyboardExpanded) {
                                viewModel.collapseKeyboard()
                            }
                            onSwitchKeyboard()
                        } else {
                            // Default: long-press toggles expanded keyboard
                            onKeyboardLongPress()
                        }
                    }
                )

                // 2. Select All / Reprocess morphing button
                // When text is selected: shows Reprocess (sparkles) with accent tint
                // Otherwise: shows Select All
                Crossfade(
                    targetState = viewModel.hasSelection,
                    animationSpec = tween(200),
                    modifier = Modifier.weight(1f),
                    label = "selectall-reprocess-morph"
                ) { hasSelection ->
                    if (hasSelection) {
                        UtilityButton(
                            icon = R.drawable.ic_sparkles,
                            contentDescription = "Reprocess with AI",
                            onClick = onReprocess,
                            tint = colors.accent
                        )
                    } else {
                        UtilityButton(
                            icon = R.drawable.ic_select_all_grid,
                            contentDescription = if (viewModel.fieldHasContent) "Select All" else "Field is empty",
                            onClick = onSelectAll,
                            enabled = viewModel.fieldHasContent
                        )
                    }
                }

                // 3. Dynamic Button - context-aware Undo/Cancel/Retry
                DynamicButton(
                    modifier = Modifier.weight(1f),
                    state = viewModel.dynamicButtonState,
                    canUndo = viewModel.canUndo,
                    onUndo = onUndo,
                    onCancel = onCancelRecording,
                    onRetry = onRetry
                )

                // 4. AI Toggle button with glow effect
                AiToggleButton(
                    modifier = Modifier.weight(1f),
                    isEnabled = viewModel.isAiEnabled,
                    isTypedSentenceEndAutocorrectEnabled = viewModel.isTypedSentenceEndAutocorrectEnabled,
                    isScreenContextEnabled = viewModel.isScreenContextEnabled,
                    onClick = onAiToggle,
                    onToggleTypedSentenceEndAutocorrect = onTypedSentenceEndAutocorrectToggle,
                    onToggleScreenContext = onScreenContextToggle
                )
            }

            // CENTER: Mic button (weight 0.20)
            Box(
                modifier = Modifier.weight(0.20f),
                contentAlignment = Alignment.Center
            ) {
                // Normalize amplitude from 0-32767 to 0.0-1.0
                val normalizedAmplitude = (viewModel.currentAmplitude / 32767f).coerceIn(0f, 1f)

                MicButton(
                    isRecording = viewModel.isRecording,
                    isProcessing = viewModel.isProcessing,
                    isHolding = viewModel.isHolding,
                    currentAmplitude = normalizedAmplitude,
                    onClick = { onRecordToggle(!viewModel.isRecording) },
                    onDoubleTap = onMicDoubleTap,
                    onHoldStart = onMicHoldStart,
                    onHoldEnd = onMicHoldEnd
                )
            }

            // RIGHT: 4 utility buttons (weight 0.40, equal weight distribution)
            Row(
                modifier = Modifier.weight(0.40f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 5. Paste Last button
                UtilityButton(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_paste,
                    contentDescription = "Paste Last",
                    onClick = onPasteLast
                )

                // 6. Special Characters button (hold-and-slide picker)
                // Long-press toggles manual number row visibility
                SpecialCharacterButton(
                    modifier = Modifier.weight(1f),
                    onInsertCharacter = onInsertCharacter,
                    onLongClick = { viewModel.toggleManualNumberRow() }
                )

                // 7. Backspace button with accelerating delete
                BackspaceButton(
                    modifier = Modifier.weight(1f),
                    onDelete = onBackspace,
                    onDeleteWord = onBackspaceWord
                )

                // 8. Return button with EditorInfo-aware morphing
                ReturnButton(
                    modifier = Modifier.weight(1f),
                    imeOptions = viewModel.imeOptions,
                    onClick = onReturn
                )
            }
        }
    }
}

/**
 * AI Toggle button with radial gradient glow effect when enabled.
 *
 * When AI is enabled:
 * - Shows radial gradient glow behind the icon
 * - Icon is tinted with accent color
 *
 * When AI is disabled:
 * - No glow effect
 * - Icon uses standard keyTextPrimary tint
 *
 * Haptic feedback:
 * - Distinct haptic for on vs off (EFFECT_DOUBLE_CLICK for on, EFFECT_TICK for off)
 *
 * @param isEnabled Whether AI post-processing is enabled
 * @param onClick Callback when button is clicked
 * @param isTypedSentenceEndAutocorrectEnabled Whether typed sentence-end autocorrect is enabled
 * @param isScreenContextEnabled Whether include-screen-context is enabled
 * @param onToggleTypedSentenceEndAutocorrect Callback to toggle sentence-end autocorrect
 * @param onToggleScreenContext Callback to toggle include-screen-context
 * @param modifier Optional modifier
 */
@Composable
private fun AiToggleButton(
    isEnabled: Boolean,
    isTypedSentenceEndAutocorrectEnabled: Boolean,
    isScreenContextEnabled: Boolean,
    onClick: () -> Unit,
    onToggleTypedSentenceEndAutocorrect: (Boolean) -> Unit,
    onToggleScreenContext: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = KeyboardPalette.colors
    var quickSettingsExpanded by remember { mutableStateOf(false) }

    // Wrap onClick with distinct haptic feedback
    val onClickWithHaptic: () -> Unit = {
        if (quickSettingsExpanded) {
            // When quick settings are open, tapping the AI button should only dismiss the menu.
            quickSettingsExpanded = false
        } else {
            // isEnabled is current state, so if enabled we're turning OFF
            if (isEnabled) {
                HapticUtils.performToggleOff(context)
            } else {
                HapticUtils.performToggleOn(context)
            }
            onClick()
        }
    }

    // Wrap in Box to apply modifier (for weight distribution)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isEnabled) {
            // Glow effect behind icon when enabled
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Radial gradient glow background
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.accent.copy(alpha = 0.6f),
                                    colors.accent.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Utility button with accent tint on top
                UtilityButton(
                    icon = R.drawable.ic_ai_cloud,
                    contentDescription = "AI Enabled - tap to disable, long-press for AI quick settings",
                    onClick = onClickWithHaptic,
                    onLongClick = { quickSettingsExpanded = true },
                    tint = colors.accent
                )
            }
        } else {
            // Standard utility button without glow
            UtilityButton(
                icon = R.drawable.ic_ai_cloud,
                contentDescription = "AI Disabled - tap to enable, long-press for AI quick settings",
                onClick = onClickWithHaptic,
                onLongClick = { quickSettingsExpanded = true }
            )
        }

        DropdownMenu(
            expanded = quickSettingsExpanded,
            onDismissRequest = { quickSettingsExpanded = false },
            // Keep popup focusable so outside taps dismiss the menu without passing
            // through to underlying buttons (which can unintentionally toggle AI).
            properties = PopupProperties(focusable = true),
            modifier = Modifier
                .background(colors.keyboardBackground, RoundedCornerShape(14.dp))
                .padding(vertical = 4.dp)
        ) {
            QuickToggleMenuRow(
                label = "Keyboard Autocorrect",
                checked = isTypedSentenceEndAutocorrectEnabled,
                onCheckedChange = onToggleTypedSentenceEndAutocorrect,
                colors = colors
            )
            QuickToggleMenuRow(
                label = "Screen context",
                checked = isScreenContextEnabled,
                onCheckedChange = onToggleScreenContext,
                colors = colors
            )
        }
    }
}

@Composable
private fun QuickToggleMenuRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: com.slumdog88.dictationkeyboardai.ui.theme.KeyboardColors
) {
    Row(
        modifier = Modifier
            .sizeIn(minWidth = 220.dp)
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.keyTextSecondary,
                uncheckedTrackColor = colors.keyBackground
            )
        )
    }
}

/**
 * Sealed class representing return button visual states.
 * Each state has an icon resource, content description, and whether it uses accent styling.
 */
private sealed class ReturnButtonStyle(
    val iconRes: Int,
    val contentDescription: String,
    val useAccentStyle: Boolean
) {
    object Search : ReturnButtonStyle(R.drawable.ic_search, "Search", true)
    object Send : ReturnButtonStyle(R.drawable.ic_send, "Send", true)
    object Go : ReturnButtonStyle(R.drawable.ic_arrow_forward, "Go", true)
    object Next : ReturnButtonStyle(R.drawable.ic_arrow_forward, "Next", false)
    object Done : ReturnButtonStyle(R.drawable.ic_check, "Done", true)
    object Return : ReturnButtonStyle(R.drawable.ic_return, "Return", false)
}

/**
 * Smart Return/Enter button that morphs based on EditorInfo.
 *
 * Action mapping:
 * - IME_ACTION_SEARCH -> Search icon with accent background
 * - IME_ACTION_SEND -> Send icon with accent background
 * - IME_ACTION_GO -> Forward arrow with accent background
 * - IME_ACTION_NEXT -> Forward arrow, no background (non-final action)
 * - IME_ACTION_DONE -> Checkmark with accent background
 * - Default/NONE/UNSPECIFIED -> Return symbol, no background
 *
 * @param imeOptions EditorInfo.imeOptions value
 * @param onClick Callback when button is clicked
 * @param modifier Optional modifier
 */
@Composable
private fun ReturnButton(
    imeOptions: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = KeyboardPalette.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "returnButtonScale"
    )

    // Morph for Search/Go/Next only — these are standard IME actions that apps
    // handle correctly via performEditorAction. Send/Done/Unspecified stay as
    // plain Return (KEYCODE_ENTER) because messaging apps like WhatsApp/Telegram
    // set IME_ACTION_SEND but don't respond to performEditorAction for it.
    val imeAction = imeOptions and EditorInfo.IME_MASK_ACTION
    val buttonStyle = when (imeAction) {
        EditorInfo.IME_ACTION_SEARCH -> ReturnButtonStyle.Search
        EditorInfo.IME_ACTION_GO -> ReturnButtonStyle.Go
        EditorInfo.IME_ACTION_NEXT -> ReturnButtonStyle.Next
        else -> ReturnButtonStyle.Return
    }

    Crossfade(
        targetState = buttonStyle,
        animationSpec = tween(200),
        modifier = modifier,
        label = "return-morph"
    ) { style ->
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (style.useAccentStyle) {
                        Modifier.background(colors.accent, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        HapticUtils.performKeyClick(context)
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(style.iconRes),
                contentDescription = style.contentDescription,
                tint = if (style.useAccentStyle) Color.White else colors.keyTextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
