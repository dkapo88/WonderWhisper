package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Standardized app top bar with:
 * - Left back arrow (preferred style)
 * - Consistent typography and colors
 * - Safe status bar padding to avoid overlap
 *
 * Use this across all screens for consistent look and feel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarDM(
    title: String,
    onBack: (() -> Unit)? = null,
    centered: Boolean = false,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurface
    )

    // Provide non-null composables to API to avoid type mismatch
    val navigationIconComposable: @Composable () -> Unit = if (onBack != null) {
        {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    } else {
        {}
    }

    val actionsComposable: @Composable RowScope.() -> Unit = actions ?: {}

    val commonModifier = modifier.statusBarsPadding()

    if (centered) {
        CenterAlignedTopAppBar(
            modifier = commonModifier,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = navigationIconComposable,
            actions = actionsComposable,
            colors = colors
        )
    } else {
        TopAppBar(
            modifier = commonModifier,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = navigationIconComposable,
            actions = actionsComposable,
            colors = colors
        )
    }
}