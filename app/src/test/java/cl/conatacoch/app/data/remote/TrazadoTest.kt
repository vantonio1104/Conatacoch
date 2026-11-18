package cl.conatacoch.app.data.remote

import cl.conatacoch.app.domain.model.Punto
import cl.conatacoch.app.domain.model.puntoEn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrazadoTest {

    private fun calza(csv: String, osm: String) = Regex("^${patronCalle(csv)}$", RegexOption.IGNORE_CASE).matches(osm)

    @Test
    fun patronCalleToleraAvenidaTildesYSufijos() {
        assertTrue(calza("Av. Fernández Albano", "Avenida Fernández Albano"))
        assertTrue(calza("Condores de Chile", "Cóndores de Chile"))
        assertTrue(calza("Av. Ossa", "Ossa"))
        assertTrue(calza("Uruguay", "Avenida Uruguay Norte"))
        assertTrue(calza("Gran Avenida José Miguel Carrera", "Gran Avenida José Miguel Carrera"))
        assertFalse(calza("Uruguay", "Paraguay"))
    }

    @Test
    fun cruceEsElNodoQueCompartenAmbasCalles() {
        val a = mapOf(1L to Punto(0.0, 0.0), 2L to Punto(0.0, 0.001))
        val b = mapOf(2L to Punto(0.0, 0.001), 3L to Punto(0.001, 0.001))
        assertEquals(listOf(Punto(0.0, 0.001)), candidatosCruce(a, b))
    }

    @Test
    fun sinNodoComunSeUsaElParMasCercanoSiEstaCerca() {
        val a = mapOf(1L to Punto(0.0, 0.0))
        val cerca = mapOf(2L to Punto(0.0, 0.0002))
        val lejos = mapOf(3L to Punto(0.0, 0.01))
        assertEquals(listOf(Punto(0.0, 0.0001)), candidatosCruce(a, cerca))
        assertTrue(candidatosCruce(a, lejos).isEmpty())
    }

    @Test
    fun decodificaPolilineaDelEjemploOficial() {
        val puntos = decodificarPolilinea("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(Punto(38.5, -120.2), Punto(40.7, -120.95), Punto(43.252, -126.453)), puntos)
    }

    @Test
    fun tramosSeSolapanEnElPuntoDeUnion() {
        val partes = tramos((1..30).toList(), 25)
        assertEquals(2, partes.size)
        assertEquals(25, partes[0].last())
        assertEquals(25, partes[1].first())
        assertEquals(30, partes[1].last())
        assertEquals(listOf(listOf(1, 2, 3)), tramos(listOf(1, 2, 3), 25))
    }

    @Test
    fun puntoEnLaMitadDeUnaRutaRecta() {
        val ruta = listOf(Punto(0.0, 0.0), Punto(0.0, 1.0), Punto(0.0, 2.0))
        assertEquals(Punto(0.0, 1.0), puntoEn(ruta, 0.5))
        assertEquals(Punto(0.0, 2.0), puntoEn(ruta, 1.0))
        assertNull(puntoEn(emptyList(), 0.5))
    }
}
