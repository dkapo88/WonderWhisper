package com.slumdog88.dictationkeyboardai.ui.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.theme.PastelBlue
import com.slumdog88.dictationkeyboardai.ui.theme.PastelGreen
import com.slumdog88.dictationkeyboardai.ui.theme.PastelOrange
import com.slumdog88.dictationkeyboardai.ui.theme.PastelPink
import com.slumdog88.dictationkeyboardai.ui.theme.PastelPurple
import com.slumdog88.dictationkeyboardai.ui.theme.Surface2
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Quick action menu that appears as an arc around the bubble on long-press.
 *
 * @param isExpanded Whether the menu is currently expanded
 * @param isRecording Whether recording is in progress (affects available actions)
 * @param isAIEnabled Whether AI processing is enabled
 * @param direction Direction the menu arc expands (LEFT or RIGHT)
 * @param onAction Callback when an action is selected
 * @param modifier Modifier for the container
 * @param arcRadius Distance from bubble center to action buttons
 * @param buttonSize Size of each action button
 */
@Composable
fun QuickActionMenu(
    isExpanded: Boolean,
    isRecording: Boolean,
    isAIEnabled: Boolean,
    direction: MenuDirection,
    onAction: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
    arcRadius: Dp = 70.dp,
    buttonSize: Dp = 36.dp
) {
    val context = LocalContext.current

    // Actions with their properties - 5 separate buttons for reliability
    val actions = listOf(
        ActionData(
            action = if (isRecording) QuickAction.CANCEL_RECORDING else QuickAction.DISMISS_MENU,
            icon = if (isRecording) R.drawable.ic_trash_white else R.drawable.ic_close,
            color = PastelPink,
            label = if (isRecording) "Cancel Recording" else "Close Menu",
            enabled = true
        ),
        ActionData(
            action = QuickAction.SELECT_ALL,
            icon = R.drawable.ic_select_all_grid,
            color = PastelBlue,
            label = "Select All",
            enabled = true
        ),
        ActionData(
            action = QuickAction.REPROCESS,
            icon = R.drawable.ic_refresh,
            color = PastelGreen,
            label = "Reprocess Selection",
            enabled = true
        ),
        ActionData(
            action = QuickAction.TOGGLE_AI,
            icon = R.drawable.ic_ai_cloud,
            color = if (isAIEnabled) PastelPurple else PastelPurple.copy(alpha = 0.5f),
            label = if (isAIEnabled) "Disable AI Processing" else "Enable AI Processing",
            enabled = true
        ),
        ActionData(
            action = QuickAction.OPEN_SETTINGS,
            icon = R.drawable.ic_settings,
            color = PastelOrange,
            label = "Open Settings",
            enabled = true
        )
    )

    // Staggered visibility for each button
    var visibleButtons by remember { mutableStateOf(0) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            visibleButtons = 0
            actions.forEachIndexed { index, _ ->
                delay(50L)
                visibleButtons = index + 1
            }
        } else {
            visibleButtons = 0
        }
    }

    // Arc positioning based on direction - creates a proper 180° semicircle
    // Button order: Cancel(0) at bottom, Settings(4) at top, middle buttons on the arc
    // LEFT: Arc curves to left of bubble (90° → 270° counterclockwise through 180°)
    // RIGHT: Arc curves to right of bubble (90° → 270° clockwise through 0°)
    val (startAngle, sweepAngle) = when (direction) {
        MenuDirection.LEFT -> Pair(90f, 180f)    // Semicircle on left: bottom → left → top
        MenuDirection.RIGHT -> Pair(90f, -180f)  // Semicircle on right: bottom → right → top
    }
    val angleStep = sweepAngle / (actions.size - 1)

    Box(modifier = modifier) {
        actions.forEachIndexed { index, actionData ->
            val angle = startAngle + (index * angleStep)
            val angleRad = Math.toRadians(angle.toDouble())

            // Calculate position on arc
            val offsetX = (arcRadius.value * cos(angleRad)).roundToInt()
            val offsetY = (arcRadius.value * sin(angleRad)).roundToInt()

            AnimatedVisibility(
                visible = isExpanded && index < visibleButtons,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(animationSpec = tween(150)),
                exit = scaleOut(animationSpec = tween(100)) + fadeOut(animationSpec = tween(100)),
                modifier = Modifier.offset { IntOffset(offsetX.dp.roundToPx(), offsetY.dp.roundToPx()) }
            ) {
                ActionButton(
                    data = actionData,
                    size = buttonSize,
                    onClick = {
                        HapticUtils.performHapticFeedback(context)
                        onAction(actionData.action)
                        // Note: Controller handles menu dismissal in handleQuickAction
                    }
                )
            }
        }
    }
}

/**
 * Individual action button in the quick menu.
 */
@Composable
private fun ActionButton(
    data: ActionData,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                spotColor = data.color.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .background(Surface2)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = data.enabled,
                onClick = onClick
            )
            .semantics { contentDescription = data.label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(data.icon),
            contentDescription = null,
            tint = if (data.enabled) data.color else data.color.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * Data class for action button configuration.
 */
private data class ActionData(
    val action: QuickAction,
    val icon: Int,
    val color: Color,
    val label: String,
    val enabled: Boolean
)
