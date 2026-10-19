package cl.conatacoch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colores que Material 3 no cubre: texto verde, cabecera negra y avisos. */
@Immutable
data class ColoresExtra(
    val oscuro: Boolean,
    val verdeTexto: Color,
    val cabecera: Color,
    val avisoFondo: Color,
    val avisoTexto: Color,
    val infoFondo: Color,
    val infoTexto: Color,
    val neutroFondo: Color,
)

private val ExtraClaro = ColoresExtra(
    oscuro = false,
    verdeTexto = VerdeOscuro,
    cabecera = Negro,
    avisoFondo = AvisoFondoC,
    avisoTexto = AvisoTextoC,
    infoFondo = InfoFondoC,
    infoTexto = InfoTextoC,
    neutroFondo = NeutroFondoC,
)

private val ExtraOscuro = ColoresExtra(
    oscuro = true,
    verdeTexto = VerdeTextoO,
    cabecera = Color.Black,
    avisoFondo = AvisoFondoO,
    avisoTexto = AvisoTextoO,
    infoFondo = InfoFondoO,
    infoTexto = InfoTextoO,
    neutroFondo = NeutroFondoO,
)

private val Claro = lightColorScheme(
    primary = VerdeOscuro,
    onPrimary = Blanco,
    primaryContainer = VerdeSuaveC,
    onPrimaryContainer = VerdeTextoSuaveC,
    secondary = VerdeMarca,
    onSecondary = Negro,
    secondaryContainer = VerdeSuaveC,
    onSecondaryContainer = VerdeOscuro,
    background = FondoC,
    onBackground = Negro,
    surface = Blanco,
    onSurface = Negro,
    onSurfaceVariant = TextoSecundarioC,
    surfaceContainerLowest = Blanco,
    surfaceContainerLow = Blanco,
    surfaceContainer = Blanco,
    surfaceContainerHigh = Blanco,
    surfaceContainerHighest = Blanco,
    outline = BordeC,
    outlineVariant = DivisorC,
    error = ErrorC,
    onError = Blanco,
    errorContainer = ErrorSuaveC,
    onErrorContainer = ErrorTextoC,
)

private val Oscuro = darkColorScheme(
    primary = VerdeMarca,
    onPrimary = FondoO,
    primaryContainer = VerdeSuaveO,
    onPrimaryContainer = VerdeTextoSuaveO,
    secondary = VerdeMarca,
    onSecondary = FondoO,
    secondaryContainer = VerdeSuaveO,
    onSecondaryContainer = VerdeTextoO,
    background = FondoO,
    onBackground = TextoO,
    surface = SuperficieO,
    onSurface = TextoO,
    onSurfaceVariant = TextoSecundarioO,
    surfaceContainerLowest = TarjetaO,
    surfaceContainerLow = TarjetaO,
    surfaceContainer = TarjetaO,
    surfaceContainerHigh = TarjetaO,
    surfaceContainerHighest = TarjetaO,
    outline = BordeO,
    outlineVariant = DivisorO,
    error = ErrorO,
    onError = FondoO,
    errorContainer = ErrorSuaveO,
    onErrorContainer = ErrorTextoO,
)

private val LocalColoresExtra = staticCompositionLocalOf { ExtraClaro }

/** `modoOscuro = null` sigue al sistema. */
@Composable
fun ConatacochTheme(modoOscuro: Boolean? = null, content: @Composable () -> Unit) {
    val oscuro = modoOscuro ?: isSystemInDarkTheme()
    CompositionLocalProvider(LocalColoresExtra provides if (oscuro) ExtraOscuro else ExtraClaro) {
        MaterialTheme(
            colorScheme = if (oscuro) Oscuro else Claro,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Acceso: `Extra.verdeTexto`. */
val Extra: ColoresExtra
    @Composable @ReadOnlyComposable get() = LocalColoresExtra.current
