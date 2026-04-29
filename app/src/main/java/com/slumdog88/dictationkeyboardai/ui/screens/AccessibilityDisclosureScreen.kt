package com.slumdog88.dictationkeyboardai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.HapticUtils

@Composable
fun AccessibilityDisclosureScreen(
    onBack: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onUnderstandAndAgree: () -> Unit
) {
    val context = LocalContext.current
    
    // Neo-Brutalist orange theme for privacy/warning
    val orangeAccent = Color(0xFFFF7F00)
    val charcoalBackground = Color(0xFF2B2B2B)
    val baseBackground = Color(0xFF1A1A1A)
    val whiteText = Color(0xFFFFFFFF)
    val lightGrayText = Color(0xFFCCCCCC)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(baseBackground)
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Main Header
        Text(
            text = "🔒 ACCESSIBILITY PERMISSION DISCLOSURE",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = orangeAccent,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.4.sp,
                lineHeight = 24.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )
        
        // Important Warning Section
        DisclosureSection(
            title = "IMPORTANT WARNING",
            content = "WonderWhisper requires Accessibility Service permission to function properly.",
            accentColor = orangeAccent,
            backgroundColor = charcoalBackground,
            textColor = whiteText,
            isWarning = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Why We Need It Section
        DisclosureSection(
            title = "WHY WE NEED ACCESSIBILITY PERMISSION:",
            content = """WonderWhisper uses the AccessibilityServices API EXCLUSIVELY to:

• INSERT DICTATED TEXT into any app (Messages, Email, Notes, etc.)
• READ SELECTED TEXT to provide context for AI commands
• DETECT ACTIVE TEXT FIELDS to show the dictation bubble

This permission allows WonderWhisper to work as a system-wide dictation tool across all your apps.""",
            accentColor = orangeAccent,
            backgroundColor = charcoalBackground,
            textColor = whiteText
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // What We Access Section
        DisclosureSection(
            title = "WHAT WE ACCESS:",
            content = """✓ Text fields and input areas (to insert dictated text)
✓ Selected text content (for AI command context)
✓ Current app information (for app-specific features)

✗ We DO NOT access passwords, payment info, or sensitive data
✗ We DO NOT monitor your browsing or app usage
✗ We DO NOT collect data for advertising""",
            accentColor = orangeAccent,
            backgroundColor = charcoalBackground,
            textColor = whiteText
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Data Usage Section
        DisclosureSection(
            title = "HOW YOUR DATA IS USED:",
            content = """• VOICE RECORDINGS: Temporarily processed for transcription, then immediately deleted
• SELECTED TEXT: Used only for AI command context, not stored permanently
• APP CONTEXT: Used to improve dictation accuracy, not tracked or stored
• TRANSCRIPTION LOGS: Stored locally on your device only, you control deletion

NO DATA IS SENT TO OUR SERVERS. All processing uses your own API keys with OpenAI/AssemblyAI/etc.""",
            accentColor = orangeAccent,
            backgroundColor = charcoalBackground,
            textColor = whiteText
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Security Section
        DisclosureSection(
            title = "SECURITY & PRIVACY:",
            content = """• All data processing is LOCAL or through YOUR API keys
• No data is sent to WonderWhisper servers
• You can disable the service anytime in Android Settings
• You can view/delete all logs in the app
• Open source code available for review""",
            accentColor = orangeAccent,
            backgroundColor = charcoalBackground,
            textColor = whiteText
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Compliance Section
        DisclosureSection(
            title = "COMPLIANCE:",
            content = "This disclosure complies with Google Play's AccessibilityServices API policy. WonderWhisper uses accessibility permissions solely for assistive purposes - enabling voice dictation for users across all apps.",
            accentColor = orangeAccent,
            backgroundColor = charcoalBackground,
            textColor = whiteText
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Consent Section
        DisclosureSection(
            title = "BY CONTINUING, YOU CONSENT TO:",
            content = """• WonderWhisper accessing text fields to insert dictated text
• Reading selected text for AI command context
• Processing your voice recordings through AI services
• Storing transcription logs locally on your device""",
            accentColor = orangeAccent,
            backgroundColor = charcoalBackground,
            textColor = whiteText,
            isWarning = true
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Action Buttons
        DisclosureActionButton(
            text = "VIEW FULL PRIVACY POLICY",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onPrivacyPolicy()
            },
            accentColor = orangeAccent,
            buttonType = DisclosureButtonType.SECONDARY
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        DisclosureActionButton(
            text = "I UNDERSTAND & AGREE - ENABLE ACCESSIBILITY",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onUnderstandAndAgree()
            },
            accentColor = orangeAccent,
            buttonType = DisclosureButtonType.PRIMARY
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        DisclosureActionButton(
            text = "BACK TO MAIN MENU",
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onBack()
            },
            accentColor = orangeAccent,
            buttonType = DisclosureButtonType.TERTIARY
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DisclosureSection(
    title: String,
    content: String,
    accentColor: Color,
    backgroundColor: Color,
    textColor: Color,
    isWarning: Boolean = false
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Multi-layer shadow system
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 8.dp, y = 8.dp)
                .background(
                    if (isWarning) accentColor.copy(alpha = 0.8f) else accentColor.copy(alpha = 0.6f),
                    RoundedCornerShape(16.dp)
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        
        // Main card content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = accentColor
                )
                .background(backgroundColor, RoundedCornerShape(16.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            // Section Title
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.3.sp,
                    lineHeight = 18.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Section Content
            Text(
                text = content,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = textColor,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

enum class DisclosureButtonType {
    PRIMARY,    // Orange filled
    SECONDARY,  // Orange border
    TERTIARY    // Gray
}

@Composable
private fun DisclosureActionButton(
    text: String,
    onClick: () -> Unit,
    accentColor: Color,
    buttonType: DisclosureButtonType
) {
    val backgroundColor = when (buttonType) {
        DisclosureButtonType.PRIMARY -> accentColor
        DisclosureButtonType.SECONDARY -> Color(0xFF1F1F1F)
        DisclosureButtonType.TERTIARY -> Color(0xFF2B2B2B)
    }
    
    val textColor = when (buttonType) {
        DisclosureButtonType.PRIMARY -> Color.Black
        DisclosureButtonType.SECONDARY -> accentColor
        DisclosureButtonType.TERTIARY -> Color.White
    }
    
    val borderColor = when (buttonType) {
        DisclosureButtonType.PRIMARY -> Color.Black
        DisclosureButtonType.SECONDARY -> accentColor
        DisclosureButtonType.TERTIARY -> Color.Black
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // Multi-layer shadow system
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(
                    when (buttonType) {
                        DisclosureButtonType.PRIMARY -> accentColor.copy(alpha = 0.8f)
                        DisclosureButtonType.SECONDARY -> accentColor.copy(alpha = 0.4f)
                        DisclosureButtonType.TERTIARY -> Color.Gray.copy(alpha = 0.4f)
                    },
                    RoundedCornerShape(16.dp)
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
        )
        
        // Main button
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = backgroundColor
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = accentColor
                )
                .border(3.dp, borderColor, RoundedCornerShape(16.dp))
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = if (buttonType == DisclosureButtonType.PRIMARY) 14.sp else 13.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.3.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
