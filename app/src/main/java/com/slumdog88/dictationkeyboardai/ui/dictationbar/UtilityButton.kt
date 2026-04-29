package com.slumdog88.dictationkeyboardai.ui.dictationbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette

/**
 * Reusable utility button for dictation bar functions.
 *
 * Features:
 * - Press animation: Scales to 0.92f when pressed
 * - Haptic feedback: Triggers HapticUtils.performKeyClick on click
 * - Touch target: 48dp minimum (Material guidelines) with 36dp visual size
 * - No ripple indication (uses scale animation instead)
 * - Optional long-press support with gestural haptic feedback
 *
 * @param icon Drawable resource ID for the button icon
 * @param contentDescription Accessibility description for the button
 * @param onClick Callback when button is clicked
 * @param modifier Optional modifier for the button
 * @param tint Optional color tint for the icon (defaults to keyTextPrimary)
 * @param enabled Whether the button is enabled (defaults to true)
 * @param onLongClick Optional callback when button is long-pressed
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UtilityButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = KeyboardPalette.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "utilityButtonScale"
    )

    val iconTint = when {
        !enabled -> colors.keyTextSecondary.copy(alpha = 0.5f)
        tint != null -> tint
        else -> colors.keyTextPrimary
    }

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .let { mod ->
                if (onLongClick != null) {
                    mod.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = {
                            HapticUtils.performKeyClick(context)
                            onClick()
                        },
                        onLongClick = {
                            HapticUtils.performGesturalFeedback(context)
                            onLongClick()
                        }
                    )
                } else {
                    mod.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = {
                            HapticUtils.performKeyClick(context)
                            onClick()
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}
