package cl.conatacoch.app.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PU-01: validación del RUT con dígito verificador. */
class RutValidatorTest {

    @Test fun pu01_conPuntosYGuion_valido() = assertTrue(RutValidator.isValid("12.345.678-5"))

    @Test fun pu01_digitoVerificadorIncorrecto_invalido() = assertFalse(RutValidator.isValid("12.345.678-9"))

    @Test fun pu01_sinPuntosConGuion_valido() = assertTrue(RutValidator.isValid("12345678-5"))

    @Test fun pu01_sinPuntosNiGuion_valido() = assertTrue(RutValidator.isValid("123456785"))

    @Test fun pu01_conPuntosSinGuion_valido() = assertTrue(RutValidator.isValid("12.345.6785"))

    @Test fun pu01_digitoK_mayuscula_valido() = assertTrue(RutValidator.isValid("10.000.013-K"))

    @Test fun pu01_digitoK_minusculaSinPuntos_valido() = assertTrue(RutValidator.isValid("10000013-k"))

    @Test fun pu01_digitoKIncorrecto_invalido() = assertFalse(RutValidator.isValid("12.345.678-K"))

    @Test fun pu01_digitoVerificadorCero_valido() = assertTrue(RutValidator.isValid("11.111.117-0"))

    @Test fun pu01_sieteDigitos_valido() = assertTrue(RutValidator.isValid("7.654.321-6"))

    @Test fun pu01_espaciosAlrededor_seIgnoran() = assertTrue(RutValidator.isValid(" 12.345.678-5 "))

    @Test fun pu01_vacioOSoloDigito_invalido() {
        assertFalse(RutValidator.isValid(""))
        assertFalse(RutValidator.isValid("5"))
    }

    @Test fun pu01_letrasEnElCuerpo_invalido() {
        assertFalse(RutValidator.isValid("12.3A5.678-5"))
        assertFalse(RutValidator.isValid("12345678-X"))
    }

    @Test fun pu01_largoFueraDeRango_invalido() {
        assertFalse(RutValidator.isValid("123456789-2"))
        assertFalse(RutValidator.isValid("1-9"))
        assertFalse(RutValidator.isValid("123.456-0"))
    }

    @Test fun pu01_guionFueraDeLugar_invalido() = assertFalse(RutValidator.isValid("12-345.678-5"))
}
