package cl.conatacoch.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PU-11: con velocidad > 0, AC-01 a AC-05 deshabilitados y AC-06 habilitado.
 * La app aún no lee la velocidad del GPS: el estado es "detenido" (velocidad 0) o no.
 */
class AvisoTest {

    private fun detenido(velocidadKmh: Double) = velocidadKmh <= 0.0

    @Test fun pu11_enMovimiento_soloAC06Habilitado() {
        val d = detenido(velocidadKmh = 25.0)
        listOf(Aviso.AC01, Aviso.AC02, Aviso.AC03, Aviso.AC04, Aviso.AC05).forEach {
            assertFalse("${it.codigo} debería estar deshabilitado", it.habilitado(d))
        }
        assertTrue(Aviso.AC06.habilitado(d))
    }

    @Test fun pu11_detenido_todosHabilitados() {
        val d = detenido(velocidadKmh = 0.0)
        Aviso.entries.forEach { assertTrue("${it.codigo} debería estar habilitado", it.habilitado(d)) }
    }
}
