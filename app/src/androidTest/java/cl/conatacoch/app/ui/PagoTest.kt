package cl.conatacoch.app.ui

import androidx.compose.ui.test.assertCountEquals
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

/** Flujo de pago: Pagar → patente y tarifa (monto no editable) → Continuar → Pago aprobado. */
@RunWith(AndroidJUnit4::class)
class PagoTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun pagoQr_muestraPatenteYTarifa_yApruebaElPago() {
        rule.onNodeWithText("Soy pasajero").performClick()
        val campos = rule.onAllNodes(hasSetTextAction())
        campos[0].performTextInput("camila.reyes@correo.cl")
        campos[1].performTextInput("colectivo-demo-2026")
        rule.onNodeWithText("Ingresar").performScrollTo().performClick()

        rule.onNodeWithText("Pagar").performClick()
        rule.onNodeWithText("Escanear QR").performClick()

        rule.onNodeWithText("ABCD-12").assertIsDisplayed()
        rule.onNodeWithText("Tarifa del Recorrido 1").assertIsDisplayed()
        rule.onNodeWithText("\$800").assertIsDisplayed()
        // El monto no se puede editar: no hay ningún campo de texto en la pantalla.
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)

        rule.onNodeWithText("Continuar en Rutpay").performScrollTo().performClick()
        rule.onNodeWithText("Pago aprobado").assertIsDisplayed()
    }
}
