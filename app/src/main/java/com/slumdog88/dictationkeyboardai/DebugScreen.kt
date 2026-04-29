package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import com.slumdog88.dictationkeyboardai.ui.components.BrutalCard

@Composable
fun DebugScreen(
    onFinish: () -> Unit,
    onStartBubbleService: () -> Unit,
    onClearPreferences: () -> Unit,
    onResetDisclosure: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestBatteryOptimizationDisable: () -> Unit,
    onRefreshStatus: () -> String
) {
    var statusText by remember { mutableStateOf("Loading status...") }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDialogContent by remember { mutableStateOf("") }

    // Test input field states - Number Row
    var normalText by remember { mutableStateOf("") }
    var numberText by remember { mutableStateOf("") }
    var phoneText by remember { mutableStateOf("") }
    var pinText by remember { mutableStateOf("") }

    // Test input field states - Email/URL Row
    var emailText by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }

    // Test input field states - IME Actions
    var searchText by remember { mutableStateOf("") }
    var sendText by remember { mutableStateOf("") }
    var goText by remember { mutableStateOf("") }
    var nextText by remember { mutableStateOf("") }
    var doneText by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    
    // Update status on composition and expose refresh function
    LaunchedEffect(Unit) {
        statusText = onRefreshStatus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.nb_base))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Title with Neo-Brutalist styling
        Text(
            text = "DEBUG & TESTING",
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = colorResource(id = R.color.nb_white),
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.5.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )
        
        // Status display card
        BrutalCard(
            accentColor = colorResource(id = R.color.nb_pink),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = statusText,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = colorResource(id = R.color.nb_white),
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                )
            }
        }
        
        // Action buttons
        DebugButton(
            text = "Test Bubble Service",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onStartBubbleService()
                Toast.makeText(context, "Bubble service started", Toast.LENGTH_SHORT).show()
            },
            accentColor = colorResource(id = R.color.nb_lime),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        DebugButton(
            text = "Clear All Preferences",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onClearPreferences()
                Toast.makeText(context, "All preferences cleared", Toast.LENGTH_SHORT).show()
                statusText = onRefreshStatus()
            },
            accentColor = colorResource(id = R.color.nb_cyan),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        DebugButton(
            text = "Reset Privacy Disclosure",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onResetDisclosure()
                Toast.makeText(context, "Privacy disclosure reset - will show again next time", Toast.LENGTH_LONG).show()
                statusText = onRefreshStatus()
            },
            accentColor = colorResource(id = R.color.nb_orange),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        DebugButton(
            text = "Check All Permissions",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                permissionDialogContent = buildPermissionStatus(context)
                showPermissionDialog = true
            },
            accentColor = colorResource(id = R.color.nb_pink),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Test Input Fields for Number Row Testing
        BrutalCard(
            accentColor = colorResource(id = R.color.nb_lime),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TEST INPUT FIELDS",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(id = R.color.nb_white),
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Tap each field to test number row behavior",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = colorResource(id = R.color.nb_gray_300),
                        fontFamily = FontFamily.SansSerif
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = colorResource(id = R.color.nb_lime),
                    unfocusedBorderColor = colorResource(id = R.color.nb_gray_500),
                    focusedLabelColor = colorResource(id = R.color.nb_lime),
                    unfocusedLabelColor = colorResource(id = R.color.nb_gray_300),
                    cursorColor = colorResource(id = R.color.nb_lime)
                )

                // Normal text field - should NOT show number row
                OutlinedTextField(
                    value = normalText,
                    onValueChange = { normalText = it },
                    label = { Text("Normal Text (no number row)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Number field - SHOULD show number row
                OutlinedTextField(
                    value = numberText,
                    onValueChange = { numberText = it },
                    label = { Text("Number (should show number row)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Phone field - SHOULD show number row
                OutlinedTextField(
                    value = phoneText,
                    onValueChange = { phoneText = it },
                    label = { Text("Phone (should show number row)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // PIN field - SHOULD show number row
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { pinText = it },
                    label = { Text("PIN (should show number row)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Email/URL Row Testing
                Text(
                    text = "EMAIL/URL ROW",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(id = R.color.nb_white),
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Should show .com, @, /, :, - row",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = colorResource(id = R.color.nb_gray_300),
                        fontFamily = FontFamily.SansSerif
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Email field - SHOULD show email/URL row
                OutlinedTextField(
                    value = emailText,
                    onValueChange = { emailText = it },
                    label = { Text("Email (should show email row)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // URL field - SHOULD show email/URL row
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("URL (should show email row)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section: IME Actions Testing
                Text(
                    text = "RETURN KEY ACTIONS",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(id = R.color.nb_white),
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Test different return key icons",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = colorResource(id = R.color.nb_gray_300),
                        fontFamily = FontFamily.SansSerif
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Search action
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search (🔍 icon)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Send action
                OutlinedTextField(
                    value = sendText,
                    onValueChange = { sendText = it },
                    label = { Text("Send (➤ icon)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Go action
                OutlinedTextField(
                    value = goText,
                    onValueChange = { goText = it },
                    label = { Text("Go (→ icon)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Next action
                OutlinedTextField(
                    value = nextText,
                    onValueChange = { nextText = it },
                    label = { Text("Next (→ icon, no bg)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Done action
                OutlinedTextField(
                    value = doneText,
                    onValueChange = { doneText = it },
                    label = { Text("Done (✓ icon)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        DebugButton(
            text = "Back",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onFinish()
            },
            accentColor = colorResource(id = R.color.nb_gray_500),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Instructions card
        BrutalCard(
            accentColor = colorResource(id = R.color.nb_gray_500),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "INSTRUCTIONS:\n• Use these tools to debug app functionality\n• Check permissions to ensure proper operation\n• Test bubble service and accessibility features\n• Clear preferences to reset app state",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorResource(id = R.color.nb_gray_300),
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 16.sp
                )
            )
        }
    }
    
    // Permission Checker Dialog
    if (showPermissionDialog) {
        PermissionCheckerDialog(
            content = permissionDialogContent,
            context = context,
            onDismiss = { showPermissionDialog = false },
            onRequestAudioPermission = {
                showPermissionDialog = false
                onRequestAudioPermission()
            },
            onRequestOverlayPermission = {
                showPermissionDialog = false
                onRequestOverlayPermission()
            },
            onOpenAccessibilitySettings = {
                showPermissionDialog = false
                onOpenAccessibilitySettings()
            },
            onRequestBatteryOptimizationDisable = {
                showPermissionDialog = false
                onRequestBatteryOptimizationDisable()
            }
        )
    }
}

@Composable
private fun DebugButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}

@Composable
private fun PermissionCheckerDialog(
    content: String,
    context: Context,
    onDismiss: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestBatteryOptimizationDisable: () -> Unit
) {
    val audioGranted = hasAudioPermission(context)
    val overlayGranted = canDrawOverlays(context)
    val accessibilityEnabled = isAccessibilityServiceEnabled(context)
    val batteryOptDisabled = isBatteryOptimizationDisabled(context)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Permission Checker",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        },
        text = {
            Text(
                text = content,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "Close",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = colorResource(id = R.color.nb_cyan)
                    )
                )
            }
        },
        dismissButton = {
            when {
                !audioGranted -> {
                    TextButton(
                        onClick = onRequestAudioPermission
                    ) {
                        Text(
                            text = "Fix Audio Permission",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = colorResource(id = R.color.nb_pink)
                            )
                        )
                    }
                }
                !overlayGranted -> {
                    TextButton(
                        onClick = onRequestOverlayPermission
                    ) {
                        Text(
                            text = "Fix Overlay Permission",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = colorResource(id = R.color.nb_pink)
                            )
                        )
                    }
                }
                !accessibilityEnabled -> {
                    TextButton(
                        onClick = onOpenAccessibilitySettings
                    ) {
                        Text(
                            text = "Enable Accessibility",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = colorResource(id = R.color.nb_pink)
                            )
                        )
                    }
                }
                !batteryOptDisabled -> {
                    TextButton(
                        onClick = onRequestBatteryOptimizationDisable
                    ) {
                        Text(
                            text = "Fix Battery Optimization",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = colorResource(id = R.color.nb_pink)
                            )
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

// Business logic functions extracted from DebugActivity
fun updateDebugStatus(context: Context, secureApiKeyManager: SecureApiKeyManager): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    return buildString {
        appendLine("=== SYSTEM STATUS ===")
        appendLine()
        
        // Add detailed API key debug information
        appendLine(secureApiKeyManager.getDebugStatus())
        appendLine()
        appendLine("Settings:")
        appendLine("- Transcription Service: ${prefs.getString("transcription_service", "Groq Whisper v3 Turbo")}")
        appendLine("- AI Model: ${prefs.getString("ai_model", "mistral-saba-24b")}")
        appendLine("- Post-processing: ${prefs.getBoolean("enable_postprocess", false)}")
        appendLine()
        appendLine("Accessibility Service:")
        appendLine("- Status: ${if (isAccessibilityServiceEnabled(context)) "ENABLED" else "DISABLED"}")
        appendLine("- Privacy Disclosure: ${if (prefs.getBoolean("accessibility_disclosure_shown", false)) "SHOWN" else "NOT SHOWN"}")
        appendLine()
        appendLine("Permissions:")
        appendLine("- Audio Recording: ${if (hasAudioPermission(context)) "GRANTED" else "NOT GRANTED"}")
        appendLine("- Overlay: ${if (canDrawOverlays(context)) "GRANTED" else "NOT GRANTED"}")
        appendLine()
        appendLine("Battery Optimization:")
        appendLine("- Status: ${if (isBatteryOptimizationDisabled(context)) "DISABLED (GOOD)" else "ENABLED (BAD)"}")
        if (!isBatteryOptimizationDisabled(context)) {
            appendLine("  ⚠️ Battery optimization must be disabled!")
            appendLine("  Accessibility service will be killed otherwise.")
        }
    }
}

// Helper functions that check for permissions and service status
// These functions are lightweight and provide consistent permission checking
// across the debug interface. They mirror similar functions in MainActivity
// but are defined locally to avoid complex dependencies.

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    
    return enabledServices.any { service ->
        service.resolveInfo.serviceInfo.packageName == context.packageName &&
        service.resolveInfo.serviceInfo.name == "com.slumdog88.dictationkeyboardai.DictationAccessibilityService"
    }
}

private fun hasAudioPermission(context: Context): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun canDrawOverlays(context: Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        android.provider.Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun isBatteryOptimizationDisabled(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true // Not applicable on older versions
    }
}

fun buildPermissionStatus(context: Context): String {
    return buildString {
        appendLine("🔍 PERMISSION CHECKER")
        appendLine("=" .repeat(30))
        appendLine()
        
        appendLine("✅ REQUIRED PERMISSIONS:")
        appendLine()
        
        // Audio Recording Permission
        val audioGranted = hasAudioPermission(context)
        appendLine("🎙️ Audio Recording: ${if (audioGranted) "✅ GRANTED" else "❌ NOT GRANTED"}")
        if (!audioGranted) {
            appendLine("   Required for voice dictation")
            appendLine("   Tap 'Fix Audio Permission' to enable")
        }
        appendLine()
        
        // Overlay Permission
        val overlayGranted = canDrawOverlays(context)
        appendLine("📱 Display Over Apps: ${if (overlayGranted) "✅ GRANTED" else "❌ NOT GRANTED"}")
        if (!overlayGranted) {
            appendLine("   Required for floating dictation button")
            appendLine("   Tap 'Fix Overlay Permission' to enable")
        }
        appendLine()
        
        // Accessibility Service
        val accessibilityEnabled = isAccessibilityServiceEnabled(context)
        appendLine("♿ Accessibility Service: ${if (accessibilityEnabled) "✅ ENABLED" else "❌ DISABLED"}")
        if (!accessibilityEnabled) {
            appendLine("   Required to insert text and read screen content")
            appendLine("   Tap 'Enable Accessibility' to activate")
        }
        appendLine()
        
        // Battery Optimization
        val batteryOptDisabled = isBatteryOptimizationDisabled(context)
        appendLine("🔋 Battery Optimization: ${if (batteryOptDisabled) "✅ DISABLED" else "⚠️ ENABLED"}")
        if (!batteryOptDisabled) {
            appendLine("   CRITICAL: Must be disabled!")
            appendLine("   Android will kill accessibility service otherwise")
            appendLine("   Tap 'Fix Battery Optimization' to disable")
        }
        appendLine()
        
        // Overall Status
        val allGranted = audioGranted && overlayGranted && accessibilityEnabled && batteryOptDisabled
        appendLine("📊 OVERALL STATUS:")
        appendLine(if (allGranted) "✅ ALL REQUIREMENTS MET" else "❌ SOME REQUIREMENTS NOT MET")
    }
}
