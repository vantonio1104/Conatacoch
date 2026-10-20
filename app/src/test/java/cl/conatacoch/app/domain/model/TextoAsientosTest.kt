package cl.conatacoch.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** PU-12: texto de asientos disponibles. */
class TextoAsientosTest {

    private fun colectivo(asientos: Int?) = Colectivo("ABCD-12", "Recorrido 1", asientos, minutosLlegada = 4)

    @Test fun pu12_ceroAsientos_muestraLleno() = assertEquals("Lleno", textoAsientos(colectivo(0)))

    @Test fun pu12_dosAsientos_muestraDisponibles() = assertEquals("2 asientos disponibles", textoAsientos(colectivo(2)))

    @Test fun pu12_sinDato_muestraSinDato() = assertEquals("Sin dato", textoAsientos(colectivo(null)))
}
