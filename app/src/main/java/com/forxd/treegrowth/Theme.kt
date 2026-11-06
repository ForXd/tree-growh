package com.forxd.treegrowth

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF28573F)
val Cream = Color(0xFFF5F5ED)
val Lime = Color(0xFFD7E8AA)
val Clay = Color(0xFFAF673D)
val LocalReduceMotion = staticCompositionLocalOf { false }
val Accents = listOf(Color(0xFFEAF0DD), Color(0xFFF5E8D7), Color(0xFFE6EDF7), Color(0xFFF0E4EE), Color(0xFFE2EEEB))
@Composable fun GardenTheme(prefs: Preferences, content: @Composable () -> Unit) {
    val dark = prefs.theme == "dark" || (prefs.theme == "system" && isSystemInDarkTheme())
    val context = androidx.compose.ui.platform.LocalContext.current
    SideEffect { (context as? android.app.Activity)?.let { activity -> androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
    val colors = if (dark) darkColorScheme(primary = Lime, onPrimary = Forest, background = Color(0xFF151D18), surface = Color(0xFF1D2921), onSurface = Color(0xFFEDF0E4), onBackground = Color(0xFFEDF0E4), onSurfaceVariant = Color(0xFFBAC7BA), surfaceVariant = Color(0xFF344637), secondaryContainer = Color(0xFF344637), onSecondaryContainer = Lime, outline = Color(0xFF788B79), outlineVariant = Color(0xFF405043))
    else lightColorScheme(primary = Forest, onPrimary = Color.White, background = Cream, surface = Color.White, onSurface = Color(0xFF26392D), onBackground = Color(0xFF26392D), onSurfaceVariant = Color(0xFF6A786C), surfaceVariant = Color(0xFFE9EDDF), secondaryContainer = Color(0xFFE2EBCE), onSecondaryContainer = Forest, outline = Color(0xFF7F8C7D), outlineVariant = Color(0xFFE3E8DD))
    CompositionLocalProvider(LocalReduceMotion provides prefs.reducedMotion) {
        MaterialTheme(colorScheme = colors, typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 23.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        ), content = content)
    }
}
