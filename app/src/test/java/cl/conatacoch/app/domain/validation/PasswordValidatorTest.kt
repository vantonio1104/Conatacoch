package cl.conatacoch.app.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PU-02: largo de la contraseña (mínimo 8, máximo 64). */
class PasswordValidatorTest {

    @Test fun pu02_7caracteres_seRechaza() = assertFalse(PasswordValidator.isValid("a".repeat(7)))

    @Test fun pu02_8caracteres_seAcepta() = assertTrue(PasswordValidator.isValid("a".repeat(8)))

    @Test fun pu02_64caracteres_seAcepta() = assertTrue(PasswordValidator.isValid("a".repeat(64)))

    @Test fun pu02_65caracteres_seRechaza() = assertFalse(PasswordValidator.isValid("a".repeat(65)))

    @Test fun pu02_vacia_seRechaza() = assertFalse(PasswordValidator.isValid(""))

    @Test fun pu02_emojiCuentaComoUnCaracter() = assertFalse(PasswordValidator.isValid("🚕".repeat(7)))
}
