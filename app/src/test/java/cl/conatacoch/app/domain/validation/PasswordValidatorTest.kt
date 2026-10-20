package cl.conatacoch.app.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PU-02: largo de la contraseña (mínimo 15, máximo 128 según el plan de pruebas). */
class PasswordValidatorTest {

    @Test fun pu02_14caracteres_seRechaza() = assertFalse(PasswordValidator.isValid("a".repeat(14)))

    @Test fun pu02_15caracteres_seAcepta() = assertTrue(PasswordValidator.isValid("a".repeat(15)))

    @Test fun pu02_128caracteres_seAcepta() = assertTrue(PasswordValidator.isValid("a".repeat(128)))

    @Test fun pu02_129caracteres_seRechaza() = assertFalse(PasswordValidator.isValid("a".repeat(129)))

    @Test fun pu02_vacia_seRechaza() = assertFalse(PasswordValidator.isValid(""))

    @Test fun pu02_emojiCuentaComoUnCaracter() = assertFalse(PasswordValidator.isValid("🚕".repeat(14)))
}
