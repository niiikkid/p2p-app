package com.android.autopay.presentation.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = BackgroundGrey
)

@Composable
fun AutoPayTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = if (darkTheme) baseColorScheme else baseColorScheme.copy(background = BackgroundGrey)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
