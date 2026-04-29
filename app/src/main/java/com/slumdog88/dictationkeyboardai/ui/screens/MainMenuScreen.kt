package com.slumdog88.dictationkeyboardai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.PowerManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.provider.Settings.Secure
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlin.OptIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.navigation.NavigationActions
import com.slumdog88.dictationkeyboardai.ui.theme.Radii
import com.slumdog88.dictationkeyboardai.utils.StatisticsManager
import com.slumdog88.dictationkeyboardai.utils.SettingsManager
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MainMenuScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isSimpleMode by remember { mutableStateOf(prefs.getBoolean("is_simple_mode", true)) }
    var showModeDialog by remember { mutableStateOf(false) }
    var targetSimpleMode by remember { mutableStateOf(true) }
    val settingsManager = remember { SettingsManager(context) }
    // Removed isStreamingDictationEnabled state as it is no longer needed in main menu

    // Warning states
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityEnabled(context)) }
    var hasMicPermission by remember { mutableStateOf(checkMicPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(canDrawOverlays(context)) }
    var batteryOptDisabled by remember { mutableStateOf(isBatteryOptimizationDisabled(context)) }

    var imeStatus by remember { mutableStateOf(getImeStatus(context)) }

    // Dialog state for reset confirmation
    var showResetConfirmation by remember { mutableStateOf(false) }

    // Permission launcher for microphone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    // Refresh checks when screen is first shown
    LaunchedEffect(Unit) {
        isAccessibilityEnabled = checkAccessibilityEnabled(context)
        hasMicPermission = checkMicPermission(context)
        hasOverlayPermission = canDrawOverlays(context)
        batteryOptDisabled = isBatteryOptimizationDisabled(context)
        imeStatus = getImeStatus(context)
    }

    // Function to refresh stats display
    fun refreshStats() {
        // This will be called to refresh the stats display
    }

    // Function to show reset confirmation dialog
    fun showResetDialog() {
        showResetConfirmation = true
        HapticUtils.performHapticFeedback(context)
    }

    // Function to reset all statistics (called after confirmation)
    fun confirmResetStats() {
        val statisticsManager = StatisticsManager(context)
        statisticsManager.resetStatistics()
        showResetConfirmation = false
        HapticUtils.performHapticFeedback(context)
    }

    // Function to cancel reset
    fun cancelReset() {
        showResetConfirmation = false
    }

    // Full-screen background that extends behind system UI
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Background gradient that extends to full screen including behind system bars
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.background
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Scaffold with transparent container to allow background to show through
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { innerPadding ->
            // Foreground content with scrollable layout
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(16.dp)) }
                // Top warning cards (accessibility, microphone, overlay, keyboard)
                if (!isAccessibilityEnabled) {
                    item {
                        WarningCard(
                            title = "Enable Accessibility Service",
                            description = "Required to insert dictated text, read selected text, and detect text fields.",
                            actionText = "Open Accessibility Settings",
                            accent = MaterialTheme.colorScheme.tertiary,
                            onAction = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) { }
                            },
                            onRefresh = {
                                isAccessibilityEnabled = checkAccessibilityEnabled(context)
                                hasMicPermission = checkMicPermission(context)
                                hasOverlayPermission = canDrawOverlays(context)
                                batteryOptDisabled = isBatteryOptimizationDisabled(context)
                                imeStatus = getImeStatus(context)
                                HapticUtils.performHapticFeedback(context)
                            }
                        )
                    }
                }
                if (!hasMicPermission) {
                    item {
                        WarningCard(
                            title = "Microphone Permission Needed",
                            description = "Required to record audio for dictation and voice notes.",
                            actionText = "Grant Microphone Access",
                            accent = MaterialTheme.colorScheme.primary,
                            onAction = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onRefresh = {
                                isAccessibilityEnabled = checkAccessibilityEnabled(context)
                                hasMicPermission = checkMicPermission(context)
                                hasOverlayPermission = canDrawOverlays(context)
                                batteryOptDisabled = isBatteryOptimizationDisabled(context)
                                imeStatus = getImeStatus(context)
                                HapticUtils.performHapticFeedback(context)
                            }
                        )
                    }
                }
                if (!hasOverlayPermission) {
                    item {
                        WarningCard(
                            title = "Overlay Permission Recommended",
                            description = "Allows the dictation bubble to appear across apps.",
                            actionText = "Allow Display Over Apps",
                            accent = MaterialTheme.colorScheme.secondary,
                            onAction = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            },
                            onRefresh = {
                                isAccessibilityEnabled = checkAccessibilityEnabled(context)
                                hasMicPermission = checkMicPermission(context)

                                hasOverlayPermission = canDrawOverlays(context)
                                batteryOptDisabled = isBatteryOptimizationDisabled(context)
                                imeStatus = getImeStatus(context)
                                HapticUtils.performHapticFeedback(context)
                            }
                        )
                    }
                }
                // Battery Optimization warning
                if (!batteryOptDisabled) {
                    item {
                        WarningCard(
                            title = "Disable Battery Optimization",
                            description = "Prevents Android from killing the accessibility service in the background.",
                            actionText = "Fix Battery Optimization",
                            accent = MaterialTheme.colorScheme.error,
                            onAction = {
                                try {
                                    // Best: direct request for exemption
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // Fallback: open general ignore battery optimization settings
                                    try {
                                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(fallback)
                                    } catch (_: Exception) {
                                        // Last resort: open battery saver settings
                                        try {
                                            val last = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                            last.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(last)
                                        } catch (_: Exception) { }
                                    }
                                }
                            },
                            onRefresh = {
                                isAccessibilityEnabled = checkAccessibilityEnabled(context)
                                hasMicPermission = checkMicPermission(context)
                                hasOverlayPermission = canDrawOverlays(context)
                                batteryOptDisabled = isBatteryOptimizationDisabled(context)
                                imeStatus = getImeStatus(context)
                                HapticUtils.performHapticFeedback(context)
                            }
                        )
                    }
                }


                // IME warnings
                if (!imeStatus.enabled) {
                    item {
                        WarningCard(
                            title = "Enable WonderWhisper Keyboard",
                            description = "Required to type via the IME and integrate dictation into any app.",
                            actionText = "Open Keyboard Settings",
                            accent = MaterialTheme.colorScheme.tertiary,
                            onAction = {
                                try {
                                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            },
                            onRefresh = {
                                isAccessibilityEnabled = checkAccessibilityEnabled(context)
                                hasMicPermission = checkMicPermission(context)
                                hasOverlayPermission = canDrawOverlays(context)
                                batteryOptDisabled = isBatteryOptimizationDisabled(context)
                                imeStatus = getImeStatus(context)
                                HapticUtils.performHapticFeedback(context)
                            }
                        )
                    }
                } else if (imeStatus.enabled && !imeStatus.selected) {
                    item {
                        WarningCard(
                            title = "Set WonderWhisper as Current Keyboard",
                            description = "Tap to switch keyboard now. You can change it anytime.",
                            actionText = "Switch Keyboard",
                            accent = MaterialTheme.colorScheme.primary,
                            onAction = {
                                try {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                    imm.showInputMethodPicker()
                                } catch (_: Exception) { }
                            },
                            onRefresh = {
                                isAccessibilityEnabled = checkAccessibilityEnabled(context)
                                hasMicPermission = checkMicPermission(context)
                                hasOverlayPermission = canDrawOverlays(context)
                                batteryOptDisabled = isBatteryOptimizationDisabled(context)
                                imeStatus = getImeStatus(context)
                                HapticUtils.performHapticFeedback(context)
                            }
                        )
                    }
                }

                // Sticky header with title and mode toggle
                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        HeaderWithModeToggle(
                            isSimpleMode = isSimpleMode,
                            onSimple = {
                                if (!isSimpleMode) { // Only show dialog if actually switching
                                    targetSimpleMode = true
                                    showModeDialog = true
                                }
                                HapticUtils.performHapticFeedback(context)
                            },
                            onPro = {
                                if (isSimpleMode) { // Only show dialog if actually switching
                                    targetSimpleMode = false
                                    showModeDialog = true
                                }
                                HapticUtils.performHapticFeedback(context)
                            }
                        )
                    }
                }

                // Statistics Card
                item {
                    Spacer(Modifier.height(8.dp))
                    StatisticsCard(context, ::showResetDialog)
                }

                if (!isSimpleMode) {
                    // StreamingToggleCard removed from here and moved to proMenu
                }

                // Menu items in a grid
                item {
                    Spacer(Modifier.height(8.dp))
                    val actions = remember { NavigationActions(context) }
                    val palette = MaterialTheme.colorScheme
                    val menu = if (isSimpleMode) {
                        simpleMenu(context, navController, actions, palette)
                    } else {
                        proMenu(context, navController, actions, palette)
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (item in menu) {
                            MenuTile(
                                icon = item.icon,
                                label = item.label,
                                accent = item.accent,
                                modifier = Modifier
                                    .weight(1f, fill = true)
                                    .widthIn(min = 140.dp, max = 220.dp),
                                onClick = {
                                    item.onClick()
                                    HapticUtils.performHapticFeedback(context)
                                }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }

        // Mode explanation dialog
        if (showModeDialog) {
            ModeExplanationDialog(
                isSimpleMode = targetSimpleMode,
                onConfirm = {
                    isSimpleMode = targetSimpleMode
                    prefs.edit().putBoolean("is_simple_mode", targetSimpleMode).apply()
                    showModeDialog = false
                },
                onDismiss = {
                    showModeDialog = false
                }
            )
        }

        // Reset statistics confirmation dialog
        if (showResetConfirmation) {
            AlertDialog(
                onDismissRequest = { cancelReset() },
                title = {
                    Text(
                        text = "Reset Statistics",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = "Do you want to clear or reset all your stats? This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { confirmResetStats() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { cancelReset() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

    }
}

@Composable
private fun HeaderWithModeToggle(
    isSimpleMode: Boolean,
    onSimple: () -> Unit,
    onPro: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "WONDER",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "WHISPER",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModePill(text = "SIMPLE", selected = isSimpleMode, onClick = onSimple)
            ModePill(text = "PRO", selected = !isSimpleMode, onClick = onPro)
        }
    }
}

@Composable
private fun ModePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .height(36.dp)
            .background(bg, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
    }
}

private data class MenuItemDef(
    val icon: Int,
    val label: String,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun simpleMenu(
    context: Context,
    navController: NavController,
    actions: NavigationActions,
    palette: androidx.compose.material3.ColorScheme
): List<MenuItemDef> {
    return listOf(
        MenuItemDef(R.drawable.ic_key, "API Keys", palette.primary) {
            actions.navigateToApiKeys()
        },
        MenuItemDef(R.drawable.ic_help, "How-To Guide", palette.tertiary) {
            // Launch as an Activity to reuse system/activity transition and hide bottom bar
            val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.HowToGuideActivity::class.java)
            context.startActivity(intent)
        },
        MenuItemDef(R.drawable.ic_settings, "Settings", palette.primary) {
            actions.navigateToSimpleSettings()
        },
        MenuItemDef(R.drawable.ic_keyboard, "Keyboard", palette.tertiary) {
            com.slumdog88.dictationkeyboardai.KeyboardSettingsActivity.start(context)
        },
        MenuItemDef(R.drawable.ic_book, "Custom Vocabulary", palette.secondary) {
            actions.navigateToVocabulary()
        },
        MenuItemDef(R.drawable.ic_lock, "Privacy & Permissions", palette.secondary) {
            actions.navigateToAccessibilityDisclosure()
        },
        MenuItemDef(R.drawable.ic_email, "Feedback & Bugs", palette.primary) {
            actions.navigateToFeedback()
        },
        MenuItemDef(R.drawable.ic_info, "About", palette.primary) {
            actions.navigateToAbout()
        }
    )
}

/* ---- IME utilities ---- */

private data class ImeStatus(
    val enabled: Boolean,
    val selected: Boolean
)

private fun getImeStatus(context: Context): ImeStatus {
    return try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledList = imm.enabledInputMethodList
        val isEnabled = enabledList.any { it.packageName == context.packageName }

        val currentId = Secure.getString(context.contentResolver, Secure.DEFAULT_INPUT_METHOD) ?: ""
        val isSelected = currentId.startsWith(context.packageName)

        ImeStatus(enabled = isEnabled, selected = isSelected)
    } catch (_: Exception) {
        ImeStatus(enabled = false, selected = false)
    }
}

@Composable
private fun proMenu(
    context: Context,
    navController: NavController,
    actions: NavigationActions,
    palette: androidx.compose.material3.ColorScheme
): List<MenuItemDef> {
    return listOf(
        MenuItemDef(R.drawable.ic_key, "API Keys", palette.primary) {
            actions.navigateToApiKeys()
        },
        MenuItemDef(R.drawable.ic_help, "How-To Guide", palette.tertiary) {
            // Use Activity launch to keep transition consistent with Simple mode and other cards
            val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.HowToGuideActivity::class.java)
            context.startActivity(intent)
        },
        MenuItemDef(R.drawable.ic_megaphone, "AI Prompt", palette.secondary) {
            actions.navigateToAiPrompt()
        },
        MenuItemDef(R.drawable.ic_book, "Custom Vocabulary", palette.tertiary) {
            actions.navigateToVocabulary()
        },
        MenuItemDef(R.drawable.ic_settings, "AI Models & Settings", palette.primary) {
            actions.navigateToAiModels()
        },
        MenuItemDef(R.drawable.ic_mic, "Streaming Dictation", palette.secondary) {
            actions.navigateToStreamingSettings()
        },
        MenuItemDef(R.drawable.ic_keyboard, "Keyboard", palette.tertiary) {
            com.slumdog88.dictationkeyboardai.KeyboardSettingsActivity.start(context)
        },
        MenuItemDef(R.drawable.ic_settings, "Bubble Appearance", palette.tertiary) {
            actions.navigateToBubbleAppearance()
        },
        MenuItemDef(R.drawable.ic_mic_white, "Dictation Test", palette.secondary) {
            actions.navigateToDictationTest()
        },
        MenuItemDef(R.drawable.ic_bug_report, "Debug & Testing", palette.secondary) {
            actions.navigateToDebug()
        },
        MenuItemDef(R.drawable.ic_email, "Feedback & Bugs", palette.primary) {
            actions.navigateToFeedback()
        },
        MenuItemDef(R.drawable.ic_info, "About", palette.tertiary) {
            actions.navigateToAbout()
        }
    )
}

@Composable
private fun MenuTile(
    icon: Int,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = Radii.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = ImageVector.vectorResource(id = icon),
                    contentDescription = label,
                    tint = accent
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ModeExplanationDialog(
    isSimpleMode: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSimpleMode) "Switch to Simple Mode" else "Switch to Pro Mode",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isSimpleMode) {
                    Text(
                        text = "Simple mode is for recommended settings with fewer choices. Add your own API key to use cloud transcription or AI cleanup.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Streamlined settings\n• API key access for cloud models\n• Essential bubble controls (opacity, size, keyboard-aware)\n• Perfect for everyday use",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Pro mode is for advanced users who want to tinker, edit prompts, API keys, and try different model providers.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Access to all AI model providers\n• Custom prompts and API key management\n• Full bubble appearance customization\n• Advanced testing and debugging tools\n• Complete control over all features",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/* ---- Statistics utilities ---- */

@Composable
private fun StatisticsCard(context: Context, onResetClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    // Calculate statistics using persistent stats manager
    val statisticsManager = remember { StatisticsManager(context) }
    var statsSummary by remember { mutableStateOf(statisticsManager.getStatisticsSummary()) }
    val totalTranscriptionTime = statsSummary.totalTranscriptionTime
    val totalWordCount = statsSummary.totalWords
    val timeSaved = statsSummary.timeSaved
    val userWpm = statsSummary.userWpm
    val speedMultiplier = statsSummary.speedMultiplier

    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_transcription_stats),
                        contentDescription = "Transcription Stats",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "TRANSCRIPTION STATS",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }

                // Refresh/Reset icon in top right corner
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            colors.onSurfaceVariant.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onResetClick() }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh),
                        contentDescription = "Reset statistics",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatItem(
                        label = "Total Time",
                        value = formatDuration(totalTranscriptionTime),
                        iconRes = R.drawable.ic_total_time
                    )
                    StatItem(
                        label = "Total Words",
                        value = formatNumber(totalWordCount),
                        iconRes = R.drawable.ic_total_words
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatItem(
                        label = "Your WPM",
                        value = if (userWpm > 0) "${userWpm} wpm" else "0 wpm",
                        iconRes = R.drawable.ic_words_per_minute
                    )
                    StatItem(
                        label = if (speedMultiplier > 1) "${String.format("%.1f", speedMultiplier)}x faster" else "Time Saved",
                        value = if (speedMultiplier > 1) formatDuration(timeSaved) else formatDuration(timeSaved),
                        iconRes = R.drawable.ic_time_saved
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, iconRes: Int) {
    val colors = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.Icon(
            imageVector = ImageVector.vectorResource(id = iconRes),
            contentDescription = label,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )

        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}



private fun formatDuration(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h ${ (seconds % 3600) / 60}m"
    }
}

private fun formatNumber(number: Int): String {
    return when {
        number < 1000 -> number.toString()
        number < 1000000 -> "${(number / 1000.0).roundToInt()}K"
        else -> "${(number / 1000000.0).roundToInt()}M"
    }
}

/* ---- Warning utilities ---- */

private fun checkAccessibilityEnabled(context: Context): Boolean {
    return try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        enabled.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    } catch (_: Exception) {
        false
    }
}

private fun isBatteryOptimizationDisabled(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

private fun checkMicPermission(context: Context): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun canDrawOverlays(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

@Composable
private fun WarningCard(
    title: String,
    description: String,
    actionText: String,
    accent: Color,
    onAction: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = Radii.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .clickable { onAction() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = actionText,
                            color = accent,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Refresh icon on the far right
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            colors.onSurfaceVariant.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onRefresh() }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh),
                        contentDescription = "Refresh permissions",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
