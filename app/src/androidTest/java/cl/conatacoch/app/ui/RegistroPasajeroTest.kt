package cl.conatacoch.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.conatacoch.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Flujo de registro del pasajero: formulario → código de 6 dígitos → mapa. */
@RunWith(AndroidJUnit4::class)
class RegistroPasajeroTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun registroCompleto_llegaAlMapa() {
        rule.onNodeWithText("Soy pasajero").performClick()
        rule.onNodeWithText("Regístrate").performClick()

        // Campos en orden: Nombre, Correo electrónico, Contraseña.
        val campos = rule.onAllNodes(hasSetTextAction())
        campos[0].performTextInput("Ana Pérez")
        campos[1].performTextInput("ana.perez@correo.cl")
        campos[2].performTextInput("una-clave-de-prueba-2026")
        rule.onAllNodesWithText("Crear cuenta").filterToOne(hasClickAction()).performScrollTo().performClick()

        rule.onNodeWithText("Revisa tu correo").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("123456")
        rule.onNodeWithText("Verificar").performClick()

        rule.onNodeWithText("Cerca de tu paradero").assertIsDisplayed()
    }
}
