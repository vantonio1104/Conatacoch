package cl.conatacoch.app.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordValidatorTest {

    @Test fun catorceCaracteresInvalida() = assertFalse(PasswordValidator.isValid("a".repeat(14)))

    @Test fun quinceCaracteresValida() = assertTrue(PasswordValidator.isValid("a".repeat(15)))

    @Test fun masDeQuinceValida() = assertTrue(PasswordValidator.isValid("una frase larga y segura"))

    @Test fun vaciaInvalida() = assertFalse(PasswordValidator.isValid(""))
}
