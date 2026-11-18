package cl.conatacoch.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.conatacoch.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Registro opcional: se entra sin cuenta, se ve el mapa, y al ir a Pagar se pide iniciar sesión.
 * Después de ingresar vuelve a Pagar.
 */
@RunWith(AndroidJUnit4::class)
class InvitadoTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun invitado_veElMapa_yParaPagarDebeIniciarSesion() {
        rule.onNodeWithText("Soy pasajero").performClick()
        rule.onNodeWithText("Continuar sin registrarme").performScrollTo().performClick()
        rule.onNodeWithText("Cerca de tu paradero").assertIsDisplayed()

        rule.onNodeWithText("Pagar").performClick()
        rule.onNodeWithText("Necesitas una cuenta").assertIsDisplayed()
        rule.onNodeWithText("Iniciar sesión").performScrollTo().performClick()

        val campos = rule.onAllNodes(hasSetTextAction())
        campos[0].performTextInput("camila.reyes@correo.cl")
        campos[1].performTextInput("colectivo-demo-2026")
        rule.onNodeWithText("Ingresar").performScrollTo().performClick()

        rule.onNodeWithText("Escanear QR").assertIsDisplayed()
    }
}
