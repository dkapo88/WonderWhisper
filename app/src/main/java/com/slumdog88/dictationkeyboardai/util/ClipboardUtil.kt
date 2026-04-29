package com.slumdog88.dictationkeyboardai.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

fun Context.copy(text: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Copied Text", text)
    clipboardManager.setPrimaryClip(clip)
    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
