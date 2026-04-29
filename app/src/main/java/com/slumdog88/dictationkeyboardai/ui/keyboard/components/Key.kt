package com.slumdog88.dictationkeyboardai.ui.keyboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.annotation.DrawableRes
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun RowScope.Key(
    label: String,
    secondaryLabel: String? = null,
    popupKeys: List<String>? = null,
    @DrawableRes icon: Int? = null,
    @DrawableRes secondaryIcon: Int? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    weight: Float = 1f,
    isSpecial: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onPopupKeyClick: ((String) -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    dragOnLongPress: Boolean = false,
    repeatable: Boolean = false,
    repeatDelayMs: Long = 350,
    repeatIntervalMs: Long = 50,
    showCapsIndicator: Boolean = false,
    popupBaseIndex: Int? = null,
    height: androidx.compose.ui.unit.Dp = 56.dp,
    showPreview: Boolean = true,
    onFlick: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    onPressStateChanged: ((Boolean, LayoutCoordinates?) -> Unit)? = null,
    secondaryAlignment: Alignment = Alignment.TopEnd,
    secondarySize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    // Callback holders to handle recomposition without restarting pointerInput
    val currentOnClick = rememberUpdatedState(onClick)
    val currentOnLongClick = rememberUpdatedState(onLongClick)
    val currentOnPopupKeyClick = rememberUpdatedState(onPopupKeyClick)
    val currentOnDrag = rememberUpdatedState(onDrag)
    val currentOnFlick = rememberUpdatedState(onFlick)
    val currentOnSwipeUp = rememberUpdatedState(onSwipeUp)
    val currentOnPressStateChanged = rememberUpdatedState(onPressStateChanged)

    // State for manual gesture handling
    var isPressed by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }
    var keySize by remember { mutableStateOf(IntSize.Zero) }
    var selectedPopupIndex by remember { mutableStateOf(-1) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    var popupStartX by remember { mutableStateOf<Float?>(null) }

    // Popup positioning state
    var popupOffsetX by remember { mutableStateOf(0f) }
    var popupOffsetY by remember { mutableStateOf(0f) }

    val palette = KeyboardPalette.colors
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(10.dp)
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    // Visual resolution
    val resolvedBackground = when {
        backgroundColor != Color.Unspecified -> backgroundColor
        isSpecial -> palette.functionalKeyBackground
        else -> palette.keyBackground
    }
    val resolvedPressedBackground = when {
        backgroundColor != Color.Unspecified -> backgroundColor
        isSpecial -> palette.functionalKeyPressedBackground
        else -> palette.keyPressedBackground
    }
    val resolvedTextColor = when {
        textColor != Color.Unspecified -> textColor
        isSpecial -> palette.functionalKeyText
        else -> palette.keyTextPrimary
    }

    // Animation for press effect
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "keyScale")
    val context = LocalContext.current

    var keyCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .weight(weight)
            .height(height)
            .pointerInput(label, popupKeys, isSpecial, dragOnLongPress, repeatable) {
                awaitPointerEventScope {
                    while (true) {
                        var longPressJob: Job? = null
                        var repeatKeyJob: Job? = null
                        var completed = false
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            
                            // Immediate Haptic Feedback on Touch Down (Low Latency)
                            HapticUtils.performKeyClick(context)
                            
                            // Notify press start for preview
                            if (showPreview) {
                                currentOnPressStateChanged.value?.invoke(true, keyCoordinates)
                            }

                            var isLongPressTriggered = false
                            var isFlickTriggered = false

                            // Start Long Press Timer
                            if (popupKeys?.isNotEmpty() == true || currentOnLongClick.value != null || dragOnLongPress) {
                                longPressJob = scope.launch {
                                    delay(350) // Long press delay
                                    if (isPressed && !isFlickTriggered) {
                                        isLongPressTriggered = true
                                        isLongPressing = true
                                        if (popupKeys?.isNotEmpty() == true) {
                                            showPopup = true
                                            selectedPopupIndex = 0 // Default to first item
                                            
                                            // Calculate Popup Position Logic
                                            val itemWidth = with(density) { 44.dp.toPx() }
                                            val itemHeight = with(density) { 54.dp.toPx() }
                                            val maxCols = 5
                                            val count = popupKeys.size
                                            val cols = kotlin.math.min(count, maxCols)
                                            val rows = kotlin.math.ceil(count.toDouble() / maxCols).toInt()
                                            val popupWidth = cols * itemWidth
                                            val popupHeight = rows * itemHeight
                                            
                                            val keyGlobalX = keyCoordinates?.positionInRoot()?.x ?: 0f
                                            val keyWidth = size.width.toFloat()
                                            
                                            // Align the first column (Index 0) with the key center
                                            // We want: popupX + itemWidth/2 = keyWidth/2
                                            // So: popupX = keyWidth/2 - itemWidth/2
                                            var idealX = (keyWidth - itemWidth) / 2f
                                            
                                            // Check bounds
                                            val globalPopupX = keyGlobalX + idealX
                                            if (globalPopupX < 10f) {
                                                idealX += (10f - globalPopupX) // Shift right
                                            } else if (globalPopupX + popupWidth > screenWidthPx - 10f) {
                                                idealX -= (globalPopupX + popupWidth - (screenWidthPx - 10f)) // Shift left
                                            }
                                            
                                            popupOffsetX = idealX
                                            // Position immediately above the key (reduce gap from 40dp to 8dp)
                                            val verticalOffset = with(density) { 8.dp.toPx() } 
                                            popupOffsetY = -popupHeight - verticalOffset
                                        } else {
                                            currentOnLongClick.value?.invoke()
                                        }
                                        HapticUtils.performGesturalFeedback(context)
                                    }
                                }
                            }

                            // Start Repeat Job
                            if (repeatable) {
                                currentOnClick.value()
                                repeatKeyJob = scope.launch {
                                    delay(repeatDelayMs)
                                    while (isPressed && !isFlickTriggered && !isLongPressTriggered) {
                                        currentOnClick.value()
                                        delay(repeatIntervalMs)
                                    }
                                }
                            }

                            // Track Movement (Flick & Drag)
                            // Reset flick tracking
                            val startX = down.position.x
                            val startY = down.position.y
                            val flickThreshold = with(density) { 20.dp.toPx() } // 20dp movement for flick

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                
                                val currentX = change.position.x
                                val currentY = change.position.y
                                val deltaX = currentX - startX
                                val deltaY = currentY - startY

                                // Calculate Vertical Flick (Down)
                                if (!isFlickTriggered && !isLongPressTriggered && currentOnFlick.value != null) {
                                    if (deltaY > flickThreshold && kotlin.math.abs(deltaX) < flickThreshold) { // Dragged Down significantly more than horizontal
                                        isFlickTriggered = true
                                        currentOnFlick.value?.invoke()
                                        HapticUtils.performGesturalFeedback(context)
                                        // Cancel other actions
                                        longPressJob?.cancel()
                                        repeatKeyJob?.cancel()
                                    }
                                }
                                
                                // Calculate Swipe Up
                                if (!isFlickTriggered && !isLongPressTriggered && currentOnSwipeUp.value != null) {
                                    if (deltaY < -flickThreshold && kotlin.math.abs(deltaX) < flickThreshold) { // Dragged Up significantly more than horizontal
                                        isFlickTriggered = true
                                        currentOnSwipeUp.value?.invoke()
                                        HapticUtils.performGesturalFeedback(context)
                                        // Cancel other actions
                                        longPressJob?.cancel()
                                        repeatKeyJob?.cancel()
                                    }
                                }

                                // Calculate Horizontal Drag (e.g. Spacebar)
                                if (currentOnDrag.value != null && !isFlickTriggered && (
                                        (!dragOnLongPress && !isLongPressTriggered) ||
                                        (dragOnLongPress && isLongPressTriggered)
                                    )) {
                                    val delta = change.position.x - change.previousPosition.x
                                    currentOnDrag.value?.invoke(delta)
                                }

                                // Handle Popup Selection (if showing)
                                if (showPopup && popupKeys != null) {
                                    val itemWidth = with(density) { 44.dp.toPx() }
                                    val itemHeight = with(density) { 54.dp.toPx() }
                                    val maxCols = 5
                                    
                                    // Finger position relative to Popup Origin
                                    val fingerX = currentX - popupOffsetX
                                    val fingerY = currentY - popupOffsetY
                                    
                                    val col = (fingerX / itemWidth).toInt()
                                    val rowFromTop = (fingerY / itemHeight).toInt()
                                    val rows = kotlin.math.ceil(popupKeys.size.toDouble() / maxCols).toInt()
                                    
                                    // We want the bottom row to be index 0..maxCols-1
                                    val logicalRow = rows - 1 - rowFromTop
                                    
                                    if (col in 0 until maxCols && logicalRow in 0 until rows) {
                                        // Index calculation: row 0 is bottom
                                        val idx = logicalRow * maxCols + col
                                        if (idx in popupKeys.indices) {
                                            selectedPopupIndex = idx
                                        }
                                    }
                                }

                                change.consume() // Consume events to prevent other detectors (though we are the main one)

                            } while (event.changes.any { it.pressed })

                            // Touch Up
                            isPressed = false
                            longPressJob?.cancel()
                            repeatKeyJob?.cancel()
                            
                            // Notify press end for preview
                            if (showPreview) {
                                currentOnPressStateChanged.value?.invoke(false, null)
                            }
                            
                            if (!isFlickTriggered && !isLongPressTriggered) {
                                if (showPopup) {
                                    // Popup Selection Confirmation
                                    if (selectedPopupIndex != -1 && popupKeys != null) {
                                        currentOnPopupKeyClick.value?.invoke(popupKeys[selectedPopupIndex])
                                    }
                                } else {
                                    // Standard Tap
                                    if (!repeatable) { // Repeatable already fired on down
                                        currentOnClick.value()
                                    }
                                }
                            } else if (showPopup) {
                                 // Popup released after selection
                                 if (selectedPopupIndex != -1 && popupKeys != null) {
                                    currentOnPopupKeyClick.value?.invoke(popupKeys[selectedPopupIndex])
                                 }
                            }

                            // Reset State
                            isLongPressing = false
                            showPopup = false
                            selectedPopupIndex = -1
                            completed = true
                        } finally {
                            if (!completed) {
                                isPressed = false
                                isLongPressing = false
                                showPopup = false
                                selectedPopupIndex = -1
                                longPressJob?.cancel()
                                repeatKeyJob?.cancel()
                                if (showPreview) {
                                    currentOnPressStateChanged.value?.invoke(false, null)
                                }
                            }
                        }
                    }
                }
            }
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .scale(scale)
            .shadow(elevation = 3.dp, shape = shape, clip = false)
            .clip(shape)
            .background(if (isPressed) resolvedPressedBackground else resolvedBackground)
            .border(1.dp, palette.keyTextSecondary.copy(alpha = 0.12f), shape)
            .onGloballyPositioned { coordinates ->
                keySize = coordinates.size
                keyCoordinates = coordinates
            },
        contentAlignment = Alignment.Center
    ) {
        // Popup Menu
        if (showPopup && !popupKeys.isNullOrEmpty()) {
            Popup(
                alignment = Alignment.TopStart, // We calculate offset manually
                offset = IntOffset(popupOffsetX.toInt(), popupOffsetY.toInt())
            ) {
                val maxCols = 5
                val itemWidth = 44.dp
                val itemHeight = 54.dp
                val count = popupKeys.size
                val cols = kotlin.math.min(count, maxCols)
                
                // Logic to display grid
                // We want index 0 at bottom-left
                // So we render rows in reverse order?
                // Column{ Row(indices 5..9), Row(indices 0..4) }
                
                Column(
                    modifier = Modifier
                        .width(itemWidth * cols)
                        .background(palette.functionalKeyBackground, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    val rows = kotlin.math.ceil(count.toDouble() / maxCols).toInt()
                    
                    // Iterate rows from TOP (highest index) to BOTTOM (lowest index)
                    // If rows=2: i=1 (top), i=0 (bottom)
                    for (r in (rows - 1) downTo 0) {
                        Row(modifier = Modifier.height(itemHeight)) {
                            for (c in 0 until maxCols) {
                                val idx = r * maxCols + c
                                if (idx < count) {
                                    val key = popupKeys[idx]
                                    Box(
                                        modifier = Modifier
                                            .width(itemWidth)
                                            .fillMaxHeight()
                                            .padding(1.dp)
                                            .background(
                                                if (idx == selectedPopupIndex) palette.accent else Color.Transparent,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable { 
                                                onPopupKeyClick?.invoke(key)
                                                showPopup = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            color = if (idx == selectedPopupIndex) palette.onAccent else resolvedTextColor,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(itemWidth))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Labels or Icon
        if (icon != null) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = label,
                tint = resolvedTextColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
            if (secondaryLabel != null) {
                Text(
                    text = secondaryLabel,
                    color = resolvedTextColor.copy(alpha = 0.8f), // Slightly increased opacity for visibility
                    fontSize = secondarySize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(secondaryAlignment)
                        .padding(
                            top = 2.dp, 
                            end = if (secondaryAlignment == Alignment.TopEnd) 4.dp else 0.dp
                        )
                )
            }
            
            if (secondaryIcon != null) {
                Icon(
                    painter = painterResource(id = secondaryIcon),
                    contentDescription = null,
                    tint = resolvedTextColor.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(secondaryAlignment)
                        .padding(
                            top = 2.dp, 
                            end = if (secondaryAlignment == Alignment.TopEnd) 4.dp else 0.dp
                        )
                        .size(12.dp)
                )
            }

            Text(
                text = label,
                color = resolvedTextColor,
                fontSize = if (isSpecial) 14.sp else 20.sp,
                fontWeight = if (isSpecial) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .align(if (secondaryLabel != null || secondaryIcon != null) Alignment.BottomCenter else Alignment.Center)
                    .padding(bottom = if (secondaryLabel != null || secondaryIcon != null) 4.dp else 0.dp)
            )
        }

        if (showCapsIndicator) {
            val indicatorColor = when {
                backgroundColor != Color.Unspecified && backgroundColor == palette.accent -> palette.onAccent
                else -> palette.accent
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .width(18.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(indicatorColor)
            )
        }
    }
}
