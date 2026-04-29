package com.slumdog88.dictationkeyboardai.ui.keyboard

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardMode
import com.slumdog88.dictationkeyboardai.ui.keyboard.components.Key
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.CharacterKeySpec
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.EmojiLayout
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.KeyboardLayouts
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.KeyboardRowSpec
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.TemplateKey
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.TemplateKeySpec
import com.slumdog88.dictationkeyboardai.ui.keyboard.layouts.SpacerKeySpec
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette
import com.slumdog88.dictationkeyboardai.ui.keyboard.emoji.EmojiKeyboardScreen
import com.slumdog88.dictationkeyboardai.ui.keyboard.components.AlphabetKeyboard
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.text.font.FontWeight
import com.slumdog88.dictationkeyboardai.HapticUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.ui.zIndex

/**
 * Full keyboard UI component.
 *
 * @deprecated Replaced by DictationBarUI in Phase 6. This code is kept dormant
 * to preserve the option to revisit full keyboard functionality. Do not remove.
 */
@Deprecated(
    message = "Replaced by StreamingDictationBar. Kept dormant for potential future use.",
    replaceWith = ReplaceWith(
        "StreamingDictationBar(viewModel, onRecordToggle, onStreamingStart, ...)",
        "com.slumdog88.dictationkeyboardai.ui.dictationbar.StreamingDictationBar"
    ),
    level = DeprecationLevel.WARNING
)
@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    onRecordToggle: (Boolean) -> Unit,
    onStreamingRecordStart: () -> Unit,
    onSwitchKeyboard: () -> Unit
) {
    val colors = KeyboardPalette.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    KeyPreviewHost(viewModel = viewModel, colors = colors, density = density)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight() // Allow dynamic height
            // REMOVED: .background(colors.keyboardBackground) - this prevents the "flash" of background color when expanding
            .windowInsetsPadding(WindowInsets.navigationBars) // Move padding to root container
    ) {
        // Always Visible Dictation Bar (Top)
        // Drawn on top with zIndex so keyboard slides out from under it visually
        DictationBar(
            viewModel = viewModel,
            onRecordToggle = onRecordToggle,
            onStreamingRecordStart = onStreamingRecordStart,
            onSwitchKeyboard = onSwitchKeyboard,
            modifier = Modifier.zIndex(1f)
        )

        val isExpanded = viewModel.isExpanded

        // Pre-calculate layout data for fixed height determination only when expanded.
        val totalKeyboardHeight = if (isExpanded) {
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

            // Calculate fixed height for the keyboard content to optimize animation performance
            remember(rows.size, keyHeight, viewModel.keyboardMode, viewModel.isEmojiSearchActive) {
                if (viewModel.keyboardMode == KeyboardMode.Emoji) {
                    if (viewModel.isEmojiSearchActive) {
                        // Search Mode: Search Bar (56) + Results (100) + Keyboard (~220) + Tabs(48) -> Approx 420-450dp
                        // Let's sum it up:
                        // Search Bar: 56.dp
                        // Emoji Grid (Reduced): 120.dp
                        // Alphabet Keyboard: 4 rows * keyHeight (~55dp) = 220dp
                        // Bottom Bar: 48.dp
                        // Total: ~444dp.
                        // We can be dynamic or fixed. Let's try 450.dp
                        450.dp
                    } else {
                        300.dp // Standard Emoji View
                    }
                } else {
                    keyHeight * rows.size
                }
            }
        } else {
            0.dp
        }

        // Instant transition - no animation
        val expansionProgress = if (isExpanded) 1f else 0f

        // Discrete height
        val boxHeight = totalKeyboardHeight

        Box(
            modifier = Modifier
                .height(boxHeight)
                .fillMaxWidth()
                .clipToBounds()
        ) {
             // Wrap FullKeyboard to apply graphics transformation (though static now)
             Box(
                 modifier = Modifier
                     .fillMaxSize()
                     .graphicsLayer {
                         alpha = expansionProgress
                         translationY = 0f // No movement
                     }
             ) {
                 if (isExpanded) {
                     FullKeyboard(viewModel)
                 }
             }
        }
    }
}

@Composable
fun KeyPreviewHost(
    viewModel: KeyboardViewModel,
    colors: com.slumdog88.dictationkeyboardai.ui.theme.KeyboardColors,
    density: androidx.compose.ui.unit.Density
) {
    val previewState = viewModel.previewState
    if (!previewState.isVisible) return

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(
            x = with(density) { (previewState.position.x + previewState.width.toPx() / 2f).toInt() - 24.dp.roundToPx() },
            y = with(density) { (previewState.position.y - 60.dp.toPx()).toInt() }
        )
    ) {
        Box(
            modifier = Modifier
                .size(48.dp, 70.dp)
                .background(colors.keyPreviewBackground, RoundedCornerShape(8.dp))
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val icon = previewState.icon
            if (icon != null) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = colors.keyTextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                androidx.compose.material3.Text(
                    text = previewState.label,
                    color = colors.keyTextPrimary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DictationBar(
    viewModel: KeyboardViewModel,
    onRecordToggle: (Boolean) -> Unit,
    onStreamingRecordStart: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KeyboardPalette.colors
    val context = LocalContext.current
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.keyboardBackground.copy(alpha = 0.96f))
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT SIDE (Weight 0.7)
        Row(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Expand/Collapse
            IconButton(onClick = { 
                HapticUtils.performKeyClick(context)
                viewModel.toggleExpand() 
            }) {
                Icon(
                    painter = painterResource(
                        id = if (viewModel.isExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up
                    ),
                    contentDescription = if (viewModel.isExpanded) "Collapse" else "Expand",
                    tint = colors.keyTextPrimary
                )
            }
            // Select All / Reprocess Selection
            if (viewModel.hasSelection) {
                IconButton(onClick = {
                    HapticUtils.performKeyClick(context)
                    viewModel.onReprocessSelection()
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = "Reprocess Selection",
                        tint = colors.accent
                    )
                }
            } else {
                IconButton(onClick = {
                    HapticUtils.performKeyClick(context)
                    viewModel.onSelectAll()
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_select_all_grid),
                        contentDescription = "Select All",
                        tint = colors.keyTextPrimary
                    )
                }
            }
        }

        // CENTER PILL BUTTON (Weight 1.3)
        Box(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            DictationButton(
                isRecording = viewModel.isRecording,
                isProcessing = viewModel.isProcessing,
                viewModel = viewModel,
                onClick = {
                    HapticUtils.performKeyClick(context)
                    if (viewModel.isRecording) {
                        // Stop recording (Standard or Streaming)
                        val nowRecording = viewModel.toggleRecording()
                        onRecordToggle(nowRecording)
                        
                        // Force return to keyboard view
                        viewModel.stopStreaming()
                    } else {
                        // Start recording
                        if (viewModel.isAlwaysStreamingEnabled) {
                            // Always start in Streaming Mode
                            if (!viewModel.isExpanded) {
                                viewModel.toggleExpand()
                            }
                            viewModel.isRecording = true
                            viewModel.startStreaming() // Immediate UI switch
                            onStreamingRecordStart()
                        } else {
                            // Start Standard Mode
                            val nowRecording = viewModel.toggleRecording()
                            onRecordToggle(nowRecording)
                        }
                    }
                },
                onDoubleTap = {
                    if (!viewModel.isProcessing) {
                        HapticUtils.performHapticFeedback(context)
                        // Automatically expand keyboard if minimized
                        if (!viewModel.isExpanded) {
                            viewModel.isExpanded = true
                        }
                        viewModel.isRecording = true
                        viewModel.startStreaming() // Immediate UI switch
                        onStreamingRecordStart()
                    }
                },
                onLongClick = {
                    // Haptic is handled in viewModel
                    viewModel.onPasteLastTranscription()
                }
            )
        }

        // RIGHT SIDE (Weight 0.7)
        Row(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Undo / Cancel Button
            // When recording, this becomes a Cancel (Trash) button
            if (viewModel.isRecording) {
                IconButton(onClick = {
                    // Cancel logic
                    viewModel.onCancelRecording()
                    // We need to tell the service to STOP without processing
                    val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.BubbleOverlayService::class.java)
                    intent.action = com.slumdog88.dictationkeyboardai.BubbleOverlayService.ACTION_STOP_DICTATION
                    // We can add an extra to tell service to discard
                    intent.putExtra("discard_recording", true)
                    context.startService(intent)
                    
                    // Force return to keyboard view
                    viewModel.cancelStreaming()
                }) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_delete),
                        contentDescription = "Cancel Recording",
                        tint = MaterialTheme.colorScheme.error // Use error color for destructive action
                    )
                }
            } else {
                if (viewModel.hasSelection) {
                    IconButton(onClick = { 
                        HapticUtils.performKeyClick(context)
                        viewModel.onDeleteClick() 
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Delete Selection",
                            tint = colors.keyTextPrimary
                        )
                    }
                } else {
                    IconButton(onClick = { 
                        HapticUtils.performKeyClick(context)
                        viewModel.onUndo() 
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_undo),
                            contentDescription = "Undo",
                            tint = colors.keyTextPrimary
                        )
                    }
                }
            }
            
            // AI Toggle
            IconButton(onClick = {
                HapticUtils.performKeyClick(context)
                viewModel.toggleAi()
            }) {
                if (viewModel.isAiEnabled) {
                    // Glow effect for enabled state
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        colors.accent.copy(alpha = 0.6f),
                                        colors.accent.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ai_cloud),
                            contentDescription = "AI Toggle",
                            tint = colors.accent
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ai_cloud),
                        contentDescription = "AI Toggle",
                        tint = colors.keyTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun FullKeyboard(viewModel: KeyboardViewModel) {
    val colors = KeyboardPalette.colors
    val context = LocalContext.current
    
    if (viewModel.isStreamingMode) {
        InKeyboardStreamingView(
            viewModel = viewModel
        )
    } else if (viewModel.keyboardMode == KeyboardMode.Emoji) {
        EmojiKeyboardScreen(viewModel = viewModel)
    } else {
        AlphabetKeyboard(viewModel)
    }
}

// Moved internal helpers to public or internal visibility so they can be used by AlphabetKeyboard
@Composable
fun KeyboardRow(
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

@Composable
fun RowScope.CharacterKey(
    keySpec: CharacterKeySpec,
    viewModel: KeyboardViewModel,
    height: androidx.compose.ui.unit.Dp
) {
    val labelText = if (viewModel.isShiftOn && keySpec.label.length == 1 && viewModel.keyboardMode == KeyboardMode.Alphabet) {
        keySpec.label.uppercase()
    } else {
        keySpec.label
    }

    val popupKeys = if (keySpec.secondaryCode != null) {
        // Prepend secondary code to popup list (so it appears first/leftmost)
        (listOf(keySpec.secondaryCode) + keySpec.popup).distinct()
    } else {
        keySpec.popup.distinct()
    }
    val popupBaseIndex = 0 // Default to highlighting the first item (secondary code)

    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Swipe Up Actions (Cut, Copy, Paste, Select All)
    val swipeUpAction = when (keySpec.code.lowercase()) {
        "a" -> { -> viewModel.onSelectAll() }
        "x" -> { -> viewModel.onCut() }
        "c" -> { -> viewModel.onCopy() }
        "v" -> { -> viewModel.onPaste() }
        else -> null
    }

    Key(
        label = labelText,
        secondaryLabel = keySpec.secondaryLabel,
        popupKeys = popupKeys,
        popupBaseIndex = popupBaseIndex,
        weight = keySpec.weight,
        height = height,
        onClick = { viewModel.onKeyClick(keySpec.code) },
        onLongClick = keySpec.secondaryCode?.let { secondary ->
            { viewModel.onKeyClick(secondary) }
        },
        onFlick = keySpec.secondaryCode?.let { secondary ->
            { viewModel.onKeyClick(secondary) }
        },
        onSwipeUp = swipeUpAction,
        onPopupKeyClick = { code -> viewModel.onKeyClick(code) },
        onPressStateChanged = { isPressed, coordinates ->
            if (isPressed && coordinates != null) {
                // Use cached density to avoid Composable calls in lambda
                val width = with(density) { coordinates.size.width.toDp() }
                val height = with(density) { coordinates.size.height.toDp() }
                viewModel.showKeyPreview(labelText, null, coordinates.positionInRoot(), width, height)
            } else {
                viewModel.hideKeyPreview()
            }
        }
    )
}

@Composable
fun RowScope.TemplateKey(
    keySpec: TemplateKeySpec,
    viewModel: KeyboardViewModel,
    height: androidx.compose.ui.unit.Dp
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    val colors = KeyboardPalette.colors
    when (keySpec.template) {
        TemplateKey.Shift -> {
            var label = "Shift"
            var icon: Int? = R.drawable.ic_shift
            var bg = Color.Unspecified
            var fg = Color.Unspecified
            var showIndicator = viewModel.isCapsLockOn

            when (viewModel.keyboardMode) {
                KeyboardMode.Symbols -> {
                    label = "1/2"
                    icon = null
                    showIndicator = false
                }
                KeyboardMode.SymbolsAlt -> {
                    label = "2/2"
                    icon = null
                    bg = colors.accent
                    fg = colors.onAccent
                    showIndicator = false
                }
                else -> {
                    if (viewModel.isShiftOn) {
                        bg = colors.accent
                        fg = colors.onAccent
                    }
                }
            }

            Key(
                label = label,
                icon = icon,
                weight = keySpec.weight,
                height = height,
                isSpecial = true,
                backgroundColor = bg,
                textColor = fg,
                showCapsIndicator = showIndicator,
                onClick = { viewModel.onShiftClick() }
            )
        }

        TemplateKey.Delete -> {
            Key(
                label = "Del",
                icon = R.drawable.ic_delete,
                weight = keySpec.weight,
                height = height,
                isSpecial = true,
                repeatable = true,
                onClick = { viewModel.onDeleteClick() }
            )
        }

        TemplateKey.SymbolsToggle -> {
            Key(
                label = if (viewModel.keyboardMode == KeyboardMode.Alphabet) "?123" else "ABC",
                weight = keySpec.weight,
                height = height,
                isSpecial = true,
                onClick = { viewModel.onSymbolClick() },
                onLongClick = { viewModel.toggleNumberRow() }
            )
        }

        TemplateKey.Comma -> {
            Key(
                label = ",",
                secondaryLabel = "🙂",
                secondaryAlignment = Alignment.TopCenter,
                secondarySize = 18.sp,
                weight = keySpec.weight,
                height = height,
                onClick = { viewModel.onKeyClick(",") },
                onFlick = {
                    viewModel.hideKeyPreview()
                    viewModel.onEmojiClick()
                },
                onLongClick = {
                    viewModel.hideKeyPreview()
                    viewModel.onEmojiClick()
                },
                onPressStateChanged = { isPressed, coordinates ->
                    if (isPressed && coordinates != null) {
                        val width = with(density) { coordinates.size.width.toDp() }
                        val height = with(density) { coordinates.size.height.toDp() }
                        viewModel.showKeyPreview(",", null, coordinates.positionInRoot(), width, height)
                    } else {
                        viewModel.hideKeyPreview()
                    }
                }
            )
        }

        TemplateKey.Space -> {
            Key(
                label = "Space",
                weight = keySpec.weight,
                height = height,
                showPreview = false,
                dragOnLongPress = true, // Enable iOS-style cursor control (Long press -> Drag)
                onClick = { viewModel.onKeyClick(" ") },
                onDrag = { delta -> viewModel.onSpaceBarDrag(delta) }
            )
        }

        TemplateKey.Period -> {
            Key(
                label = ".",
                weight = keySpec.weight,
                height = height,
                popupKeys = listOf("!", "?", ":", ";"),
                onClick = { viewModel.onKeyClick(".") },
                onFlick = { viewModel.onKeyClick("!") }, // Reasonable default flick for period
                onPopupKeyClick = { code -> viewModel.onKeyClick(code) },
                onPressStateChanged = { isPressed, coordinates ->
                    if (isPressed && coordinates != null) {
                        val width = with(density) { coordinates.size.width.toDp() }
                        val height = with(density) { coordinates.size.height.toDp() }
                        viewModel.showKeyPreview(".", null, coordinates.positionInRoot(), width, height)
                    } else {
                        viewModel.hideKeyPreview()
                    }
                }
            )
        }

        TemplateKey.Enter -> {
            // Always show as Enter/Return key regardless of IME options
            Key(
                label = "Enter",
                icon = R.drawable.ic_return,
                weight = keySpec.weight,
                height = height,
                isSpecial = true,
                backgroundColor = colors.accent,
                textColor = colors.onAccent,
                onClick = { 
                    // We still perform the correct IME action if it's not a newline
                    val imeAction = viewModel.imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION
                    if (imeAction != android.view.inputmethod.EditorInfo.IME_ACTION_NONE &&
                        imeAction != android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED) {
                         viewModel.performImeAction()
                    } else {
                        viewModel.onKeyClick("\n")
                    }
                }
            )
        }
    }
}

@Composable
private fun DictationButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    viewModel: KeyboardViewModel,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KeyboardPalette.colors
    val scope = rememberCoroutineScope()
    var longPressTriggered by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f) // Not full height to leave some margin
            .clip(RoundedCornerShape(32.dp))
            .background(
                if (isRecording) colors.micActiveBackground else colors.micIdleBackground
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { 
                        if (!longPressTriggered) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastTapTime < 500) {
                                onDoubleTap()
                                lastTapTime = 0L
                            } else {
                                onClick()
                                lastTapTime = currentTime
                            }
                        }
                        longPressTriggered = false
                    },
                    onPress = {
                        longPressTriggered = false
                        val job = scope.launch {
                            delay(500) // Reduced from 1000ms to be more responsive, but kept safe
                            longPressTriggered = true
                            onLongClick()
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                )
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isProcessing) {
            // Processing State
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colors.micIconColor,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Text(
                    text = "Processing...",
                    color = colors.micIconColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (isRecording) {
            // Waveform State
            WaveformVisualizer(viewModel = viewModel, color = colors.micIconColor)
        } else {
            // Default State
            Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = "Record",
                tint = colors.micIconColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun WaveformVisualizer(viewModel: KeyboardViewModel, color: Color) {
    // Keep history of amplitudes
    // Start with some zeros
    val history = remember { mutableStateListOf<Float>().apply { repeat(40) { add(0f) } } }
    
    LaunchedEffect(Unit) {
        while(isActive) {
            delay(50)
            // Capture latest amplitude from viewModel
            // Normalize: maxAmplitude is usually up to 32767
            // We'll use a non-linear scale to make quiet sounds visible
            val rawAmp = viewModel.currentAmplitude.toFloat()
            val normalized = (rawAmp / 32767f).coerceIn(0f, 1f)
            // Apply some boosting for visibility
            val boosted = kotlin.math.sqrt(normalized)

            history.add(boosted)
            if (history.size > 40) {
                history.removeAt(0)
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
        val barWidth = size.width / 40f
        val middleY = size.height / 2f

        history.forEachIndexed { index, amp ->
            // Min height 2px so it's visible
            val barHeight = (amp * size.height * 0.8f) + 4f
            val x = index * barWidth + (barWidth / 2)

            drawLine(
                color = color,
                start = Offset(x, middleY - barHeight / 2),
                end = Offset(x, middleY + barHeight / 2),
                strokeWidth = barWidth * 0.6f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}
