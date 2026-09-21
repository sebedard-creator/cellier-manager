package com.cellier.manager.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.cellier.manager.data.BeverageType

/**
 * Schéma de couleurs pour les vins (bourgogne) — thème par défaut.
 */
private val WineColorScheme = darkColorScheme(
    primary = WineAccent,
    onPrimary = OnPrimary,
    primaryContainer = WineAccentContainer,
    onPrimaryContainer = WineAccentOnContainer,

    secondary = WineAccent,
    onSecondary = OnPrimary,
    secondaryContainer = WineAccentContainer,
    onSecondaryContainer = WineAccentOnContainer,

    tertiary = BeerAccent,
    onTertiary = Background,
    tertiaryContainer = BeerAccentContainer,
    onTertiaryContainer = BeerAccentOnContainer,

    background = Background,
    onBackground = OnBackground,

    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,

    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,

    outline = Outline,
    outlineVariant = OutlineVariant,

    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

/**
 * Schéma de couleurs pour les bières (ambre/jaune brûlé). Primary devient
 * BeerAccent, tertiary conserve le bourgogne pour ne pas perdre l'ancrage.
 */
private val BeerColorScheme = darkColorScheme(
    primary = BeerAccent,
    onPrimary = Background,
    primaryContainer = BeerAccentContainer,
    onPrimaryContainer = BeerAccentOnContainer,

    secondary = BeerAccent,
    onSecondary = Background,
    secondaryContainer = BeerAccentContainer,
    onSecondaryContainer = BeerAccentOnContainer,

    tertiary = WineAccent,
    onTertiary = OnPrimary,
    tertiaryContainer = WineAccentContainer,
    onTertiaryContainer = WineAccentOnContainer,

    background = Background,
    onBackground = OnBackground,

    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,

    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,

    outline = Outline,
    outlineVariant = OutlineVariant,

    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

/**
 * Thème principal de l'app.
 *
 * @param beverageType Si fourni, change les couleurs primary du thème.
 *   - null ou VIN : bourgogne (défaut)
 *   - BIERE : ambre/jaune
 *
 * Permet de faire réagir la fiche détail et l'écran Nouvelle fiche au type sélectionné.
 */
@Composable
fun CellierManagerTheme(
    beverageType: BeverageType? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when (beverageType) {
        BeverageType.BIERE -> BeerColorScheme
        else -> WineColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CellierTypography,
        content = content
    )
}
