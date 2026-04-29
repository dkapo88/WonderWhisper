package com.slumdog88.dictationkeyboardai.ui.keyboard.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.ui.keyboard.KeyboardViewModel
import com.slumdog88.dictationkeyboardai.ui.theme.KeyboardPalette
import com.slumdog88.dictationkeyboardai.HapticUtils

import com.slumdog88.dictationkeyboardai.ui.keyboard.components.AlphabetKeyboard

@Composable
fun EmojiKeyboardScreen(viewModel: KeyboardViewModel) {
    val colors = KeyboardPalette.colors
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.keyBackground)
    ) {
        // 1. Search Bar
        EmojiSearchBar(
            query = viewModel.emojiSearchQuery,
            isActive = viewModel.isEmojiSearchActive,
            onQueryChange = { viewModel.onEmojiSearch(it) },
            onFocus = { viewModel.toggleEmojiSearch(true) },
            onCancel = { 
                 viewModel.toggleEmojiSearch(false)
                 HapticUtils.performKeyClick(context)
            }
        )
        
        // 2. Emoji Grid
        Box(modifier = Modifier.weight(1f)) {
            if (viewModel.emojiGridItems.isEmpty()) {
                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                     Text(
                         text = "No emojis found",
                         color = colors.keyTextSecondary
                     )
                 }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 40.dp),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = viewModel.emojiGridItems,
                        key = { emojiItem -> emojiItem.emoji }
                    ) { emojiItem ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .clickable { 
                                    HapticUtils.performKeyClick(context)
                                    viewModel.onKeyClick(emojiItem.emoji) 
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emojiItem.emoji,
                                fontSize = 28.sp
                            )
                        }
                    }
                }
            }
        }
        
        // 3. Conditional Bottom Content
        if (viewModel.isEmojiSearchActive) {
            // Show Keyboard when searching
            // Divider
            HorizontalDivider(thickness = 1.dp, color = colors.keyTextSecondary.copy(alpha = 0.1f))
            AlphabetKeyboard(viewModel)
        } else {
            // Show Category Tabs when browsing
             EmojiBottomBar(viewModel)
        }
    }
}

@Composable
fun EmojiSearchBar(
    query: String,
    isActive: Boolean,
    onQueryChange: (String) -> Unit,
    onFocus: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = KeyboardPalette.colors
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.keyboardBackground)
                .clickable { onFocus() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_search),
                    contentDescription = "Search",
                    tint = if (isActive) colors.accent else colors.keyTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                if (query.isEmpty() && !isActive) {
                    Text(
                        text = "Search emojis...",
                        fontSize = 16.sp,
                        color = colors.keyTextSecondary
                    )
                } else {
                    Text(
                        text = query,
                        fontSize = 16.sp,
                        color = colors.keyTextPrimary
                    )
                    // Blinking cursor simulator could be added here if desired
                    if (isActive) {
                         Box(
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .width(2.dp)
                                .height(18.dp)
                                .background(colors.accent)
                        )
                    }
                }
            }
        }
        
        if (isActive) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel", color = colors.accent)
            }
        }
    }
}

@Composable
fun EmojiBottomBar(viewModel: KeyboardViewModel) {
    val colors = KeyboardPalette.colors
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.keyboardBackground),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back to ABC
        IconButton(
            onClick = { 
                HapticUtils.performKeyClick(context)
                viewModel.onEmojiClick() // Toggle back
            },
            modifier = Modifier.weight(0.15f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_keyboard),
                contentDescription = "Back",
                tint = colors.keyTextPrimary
            )
        }
        
        // Categories Scrollable List
        if (viewModel.emojiCategories.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.weight(0.7f).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp), // Tighter spacing
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(
                    items = viewModel.emojiCategories,
                    key = { category -> category }
                ) { category ->
                    val isSelected = category == viewModel.selectedEmojiCategory
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .width(40.dp) // Fixed small width for tighter packing
                            .clip(RoundedCornerShape(20.dp)) // Circle shape
                            .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { 
                                 HapticUtils.performKeyClick(context)
                                 viewModel.onEmojiCategoryClick(category) 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getCategoryIconOrName(category),
                            fontSize = 20.sp,
                            color = if (isSelected) colors.accent else colors.keyTextSecondary
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(0.7f))
        }
        
        // Backspace
        IconButton(
            onClick = { 
                HapticUtils.performKeyClick(context)
                viewModel.onDeleteClick() 
            },
            modifier = Modifier.weight(0.15f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = "Backspace",
                tint = colors.keyTextPrimary
            )
        }
    }
}

fun getCategoryIconOrName(category: String): String {
    return when(category) {
        "Recent" -> "🕒"
        "Smileys & Emotion" -> "🙂"
        "People & Body" -> "👋"
        "Animals & Nature" -> "🐶"
        "Food & Drink" -> "🍔"
        "Travel & Places" -> "🚗"
        "Activities" -> "⚽"
        "Objects" -> "💡"
        "Symbols" -> "🔣"
        "Flags" -> "🏳️"
        "Component" -> "🏼" // Skin tones etc
        else -> category.take(2)
    }
}
