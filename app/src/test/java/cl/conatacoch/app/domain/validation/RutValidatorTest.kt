package cl.conatacoch.app.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RutValidatorTest {

    @Test fun rutConPuntosYGuionValido() = assertTrue(RutValidator.isValid("12.345.678-5"))

    @Test fun rutSinPuntosValido() = assertTrue(RutValidator.isValid("12345678-5"))

    @Test fun rutSinGuionValido() = assertTrue(RutValidator.isValid("123456785"))

    @Test fun digitoVerificadorKMayusculaYMinuscula() {
        assertTrue(RutValidator.isValid("10.000.013-K"))
        assertTrue(RutValidator.isValid("10000013-k"))
    }

    @Test fun digitoVerificadorCero() = assertTrue(RutValidator.isValid("11.111.117-0"))

    @Test fun rutDeSieteDigitosValido() = assertTrue(RutValidator.isValid("7.654.321-6"))

    @Test fun digitoVerificadorIncorrecto() = assertFalse(RutValidator.isValid("12.345.678-9"))

    @Test fun vacioOSoloDigitoInvalido() {
        assertFalse(RutValidator.isValid(""))
        assertFalse(RutValidator.isValid("5"))
    }

    @Test fun caracteresNoNumericosInvalidos() {
        assertFalse(RutValidator.isValid("12.3A5.678-5"))
        assertFalse(RutValidator.isValid("12345678-X"))
    }

    @Test fun cuerpoDemasiadoLargoInvalido() = assertFalse(RutValidator.isValid("123456789-2"))
}
