package com.slumdog88.dictationkeyboardai.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.LogEntry
import com.slumdog88.dictationkeyboardai.LogsActivity
import com.slumdog88.dictationkeyboardai.R
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

// Neo-Brutalist Colors (matching the XML theme)
private val nbBase = Color(0xFF0B0B0F)
private val nbSurface = Color(0xFF0F0F14)
private val nbSurfaceAlt = Color(0xFF14141A)
private val nbGray900 = Color(0xFF1A1A1A)
private val nbBorder = Color(0xFF2C1E45)
private val nbBorderStrong = Color(0xFF3A275A)
private val nbCyan = Color(0xFF00F5FF)
private val nbWhite = Color.White
private val nbGray500 = Color(0xFF9A9A9A)
private val nbPressed = Color(0xFF2A1147)

// Neo-Brutalist Border Style
private val brutalBorder = androidx.compose.foundation.BorderStroke(4.dp, nbBorder)

@Composable
fun LogEntryRowDM(
    entry: LogEntry,
    onPlayAudio: (String) -> Unit,
    onReprocess: (LogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(nbSurface)
            .border(4.dp, nbBorder, RoundedCornerShape(2.dp))
            .padding(16.dp)
    ) {
        // Collapsed view - timestamp button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    HapticUtils.performHapticFeedback(context)
                    val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.LogDetailActivity::class.java)
                    intent.putExtra("rawLog", entry.rawText.ifBlank { entry.userMessage ?: "" })
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = nbSurfaceAlt,
                    contentColor = nbGray900
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, nbBorderStrong)
            ) {
                Text(
                    text = if (entry.isReprocessed) "⟲ ${entry.timestamp} (REPROCESSED)" else entry.timestamp,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (entry.isReprocessed) Color(0xFFFFD700) else nbGray900
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play button (only if audio exists)
            entry.audioFileName?.let { audioFileName ->
                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context)
                        onPlayAudio(audioFileName)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = nbCyan,
                        contentColor = nbGray900
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = brutalBorder,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "▶",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Body content (tap to open detail)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            // Action buttons row
            entry.audioFileName?.let { audioFileName ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Share button (placeholder)
                    Button(
                        onClick = {
                            HapticUtils.performHapticFeedback(context)
                            Toast.makeText(context, "Share functionality not implemented yet", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = nbCyan,
                            contentColor = nbGray900
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = brutalBorder,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(text = "📤", style = TextStyle(fontSize = 20.sp))
                    }

                    // Play button
                    Button(
                        onClick = {
                            HapticUtils.performHapticFeedback(context)
                            onPlayAudio(audioFileName)
                        },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = nbCyan,
                            contentColor = nbGray900
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = brutalBorder,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(text = "▶", style = TextStyle(fontSize = 20.sp))
                    }

                    // Reprocess button
                    Button(
                        onClick = {
                            HapticUtils.performHapticFeedback(context)
                            onReprocess(entry)
                        },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = nbCyan,
                            contentColor = nbGray900
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = brutalBorder,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(text = "⟲", style = TextStyle(fontSize = 20.sp))
                    }
                }
            }

            // User message block (clickable to open details)
            Text(
                text = entry.userMessage ?: "No transcription available",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = nbGray900
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(nbSurface)
                    .border(4.dp, nbBorder, RoundedCornerShape(2.dp))
                    .padding(16.dp)
                    .clickable {
                        HapticUtils.performHapticFeedback(context)
                        val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.LogDetailActivity::class.java)
                        intent.putExtra("rawLog", entry.rawText.ifBlank { entry.userMessage ?: "" })
                        context.startActivity(intent)
                    }
            )

            // AI processed block (if present)
            entry.aiProcessed?.takeIf { it.isNotBlank() && it != entry.userMessage }?.let { aiText ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = aiText,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = nbGray900
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(nbSurface)
                        .border(4.dp, nbBorder, RoundedCornerShape(2.dp))
                        .padding(16.dp)
                        .clickable {
                            HapticUtils.performHapticFeedback(context)
                            val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.LogDetailActivity::class.java)
                            intent.putExtra("rawLog", entry.rawText.ifBlank { entry.userMessage ?: "" })
                            context.startActivity(intent)
                        }
                )
            }
        }
    }
}

@Composable
fun LogsScreenDM(
    onBack: () -> Unit,
    onBrowseAudio: () -> Unit,
    onDeleteAll: () -> Unit,
    onApplyLimit: (Int) -> Unit,
    recordingHistoryLimit: Int,
    onRefresh: () -> Unit,
    logEntries: List<LogEntry>,
    onPlayAudio: (String) -> Unit,
    onReprocess: (LogEntry) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(nbBase)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "RECORDING HISTORY",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = nbWhite
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable {
                    HapticUtils.performHapticFeedback(context)
                    onRefresh()
                    Toast.makeText(context, "Logs refreshed", Toast.LENGTH_SHORT).show()
                }
        )

        // Browse Audio Button
        Button(
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onBrowseAudio()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = nbWhite
            ),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
        ) {
            Text(
                text = "📂 OPEN WONDERWHISPER FOLDER",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
        }

        // Recording History Settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(12.dp)
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KEEP LAST:",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = nbWhite
                ),
                modifier = Modifier.weight(1f)
            )

            var limitText by remember { mutableStateOf(recordingHistoryLimit.toString()) }

            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                modifier = Modifier
                    .width(70.dp)
                    .height(36.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = nbWhite
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = nbWhite,
                    unfocusedBorderColor = nbWhite,
                    focusedContainerColor = Color(0xFF333333),
                    unfocusedContainerColor = Color(0xFF333333)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    HapticUtils.performHapticFeedback(context)
                    val limit = try {
                        val parsed = limitText.toInt()
                        parsed.coerceIn(0, 1000)
                    } catch (e: NumberFormatException) {
                        50
                    }
                    onApplyLimit(limit)
                },
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = nbWhite
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
            ) {
                Text(
                    text = "APPLY",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }
        }

        Text(
            text = "Set to 0 for no logs (privacy mode). Older recordings auto-deleted.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = nbWhite
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Logs List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
        ) {
            items(logEntries) { entry ->
                LogEntryRowDM(
                    entry = entry,
                    onPlayAudio = onPlayAudio,
                    onReprocess = onReprocess,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }

        // Delete All Button
        Button(
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onDeleteAll()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = nbWhite
            ),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, nbWhite)
        ) {
            Text(
                text = "DELETE ALL RECORDINGS",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }

        // Back Button
        Button(
            onClick = {
                HapticUtils.performHapticFeedback(context)
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = nbWhite
            ),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
        ) {
            Text(
                text = "BACK",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }
    }

@Composable
fun LogEntryRowDM(
    entry: LogEntry,
    onPlayAudio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(nbSurface)
            .border(4.dp, nbBorder, RoundedCornerShape(2.dp))
            .padding(16.dp)
    ) {
        // Collapsed view - timestamp button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    HapticUtils.performHapticFeedback(context)
                    val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.LogDetailActivity::class.java)
                    intent.putExtra("rawLog", entry.rawText.ifBlank { entry.userMessage ?: "" })
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = nbSurfaceAlt,
                    contentColor = nbGray900
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, nbBorderStrong)
            ) {
                Text(
                    text = if (entry.isReprocessed) "⟲ ${entry.timestamp} (REPROCESSED)" else entry.timestamp,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (entry.isReprocessed) Color(0xFFFFD700) else nbGray900
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play button (only if audio exists)
            entry.audioFileName?.let { audioFileName ->
                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context)
                        onPlayAudio(audioFileName)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = nbCyan,
                        contentColor = nbGray900
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = brutalBorder,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "▶",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Body content (tap to open detail)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
                // Action buttons row
                entry.audioFileName?.let { audioFileName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Share button
                        Button(
                            onClick = {
                                HapticUtils.performHapticFeedback(context)
                                // TODO: Implement share functionality
                                Toast.makeText(context, "Share functionality not implemented yet", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = nbCyan,
                                contentColor = nbGray900
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = brutalBorder,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "📤",
                                style = TextStyle(fontSize = 20.sp)
                            )
                        }

                        // Play button
                        Button(
                            onClick = {
                                HapticUtils.performHapticFeedback(context)
                                onPlayAudio(audioFileName)
                            },
                            modifier = Modifier.size(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = nbCyan,
                                contentColor = nbGray900
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = brutalBorder,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "▶",
                                style = TextStyle(fontSize = 20.sp)
                            )
                        }

                        // Reprocess button
                        Button(
                            onClick = {
                                HapticUtils.performHapticFeedback(context)
                                // TODO: Implement reprocess functionality
                                Toast.makeText(context, "Reprocess functionality not implemented yet", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = nbCyan,
                                contentColor = nbGray900
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = brutalBorder,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "⟲",
                                style = TextStyle(fontSize = 20.sp)
                            )
                        }
                    }
                }

                // User message section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "You said:",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = nbGray900
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                HapticUtils.performHapticFeedback(context)
                                // TODO: Implement copy functionality
                                Toast.makeText(context, "Copy functionality not implemented yet", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = nbCyan,
                                contentColor = nbGray900
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(4.dp, nbBorder)
                        ) {
                            Text(
                                text = "COPY",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }

                    Text(
                        text = entry.userMessage ?: "No transcription available",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = nbGray900
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(nbSurface)
                            .border(4.dp, nbBorder, RoundedCornerShape(2.dp))
                            .padding(16.dp)
                            .clickable {
                                HapticUtils.performHapticFeedback(context)
                                val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.LogDetailActivity::class.java)
                                intent.putExtra("rawLog", entry.rawText.ifBlank { entry.userMessage ?: "" })
                                context.startActivity(intent)
                            }
                    )
                }

                // AI processed section (if available)
                entry.aiProcessed?.takeIf { it.isNotBlank() && it != entry.userMessage }?.let { aiText ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI processed:",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = nbGray900
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context)
                                    // TODO: Implement copy functionality
                                    Toast.makeText(context, "Copy functionality not implemented yet", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(40.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = nbCyan,
                                    contentColor = nbGray900
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(4.dp, nbBorder)
                            ) {
                                Text(
                                    text = "COPY",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }

                        Text(
                            text = aiText,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                color = nbGray900
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(nbSurface)
                                .border(4.dp, nbBorder, RoundedCornerShape(2.dp))
                                .padding(16.dp)
                                .clickable {
                                    HapticUtils.performHapticFeedback(context)
                                    val intent = android.content.Intent(context, com.slumdog88.dictationkeyboardai.LogDetailActivity::class.java)
                                    intent.putExtra("rawLog", entry.rawText.ifBlank { entry.userMessage ?: "" })
                                    context.startActivity(intent)
                                }
                        )
                    }
                }
            }
        }
    }
}
