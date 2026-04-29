package com.slumdog88.dictationkeyboardai

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.*

class FeedbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            com.slumdog88.dictationkeyboardai.ui.theme.AppTheme {
                androidx.compose.material3.Scaffold(
                    topBar = {
                        com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM(
                            title = "Feedback & Bugs",
                            onBack = { finish() }
                        )
                    }
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.padding(innerPadding)
                    ) {
                        com.slumdog88.dictationkeyboardai.ui.screens.FeedbackScreenDM(
                            onSendFeedback = { type, priority, category, subject, description, steps ->
                                sendFeedback(type, priority, category, subject, description, steps)
                            },
                            onBackPressed = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun sendFeedback(feedbackType: String, priority: String, category: String, subject: String, description: String, steps: String) {

        if (subject.isBlank()) {
            Toast.makeText(this, "Please enter a subject", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (description.isBlank()) {
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show()
            return
        }

        val issueUri = buildGitHubIssueUri(
            feedbackType = feedbackType,
            priority = priority,
            category = category,
            subject = subject.trim(),
            description = description.trim(),
            steps = steps.trim()
        )
        val issueIntent = Intent(Intent.ACTION_VIEW, issueUri)

        try {
            startActivity(issueIntent)
            Toast.makeText(this, "Opening GitHub issue...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("FeedbackActivity", "Error opening GitHub issue", e)
            Toast.makeText(this, "No browser found to open GitHub", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildGitHubIssueUri(
        feedbackType: String,
        priority: String,
        category: String,
        subject: String,
        description: String,
        steps: String
    ): Uri {
        val isBug = feedbackType == "Bug Report" || feedbackType == "Performance Issue"
        val isFeature = feedbackType == "Feature Request"
        val titlePrefix = when {
            isBug -> "[Bug]"
            isFeature -> "[Feature]"
            else -> "[Feedback]"
        }

        val builder = Uri.Builder()
            .scheme("https")
            .authority("github.com")
            .appendPath("dkapo88")
            .appendPath("WonderWhisper")
            .appendPath("issues")
            .appendPath("new")
            .appendQueryParameter("title", "$titlePrefix: $subject")
            .appendQueryParameter(
                "body",
                buildGitHubIssueBody(
                    feedbackType = feedbackType,
                    priority = priority,
                    category = category,
                    description = description,
                    steps = steps,
                    isBug = isBug,
                    isFeature = isFeature
                )
            )

        when {
            isBug -> builder.appendQueryParameter("template", "bug_report.md")
            isFeature -> builder.appendQueryParameter("template", "feature_request.md")
        }

        return builder.build()
    }

    private fun buildGitHubIssueBody(
        feedbackType: String,
        priority: String,
        category: String,
        description: String,
        steps: String,
        isBug: Boolean,
        isFeature: Boolean
    ): String {
        return when {
            isBug -> buildBugIssueBody(feedbackType, priority, category, description, steps)
            isFeature -> buildFeatureIssueBody(priority, category, description)
            else -> buildGeneralIssueBody(feedbackType, priority, category, description)
        }
    }

    private fun buildBugIssueBody(
        feedbackType: String,
        priority: String,
        category: String,
        description: String,
        steps: String
    ): String = buildString {
        append("## What happened?\n\n")
        append(description)
        append("\n\n")
        append("## Expected behavior\n\n")
        append("_Please describe what you expected to happen._\n\n")
        append("## Steps to reproduce\n\n")
        if (steps.isBlank()) {
            append("_No steps provided._\n\n")
        } else {
            append(steps)
            append("\n\n")
        }
        append("## Device and app details\n\n")
        appendDeviceDetails(feedbackType, priority, category)
        append("\n## Logs\n\n")
        append("Please attach screenshots or sanitized logs manually if they help. Remove API keys, transcripts, and other private content before posting.\n")
    }

    private fun buildFeatureIssueBody(
        priority: String,
        category: String,
        description: String
    ): String = buildString {
        append("## Workflow\n\n")
        append(description)
        append("\n\n")
        append("## Current limitation\n\n")
        append("_What does WonderWhisper not handle well today?_\n\n")
        append("## Proposed behavior\n\n")
        append("_What should happen instead?_\n\n")
        append("## Alternatives considered\n\n")
        append("_Any workarounds or related apps/features?_\n\n")
        append("## App details\n\n")
        appendDeviceDetails("Feature Request", priority, category)
    }

    private fun buildGeneralIssueBody(
        feedbackType: String,
        priority: String,
        category: String,
        description: String
    ): String = buildString {
        append("## Feedback\n\n")
        append(description)
        append("\n\n")
        append("## App details\n\n")
        appendDeviceDetails(feedbackType, priority, category)
    }

    private fun StringBuilder.appendDeviceDetails(feedbackType: String, priority: String, category: String) {
        append("- Feedback type: $feedbackType\n")
        append("- Priority: $priority\n")
        append("- Category: $category\n")
        append("- Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        append("- Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        append("- App version: ${getAppVersion()}\n")
        append("- Available memory: ${getAvailableMemory()}\n")
        append("- Free storage: ${getFreeStorage()}\n")
        append("- Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "${packageInfo.versionName} (${packageInfo.longVersionCode})"
            } else {
                @Suppress("DEPRECATION")
                "${packageInfo.versionName} (${packageInfo.versionCode})"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }
    
    private fun getAvailableMemory(): String {
        val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return "${memoryInfo.availMem / (1024 * 1024)}MB"
    }
    
    private fun getFreeStorage(): String {
        val storageDir = getExternalFilesDir(null)
        return if (storageDir != null) {
            "${storageDir.freeSpace / (1024 * 1024)}MB"
        } else {
            "Unknown"
        }
    }
    
    private fun performHapticFeedback() {
        HapticUtils.performHapticFeedback(this)
    }
}

@Composable
fun FeedbackScreen(
    onSendFeedback: (String, String, String, String, String, String) -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val limeColor = colorResource(id = R.color.nb_lime)
    
    // Form state
    var feedbackType by remember { mutableStateOf("Bug Report") }
    var priority by remember { mutableStateOf("Medium") }
    var category by remember { mutableStateOf("UI/UX") }
    var subject by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var steps by remember { mutableStateOf(TextFieldValue("")) }
    
    // Show steps section for Bug Report and Performance Issue
    val showSteps = feedbackType == "Bug Report" || feedbackType == "Performance Issue"
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.nb_base))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header
            Text(
                text = "FEEDBACK & BUG REPORTS",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = colorResource(id = R.color.nb_white),
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.3.sp,
                    lineHeight = 20.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Feedback Type Card
            BrutalistCard(accentColor = limeColor) {
                Column {
                    Text(
                        text = "Feedback Type",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(id = R.color.nb_white),
                            fontFamily = FontFamily.SansSerif
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    BrutalistDropdown(
                        selectedValue = feedbackType,
                        options = listOf("Bug Report", "Feature Request", "General Feedback", "Performance Issue"),
                        onValueChange = { feedbackType = it }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Priority Card
            BrutalistCard(accentColor = limeColor) {
                Column {
                    Text(
                        text = "Priority",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(id = R.color.nb_white),
                            fontFamily = FontFamily.SansSerif
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    BrutalistDropdown(
                        selectedValue = priority,
                        options = listOf("Low", "Medium", "High", "Critical"),
                        onValueChange = { priority = it }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Category Card
            BrutalistCard(accentColor = limeColor) {
                Column {
                    Text(
                        text = "Category",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(id = R.color.nb_white),
                            fontFamily = FontFamily.SansSerif
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    BrutalistDropdown(
                        selectedValue = category,
                        options = listOf("UI/UX", "Audio Processing", "AI Integration", "Performance", "Crash/Stability", "Settings", "Other"),
                        onValueChange = { category = it }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Subject Card
            BrutalistCard(accentColor = limeColor) {
                Column {
                    Text(
                        text = "Subject *",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(id = R.color.nb_white),
                            fontFamily = FontFamily.SansSerif
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    BrutalistTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        placeholder = "Brief description of the issue or request",
                        maxLines = 2
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Description Card
            BrutalistCard(accentColor = limeColor) {
                Column {
                    Text(
                        text = "Description *",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(id = R.color.nb_white),
                            fontFamily = FontFamily.SansSerif
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    BrutalistTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = "Detailed description of the issue, feature request, or feedback. Please be as specific as possible.",
                        minHeight = 120.dp,
                        maxLines = Int.MAX_VALUE
                    )
                }
            }
            
            // Steps to Reproduce (conditional)
            if (showSteps) {
                Spacer(modifier = Modifier.height(16.dp))
                
                BrutalistCard(accentColor = limeColor) {
                    Column {
                        Text(
                            text = "Steps to Reproduce",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorResource(id = R.color.nb_white),
                                fontFamily = FontFamily.SansSerif
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        BrutalistTextField(
                            value = steps,
                            onValueChange = { steps = it },
                            placeholder = "1. First step\n2. Second step\n3. Third step...",
                            minHeight = 100.dp,
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // GitHub issue card
            BrutalistCard(accentColor = limeColor) {
                Column {
                    Text(
                        text = "GitHub Issue",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(id = R.color.nb_white),
                            fontFamily = FontFamily.SansSerif
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "This will open a pre-filled issue on GitHub. Attach screenshots or sanitized logs on GitHub only if you want to share them publicly.",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Send Button
            BrutalistActionButton(
                text = "OPEN GITHUB ISSUE",
                onClick = {
                    HapticUtils.performHapticFeedback(context)
                    onSendFeedback(feedbackType, priority, category, subject.text, description.text, steps.text)
                },
                accentColor = limeColor,
                modifier = Modifier.height(56.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Back Button
            BrutalistSecondaryButton(
                text = "BACK",
                onClick = {
                    HapticUtils.performHapticFeedback(context)
                    onBackPressed()
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun BrutalistDropdown(
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier.fillMaxWidth()) {
        // Dropdown button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF1F1F1F), // charcoal equivalent
                    RoundedCornerShape(8.dp)
                )
                .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Text(
                text = selectedValue,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = colorResource(id = R.color.nb_white),
                    fontFamily = FontFamily.SansSerif
                )
            )
        }
        
        // Dropdown menu
        if (expanded) {
            Dialog(
                onDismissRequest = { expanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    BrutalistCard(accentColor = colorResource(id = R.color.nb_lime)) {
                        Column {
                            options.forEach { option ->
                                Text(
                                    text = option,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = colorResource(id = R.color.nb_white),
                                        fontFamily = FontFamily.SansSerif
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onValueChange(option)
                                            expanded = false
                                        }
                                        .padding(12.dp)
                                )
                                
                                if (option != options.last()) {
                                    Divider(color = Color.Black, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrutalistTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    maxLines: Int = 1
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(
                Color(0xFF1F1F1F), // charcoal equivalent
                RoundedCornerShape(8.dp)
            )
            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = Color(0xFF888888),
                    fontFamily = FontFamily.SansSerif
                )
            )
        }
        
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = colorResource(id = R.color.nb_white),
                fontFamily = FontFamily.SansSerif
            ),
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BrutalistSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF666666)
    
    Box(
        modifier = modifier
    ) {
        // Multi-layer shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color.Black, RoundedCornerShape(12.dp))
        )
        
        // Main button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color.White.copy(alpha = 0.2f),
                    spotColor = accentColor
                )
                .background(Color(0xFF1F1F1F), RoundedCornerShape(12.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(id = R.color.nb_white),
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.3.sp
                )
            )
        }
    }
}

@Composable
fun FeedbackTheme(content: @Composable () -> Unit) {
    val pink = Color(0xFFFF006E)
    val cyan = Color(0xFF00F5FF)
    val base = Color(0xFF1A1A1A)
    val onBase = Color(0xFFFFFFFF)
    val charcoal = Color(0xFF2B2B2B)
    val lime = Color(0xFF8AC926)
    val orange = Color(0xFFFF7F00)

    val scheme = darkColorScheme(
        primary = lime, // Use lime as primary for feedback theme
        onPrimary = Color.Black,
        secondary = cyan,
        onSecondary = onBase,
        background = base,
        surface = charcoal,
        onBackground = onBase,
        onSurface = onBase
    )

    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
