package cl.conatacoch.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import cl.conatacoch.app.ui.navigation.AppNavHost
import cl.conatacoch.app.ui.theme.ConatacochTheme

/** FragmentActivity porque BiometricPrompt la necesita. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by AppContainer.preferencias.preferencias.collectAsState()
            val oscuro = prefs.modoOscuro ?: isSystemInDarkTheme()
            // Íconos de la barra de estado según el modo elegido en la app.
            LaunchedEffect(oscuro) {
                val estilo = if (oscuro) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(estilo, estilo)
            }
            ConatacochTheme(oscuro) { AppNavHost() }
        }
    }
}
