package com.slumdog88.dictationkeyboardai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.LogEntry

@Composable
fun LogEntryCard(
    entry: LogEntry,
    currentlyPlayingAudio: String?,
    onAudioClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onReprocessClick: (LogEntry) -> Unit,
    onBodyClick: (LogEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = if (entry.isReprocessed) colorResource(id = R.color.nb_orange) else colorResource(id = R.color.nb_cyan)
    
    BrutalCard(accentColor = accentColor) {
        val isPlaying = currentlyPlayingAudio == entry.audioFileName
        
        Column {
            // Timestamp with inline play button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (entry.isReprocessed) "⟲ ${entry.timestamp} (REPROCESSED)" else entry.timestamp,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        letterSpacing = 0.2.sp
                    ),
                    modifier = Modifier
                        .clickable { onBodyClick(entry) }
                        .weight(1f)
                )
                
                // Inline play button (only show if audio file exists)
                if (entry.audioFileName != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                onAudioClick(entry.audioFileName!!)
                                HapticUtils.performHapticFeedback(context)
                            }
                            .background(
                                Color(0xFF00F5FF).copy(alpha = if (isPlaying) 0.2f else 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 2.dp,
                                color = if (isPlaying) Color(0xFF00F5FF) else Color(0xFF00F5FF).copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPlaying) "⏸" else "▶",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00F5FF),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Display AI processed text by default, fallback to raw transcription
            val displayText = entry.getAiProcessedContent().ifBlank { entry.getRawTranscriptionContent() }
            
            // Main content - navigate to detail on tap
            Text(
                text = displayText,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    lineHeight = 18.sp
                ),
                modifier = Modifier
                    .clickable { onBodyClick(entry) }
                    .fillMaxWidth()
            )
            
            // Details removed from inline expansion; use onBodyClick to open detail screen
            
            if (entry.audioFileName != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistSmallButton(
                        text = if (isPlaying) "PAUSE" else "PLAY",
                        onClick = { onAudioClick(entry.audioFileName!!) }
                    )
                    BrutalistSmallButton(
                        text = "SHARE",
                        onClick = { onShareClick(entry.audioFileName!!) }
                    )
                    BrutalistSmallButton(
                        text = "REPROCESS",
                        onClick = { onReprocessClick(entry) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val context = LocalContext.current
                    BrutalistSmallButton(
                        text = "COPY RAW",
                        onClick = {
                            val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
                            val clip = ClipData.newPlainText("Raw Text", entry.getRawTranscriptionContent())
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Raw text copied", Toast.LENGTH_SHORT).show()
                            HapticUtils.performHapticFeedback(context)
                        }
                    )
                    if (!entry.aiProcessed.isNullOrBlank()) {
                        BrutalistSmallButton(
                            text = "COPY AI",
                            onClick = {
                                val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
                                val clip = ClipData.newPlainText("AI Processed Text", entry.getAiProcessedContent())
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "AI processed text copied", Toast.LENGTH_SHORT).show()
                                HapticUtils.performHapticFeedback(context)
                            }
                        )
                    }
                }
            }
        }
    }
}
