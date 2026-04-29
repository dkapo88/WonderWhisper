package com.slumdog88.dictationkeyboardai

import android.content.Intent



import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import com.slumdog88.dictationkeyboardai.ui.screens.AboutScreenDM

private const val LOG_TAG = "AboutActivity"

class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d(LOG_TAG, "AboutActivity onCreate")
        setContent {
            com.slumdog88.dictationkeyboardai.ui.theme.AppTheme {
                com.slumdog88.dictationkeyboardai.ui.screens.AboutScreenDM()
            }
        }
    }
}

@Composable
private fun BrutalTheme(content: @Composable () -> Unit) {
    val pink = colorResource(id = R.color.nb_pink)
    val cyan = colorResource(id = R.color.nb_cyan)
    val base = colorResource(id = R.color.nb_base)
    val onBase = colorResource(id = R.color.nb_white)

    val scheme = darkColorScheme(
        primary = pink,
        onPrimary = onBase,
        secondary = cyan,
        onSecondary = onBase,
        background = base,
        surface = base,
        onBackground = onBase,
        onSurface = onBase
    )

    // Prefer Google Fonts provider at runtime; fall back to system Monospace if GMS is missing (previews, AOSP emulator)
    val context = LocalContext.current
    val hasGms = try {
        context.packageManager.getPackageInfo("com.google.android.gms", 0)
        true
    } catch (_: Exception) { false }

    val isPreview = LocalInspectionMode.current
    val heading: FontFamily
    val body: FontFamily
    if (!FontDebugConfig.FORCE_LOCAL_FONTS && !isPreview && hasGms) {
        android.util.Log.d(LOG_TAG, "BrutalTheme: Using Google Fonts provider (isPreview=$isPreview, hasGms=$hasGms)")
        val provider = androidx.compose.ui.text.googlefonts.GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs
        )
        val spaceMono = androidx.compose.ui.text.googlefonts.GoogleFont("Space Mono")
        val robotoMono = androidx.compose.ui.text.googlefonts.GoogleFont("Roboto Mono")
        heading = FontFamily(
            androidx.compose.ui.text.googlefonts.Font(spaceMono, provider, androidx.compose.ui.text.font.FontWeight.W700, FontStyle.Italic)
        )
        body = FontFamily(
            androidx.compose.ui.text.googlefonts.Font(robotoMono, provider, androidx.compose.ui.text.font.FontWeight.W500)
        )
    } else {
        // Use bundled fonts for previews and non-GMS devices
        android.util.Log.d(LOG_TAG, "BrutalTheme: Using LOCAL bundled fonts (isPreview=$isPreview, hasGms=$hasGms, FORCE_LOCAL_FONTS=${FontDebugConfig.FORCE_LOCAL_FONTS})")
        heading = FontFamily.Monospace
        body = FontFamily.Monospace
    }

    val typography = MaterialTheme.typography.copy(
        headlineLarge = TextStyle(fontFamily = heading, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 32.sp, letterSpacing = 0.2.sp),
        headlineMedium = TextStyle(fontFamily = heading, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 28.sp, letterSpacing = 0.2.sp),
        headlineSmall = TextStyle(fontFamily = heading, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 24.sp, letterSpacing = 0.2.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.0.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.0.sp)
    )

    MaterialTheme(colorScheme = scheme, typography = typography) {
        content()
    }
}


@Composable
private fun NeoBrutalAboutScreen() {
    val context = LocalContext.current

    // Colors from the existing neo‑brutalist palette
    val base = colorResource(id = R.color.nb_base)
    val surface = colorResource(id = R.color.nb_surface)
    val white = colorResource(id = R.color.nb_white)
    val borderStrong = colorResource(id = R.color.nb_border_strong)
    val pink = colorResource(id = R.color.nb_pink)
    val gray300 = colorResource(id = R.color.nb_gray_300)

    // Version string (computed once)
    var versionText by remember { mutableStateOf("Version information unavailable") }
    remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            versionText = "Version $versionName (Build $versionCode)"
        } catch (_: PackageManager.NameNotFoundException) {
            versionText = "Version information unavailable"
        }
        0
    }

    Surface(color = base) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "ABOUT WONDER WHISPER",
                color = white,
                style = MaterialTheme.typography.headlineMedium,
                letterSpacing = 0.6.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            )

            // Reddit Button with offset white shadow look
            BrutalOffsetButton(
                text = "🔗 VISIT OUR REDDIT FOR UPDATES & FEEDBACK",
                containerColor = pink,
                onClick = {
                    HapticUtils.performHapticFeedback(context)
                    val redditUrl = "https://www.reddit.com/r/WonderWhisper/"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redditUrl))
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(redditUrl))
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(browserIntent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Description
            Text(
                text = "Hey, thank you for downloading and installing WonderWhisper. In short, this project was born because I love AI dictation apps and been using them on my Mac for quite some time with the likes of SuperWhisper, Voicink, WisprFlow, and Talktastic. However, I found that on Android, nothing quite existed that fulfilled the same need as these apps on desktop. Whilst there was some that existed, I didn't like the way they were implemented. It felt outdated and unconfigurable and clunky.\n\nSo I set out to create a version of what we have on desktop but on Android with a very simple and easy way to action voice dictation without the need to use a dedicated keyboard. Wonder Whisper was born.\n\nIt's still very early and at the moment it's a hobby development. Please provide any feedback that you have. I am very fast to try out new ideas and implement. As a new developer, I'm just having fun.\n\nThe best way to keep up to date on the changes being made to the app or even provide feedback is please use the newly created Reddit community where I'd be happy to interact with you directly.\n\nCheers fam and happy dictating!",
                color = gray300,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Version card with strong border and offset white shadow effect
            BrutalOffsetCard(
                backgroundColor = surface,
                borderColor = borderStrong
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "VERSION INFORMATION",
                        color = white,
                        style = MaterialTheme.typography.headlineSmall,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = versionText,
                        color = gray300,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BrutalOffsetCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    borderColor: Color,
    cornerRadius: Int = 4,
    offset: Int = 6
    , content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(modifier = modifier.fillMaxWidth()) {
        // Offset white shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(offset.dp, offset.dp)
                .clip(shape)
                .background(Color.White)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 4.dp, color = borderColor, shape = shape)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun BrutalOffsetButton(
    text: String,
    containerColor: Color,
    onClick: () -> Unit,
    cornerRadius: Int = 4,
    offset: Int = 6
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(modifier = Modifier.fillMaxWidth()) {
        // Offset white shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(offset.dp, offset.dp)
                .clip(shape)
                .background(Color.White)
        )
        Button(
            onClick = onClick,
            shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .border(width = 4.dp, color = colorResource(id = R.color.nb_border_strong), shape = shape)
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "About – Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF0B0B0F)
@Preview(name = "About – Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun Preview_AboutScreen() {
    BrutalTheme { NeoBrutalAboutScreen() }
}

@Preview(name = "Offset Card – Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun Preview_OffsetCard() {
    BrutalTheme {
        BrutalOffsetCard(
            backgroundColor = colorResource(id = R.color.nb_surface),
            borderColor = colorResource(id = R.color.nb_border_strong)
        ) {
            Text(
                text = "VERSION INFORMATION\nVersion 1.0 (Build 1)",
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(id = R.color.nb_white)
            )
        }
    }
}

@Preview(name = "Offset Button – Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun Preview_OffsetButton() {
    BrutalTheme {
        BrutalOffsetButton(
            text = "🔗 VISIT OUR REDDIT FOR UPDATES & FEEDBACK",
            containerColor = colorResource(id = R.color.nb_pink),
            onClick = {}
        )
    }
}
