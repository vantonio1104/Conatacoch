package cl.conatacoch.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PS-15: el pasajero solo ve las notificaciones que dejó activas en Preferencias. */
class PreferenciasTest {

    @Test fun porDefecto_muestraRetrasosDesviosYReservas_peroNoSinUbicacion() {
        val p = Preferencias()
        assertTrue(p.permite(TipoNotificacion.RETRASO))
        assertTrue(p.permite(TipoNotificacion.DESVIO))
        assertTrue(p.permite(TipoNotificacion.RESERVA_CONFIRMADA))
        assertFalse(p.permite(TipoNotificacion.SIN_UBICACION))
    }

    @Test fun desactivarRetrasos_ocultaSoloLosRetrasos() {
        val p = Preferencias(retrasos = false)
        assertFalse(p.permite(TipoNotificacion.RETRASO))
        assertTrue(p.permite(TipoNotificacion.DESVIO))
    }

    @Test fun cadaPreferenciaControlaSuTipo() {
        assertFalse(Preferencias(desvios = false).permite(TipoNotificacion.DESVIO))
        assertFalse(Preferencias(reservaConfirmada = false).permite(TipoNotificacion.RESERVA_CONFIRMADA))
        assertTrue(Preferencias(sinUbicacion = true).permite(TipoNotificacion.SIN_UBICACION))
    }

    @Test fun losTiposSinPreferencia_siempreSeMuestran() {
        val todoApagado = Preferencias(reservaConfirmada = false, retrasos = false, desvios = false, sinUbicacion = false)
        listOf(TipoNotificacion.RESERVA_CANCELADA, TipoNotificacion.NUEVA_RESERVA, TipoNotificacion.NUEVA_ASIGNACION)
            .forEach { assertTrue("$it debe mostrarse siempre", todoApagado.permite(it)) }
    }
}
