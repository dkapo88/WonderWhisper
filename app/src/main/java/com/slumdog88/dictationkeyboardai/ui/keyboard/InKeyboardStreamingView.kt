package com.slumdog88.dictationkeyboardai.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette

@Composable
fun InKeyboardStreamingView(
    viewModel: KeyboardViewModel
) {
    val colors = KeyboardPalette.colors
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Auto-scroll to bottom when text changes
    LaunchedEffect(viewModel.streamingText) {
        val maxValue = scrollState.maxValue
        val distanceFromBottom = maxValue - scrollState.value
        val thresholdPx = with(density) { 48.dp.toPx() }
        if (distanceFromBottom <= thresholdPx) {
            scrollState.scrollTo(maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.keyboardBackground)
            .padding(8.dp)
    ) {
        // Scrollable Text Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.keyBackground)
                .padding(12.dp)
        ) {
            Text(
                text = viewModel.streamingText.ifEmpty { "..." },
                color = colors.keyTextPrimary,
                fontSize = 18.sp,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }
    }
}
