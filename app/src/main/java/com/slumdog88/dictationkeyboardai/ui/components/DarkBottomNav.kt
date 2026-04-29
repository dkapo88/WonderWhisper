package com.slumdog88.dictationkeyboardai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.navigation.navigationItems

/**
 * Dark Material bottom navigation, adapted from dark-material-compose.
 *
 * Usage:
 * DarkBottomNav(
 *   selectedRoute = currentRoute,
 *   onRouteSelected = { route -> ... }
 * )
 */
@Composable
fun DarkBottomNav(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    // Wrapper to add a subtle top gradient that blends the bar into the background
    Box(modifier = modifier) {
        // Top fade overlay: transparent -> bar color for a softer edge
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(18.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.surface.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        NavigationBar(
            containerColor = colors.surface.copy(alpha = 0.88f),
            contentColor = colors.onSurface,
            tonalElevation = 0.dp
        ) {
            navigationItems.forEach { screen ->
                val selected = selectedRoute == screen.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onRouteSelected(screen.route) },
                    icon = {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = screen.icon),
                            contentDescription = screen.label
                        )
                    },
                    label = { Text(screen.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.surfaceVariant,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}