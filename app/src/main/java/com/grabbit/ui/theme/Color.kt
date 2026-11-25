package com.grabbit.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF006A60)
private val TealContainer = Color(0xFF9EF2E4)

val LightColors =
    lightColorScheme(
        primary = Teal,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = TealContainer,
        onPrimaryContainer = Color(0xFF00201C),
        secondary = Color(0xFF4A635F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E2),
        onSecondaryContainer = Color(0xFF051F1C),
        tertiary = Color(0xFF456179),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCBE6FF),
        onTertiaryContainer = Color(0xFF001E31),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFAFDFB),
        onBackground = Color(0xFF191C1B),
        surface = Color(0xFFFAFDFB),
        onSurface = Color(0xFF191C1B),
        surfaceVariant = Color(0xFFDAE5E1),
        onSurfaceVariant = Color(0xFF3F4946),
        outline = Color(0xFF6F7976),
        outlineVariant = Color(0xFFBEC9C5),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F7F5),
        surfaceContainer = Color(0xFFEEF2F0),
        surfaceContainerHigh = Color(0xFFE8ECEA),
        surfaceContainerHighest = Color(0xFFE2E6E4),
        inverseSurface = Color(0xFF2E3130),
        inverseOnSurface = Color(0xFFEFF1EF),
        inversePrimary = Color(0xFF82D5C7),
    )

val DarkColors =
    darkColorScheme(
        primary = Color(0xFF82D5C7),
        onPrimary = Color(0xFF003731),
        primaryContainer = Color(0xFF005048),
        onPrimaryContainer = TealContainer,
        secondary = Color(0xFFB1CCC6),
        onSecondary = Color(0xFF1C3531),
        secondaryContainer = Color(0xFF334B47),
        onSecondaryContainer = Color(0xFFCCE8E2),
        tertiary = Color(0xFFACCAE5),
        onTertiary = Color(0xFF123349),
        tertiaryContainer = Color(0xFF2B4961),
        onTertiaryContainer = Color(0xFFCBE6FF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0F1413),
        onBackground = Color(0xFFDEE4E2),
        surface = Color(0xFF0F1413),
        onSurface = Color(0xFFDEE4E2),
        surfaceVariant = Color(0xFF3F4946),
        onSurfaceVariant = Color(0xFFBEC9C5),
        outline = Color(0xFF889390),
        outlineVariant = Color(0xFF3F4946),
        surfaceContainerLowest = Color(0xFF0A0F0E),
        surfaceContainerLow = Color(0xFF171D1B),
        surfaceContainer = Color(0xFF1B211F),
        surfaceContainerHigh = Color(0xFF252B29),
        surfaceContainerHighest = Color(0xFF303634),
        inverseSurface = Color(0xFFDEE4E2),
        inverseOnSurface = Color(0xFF2E3130),
        inversePrimary = Teal,
    )
