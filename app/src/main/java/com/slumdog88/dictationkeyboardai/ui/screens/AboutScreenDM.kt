package com.slumdog88.dictationkeyboardai.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreenDM() {
    val context = LocalContext.current

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

    androidx.compose.material3.Scaffold(
        topBar = {
            com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM(
                title = "About",
                onBack = { (context as? android.app.Activity)?.finish() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Text(
                text = "About Wonder Whisper",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            // Restore long-form description about the app’s origin and mission
            TokenCard {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Hey, thank you for downloading and installing WonderWhisper. In short, this project was born because I love AI dictation apps and been using them on my Mac for quite some time with the likes of SuperWhisper, Voicink, WisprFlow, and Talktastic. However, I found that on Android, nothing quite existed that fulfilled the same need as these apps on desktop. Whilst there was some that existed, I didn't like the way they were implemented. It felt outdated and unconfigurable and clunky.\n\nSo I set out to create a version of what we have on desktop but on Android with a very simple and easy way to action voice dictation without the need to use a dedicated keyboard. Wonder Whisper was born.\n\nIt's still very early and at the moment it's a hobby development. Please provide any feedback that you have. I am very fast to try out new ideas and implement. As a new developer, I'm just having fun.\n\nThe best way to keep up to date on the changes being made to the app or even provide feedback is please use the newly created Reddit community where I'd be happy to interact with you directly.\n\nCheers fam and happy dictating!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            TokenCard {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Project",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Wonder Whisper brings fast, configurable AI dictation to Android with a lightweight bubble overlay and simple actions. It aims to deliver a desktop‑class dictation experience on mobile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val redditUrl = "https://www.reddit.com/r/WonderWhisper/"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redditUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Open Reddit community")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TokenCard {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Version Information",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = versionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TokenCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = com.slumdog88.dictationkeyboardai.ui.theme.Radii.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}