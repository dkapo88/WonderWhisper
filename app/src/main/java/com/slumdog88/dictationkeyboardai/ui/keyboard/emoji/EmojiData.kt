package com.slumdog88.dictationkeyboardai.ui.keyboard.emoji

import kotlinx.serialization.Serializable

@Serializable
data class EmojiItem(
    val emoji: String,
    val description: String,
    val category: String,
    val aliases: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val unicode_version: String = "",
    val ios_version: String = ""
)
