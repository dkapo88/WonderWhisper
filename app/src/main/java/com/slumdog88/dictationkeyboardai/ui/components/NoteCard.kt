package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.Note

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = colorResource(id = R.color.nb_lime)
    
    BrutalCard(accentColor = accentColor, modifier = modifier.clickable { onClick() }) {
        Column {
            // Date (smaller font above title)
            Text(
                text = note.getFormattedTimestamp(),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.1.sp
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // AI-generated Title (use getDisplayTitle() to prefer AI title)
            Text(
                text = note.getDisplayTitle().uppercase(),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.2.sp
                ),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content preview
            Text(
                text = note.content.take(100) + if (note.content.length > 100) "..." else "",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    lineHeight = 18.sp
                ),
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrutalistSmallButton(
                    text = "EDIT",
                    onClick = onClick
                )
                BrutalistSmallButton(
                    text = "DELETE",
                    onClick = onDeleteClick
                )
            }
        }
    }
}