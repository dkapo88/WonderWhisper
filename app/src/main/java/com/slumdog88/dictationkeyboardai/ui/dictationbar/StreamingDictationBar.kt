package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyPreviewHost
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardMode
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardViewModel
import com.slumdog88.dictationkeyboardai.ui.keyboard.components.AlphabetKeyboard
import com.slumdog88.dictationkeyboardai.ui.keyboard.emoji.EmojiKeyboardScreen
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Secondary characters for number keys (accessed via long-press).
 * Maps number to corresponding shifted character from standard keyboard layout.
 */
private val numberSecondaryChars = mapOf(
    "1" to "!",
    "2" to "@",
    "3" to "#",
    "4" to "$",
    "5" to "%",
    "6" to "^",
    "7" to "&",
    "8" to "*",
    "9" to "(",
    "0" to ")"
)

/**
 * Wrapper composable that combines StreamingPreviewArea with DictationBarScreen.
 *
 * Layout structure:
 * ```
 * Column (fillMaxWidth)
 *   |-- StreamingPreviewArea (conditional, above bar, expands during streaming)
 *   |-- DictationBarScreen (fixed 112dp height, always visible)
 * ```
 *
 * This wrapper enables streaming preview integration by:
 * - Showing the preview area above the keyboard during streaming mode
 * - Passing double-tap callback to trigger streaming mode
 * - Passing cancel callback to discard streaming recording
 *
 * @param viewModel State holder providing streaming state and recording state
 * @param onRecordToggle Callback when recording state should be toggled (single tap)
 * @param onStreamingStart Callback when streaming mode should start (double tap)
 * @param onStreamingCancel Callback to cancel streaming without inserting text
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
 * @param onMicHoldStart Callback when mic hold-to-talk activates (300ms threshold)
 * @param onMicHoldEnd Callback when user releases after hold-to-talk
 * @param onUndo Callback to undo last transcription
 * @param onCancelRecording Callback to cancel current recording without transcribing
 * @param onRetry Callback to retry last failed transcription
 * @param onInsertCharacter Callback to insert a special character at cursor position
 * @param keyboardViewModel Optional KeyboardViewModel for expanded QWERTY keyboard
 * @param onKeyboardLongPress Callback when keyboard switch button is long-pressed
 * @param modifier Optional modifier for the wrapper
 */
@Composable
fun StreamingDictationBar(
    viewModel: DictationBarViewModel,
    onRecordToggle: (Boolean) -> Unit,
    onStreamingStart: () -> Unit,
    onStreamingCancel: () -> Unit,
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
    onMicHoldStart: () -> Unit = {},
    onMicHoldEnd: () -> Unit = {},
    onUndo: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onRetry: () -> Unit = {},
    onInsertCharacter: (String) -> Unit = {},
    keyboardViewModel: KeyboardViewModel? = null,
    onKeyboardLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    KeyboardTheme {
        val context = LocalContext.current
        val colors = KeyboardPalette.colors
        val density = LocalDensity.current

        // Read bottom padding from SharedPreferences (for device-specific adjustments)
        val bottomPadding = remember {
            context.getSharedPreferences("keyboard_prefs", android.content.Context.MODE_PRIVATE)
                .getFloat("keyboard_bottom_padding", 0f)
        }

        // Key preview popup (shared with expanded keyboard and dictation-bar rows)
        if (keyboardViewModel != null) {
            KeyPreviewHost(viewModel = keyboardViewModel, colors = colors, density = density)
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = bottomPadding.dp)
        ) {
            // Preview area (only visible during streaming mode)
            // Expands above the fixed dictation bar
            StreamingPreviewArea(
                isVisible = viewModel.isStreamingMode,
                text = viewModel.streamingText,
                status = viewModel.streamingStatus,
                onCancel = onStreamingCancel
            )

            // Number row (visible for numeric input fields OR manually toggled via long-press)
            if (viewModel.isNumericInput || viewModel.isManualNumberRowVisible) {
                NumberRow(
                    onNumberClick = onInsertCharacter,
                    keyboardViewModel = keyboardViewModel,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Email/URL row (only visible for email/URL input fields)
            // Note: This is mutually exclusive with NumberRow since numeric fields won't be text class
            if (viewModel.isEmailOrUrlInput) {
                EmailUrlRow(
                    onCharacterClick = onInsertCharacter,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Fixed dictation bar (always 112dp height)
            DictationBarScreen(
                viewModel = viewModel,
                onRecordToggle = onRecordToggle,
                onMicDoubleTap = onStreamingStart,
                onMicHoldStart = onMicHoldStart,
                onMicHoldEnd = onMicHoldEnd,
                onSwitchKeyboard = onSwitchKeyboard,
                onSelectAll = onSelectAll,
                onReprocess = onReprocess,
                onAiToggle = onAiToggle,
                onTypedSentenceEndAutocorrectToggle = onTypedSentenceEndAutocorrectToggle,
                onScreenContextToggle = onScreenContextToggle,
                onReturn = onReturn,
                onPasteLast = onPasteLast,
                onBackspace = onBackspace,
                onBackspaceWord = onBackspaceWord,
                onUndo = onUndo,
                onCancelRecording = onCancelRecording,
                onRetry = onRetry,
                onInsertCharacter = onInsertCharacter,
                onKeyboardLongPress = onKeyboardLongPress
            )

            // Expandable QWERTY keyboard (visible on long-press of keyboard button)
            // Instant transition - no animation (avoids jank issues)
            if (viewModel.isKeyboardExpanded && keyboardViewModel != null) {
                if (keyboardViewModel.keyboardMode == KeyboardMode.Emoji) {
                    // Constrain emoji UI to keyboard-sized region (avoid full-screen takeover).
                    val emojiHeight = if (keyboardViewModel.isEmojiSearchActive) 450.dp else 300.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(emojiHeight)
                            .background(colors.keyboardBackground)
                    ) {
                        EmojiKeyboardScreen(viewModel = keyboardViewModel)
                    }
                } else {
                    AlphabetKeyboard(viewModel = keyboardViewModel)
                }
            }
        }
    }
}

/**
 * Compact number row for numeric input fields and manual toggle.
 * Displays 1-9 and 0 in a single row with equal weight distribution.
 * Each key shows its secondary character (!, @, #, etc.) in the top-right corner.
 * - Tap: inserts the number
 * - Long-press: inserts the secondary character
 *
 * @param onNumberClick Callback when a character (number or secondary) is pressed
 * @param modifier Optional modifier
 */
@Composable
private fun NumberRow(
    onNumberClick: (String) -> Unit,
    keyboardViewModel: KeyboardViewModel? = null,
    modifier: Modifier = Modifier
) {
    val colors = KeyboardPalette.colors
    val density = LocalDensity.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.keyboardBackground)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { number ->
            val secondaryChar = numberSecondaryChars[number]
            NumberKey(
                number = number,
                secondaryChar = secondaryChar,
                onClick = { onNumberClick(number) },
                onLongClick = secondaryChar?.let { secondary ->
                    {
                        onNumberClick(secondary)
                    }
                },
                onPreviewStateChanged = if (keyboardViewModel != null) { isPressed, coordinates, previewLabel ->
                    if (isPressed && coordinates != null && !previewLabel.isNullOrEmpty()) {
                        val width = with(density) { coordinates.size.width.toDp() }
                        val height = with(density) { coordinates.size.height.toDp() }
                        keyboardViewModel.showKeyPreview(previewLabel, null, coordinates.positionInRoot(), width, height)
                    } else {
                        keyboardViewModel.hideKeyPreview()
                    }
                } else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Individual number key with press animation, secondary character hint, and long-press support.
 *
 * @param number The primary number character to display
 * @param secondaryChar Optional secondary character shown in top-right corner (for long-press)
 * @param onClick Callback when key is tapped (inserts number)
 * @param onLongClick Optional callback when key is long-pressed (inserts secondary character)
 * @param modifier Optional modifier
 */
@Composable
private fun NumberKey(
    number: String,
    secondaryChar: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onPreviewStateChanged: ((Boolean, LayoutCoordinates?, String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = KeyboardPalette.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var keyCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "numberKeyScale"
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .padding(horizontal = 2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = colors.keyBackground,
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(number, secondaryChar, onLongClick != null) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    onPreviewStateChanged?.invoke(true, keyCoordinates, number)
                    HapticUtils.performKeyClick(context)

                    var longPressTriggered = false
                    val longPressJob = if (onLongClick != null) {
                        scope.launch {
                            delay(350)
                            if (isPressed) {
                                longPressTriggered = true
                                onPreviewStateChanged?.invoke(true, keyCoordinates, secondaryChar ?: number)
                                HapticUtils.performGesturalFeedback(context)
                                onLongClick()
                            }
                        }
                    } else {
                        null
                    }

                    val up = waitForUpOrCancellation()
                    longPressJob?.cancel()
                    isPressed = false
                    onPreviewStateChanged?.invoke(false, null, null)

                    if (up != null) {
                        up.consume()
                        if (!longPressTriggered) {
                            onClick()
                        }
                    }
                }
            }
            .onGloballyPositioned { coordinates ->
                keyCoordinates = coordinates
            },
        contentAlignment = Alignment.Center
    ) {
        // Main number in center
        Text(
            text = number,
            color = colors.keyTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(if (secondaryChar != null) Alignment.BottomCenter else Alignment.Center)
                .padding(bottom = if (secondaryChar != null) 4.dp else 0.dp)
        )

        // Secondary character in top-right corner
        if (secondaryChar != null) {
            Text(
                text = secondaryChar,
                color = colors.keyTextPrimary.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 3.dp)
            )
        }
    }
}

/**
 * Compact character row for email and URL input fields.
 * Displays common email/URL characters: .com, @, /, :, -, .
 * Styled to match the dictation bar aesthetic.
 *
 * @param onCharacterClick Callback when a character/string is pressed
 * @param modifier Optional modifier
 */
@Composable
private fun EmailUrlRow(
    onCharacterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KeyboardPalette.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.keyboardBackground)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Email/URL quick characters - .com gets more weight as it's longer
        listOf(".com", "@", "/", ":", "-", ".").forEach { char ->
            EmailUrlKey(
                text = char,
                onClick = { onCharacterClick(char) },
                modifier = Modifier.weight(if (char == ".com") 1.5f else 1f)
            )
        }
    }
}

/**
 * Individual email/URL key with press animation.
 */
@Composable
private fun EmailUrlKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KeyboardPalette.colors
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "emailUrlKeyScale"
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .padding(horizontal = 2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = colors.keyBackground,
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(text) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    HapticUtils.performKeyClick(context)

                    val up = waitForUpOrCancellation()
                    isPressed = false

                    if (up != null) {
                        up.consume()
                        onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.keyTextPrimary,
            fontSize = if (text.length > 1) 14.sp else 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
