package cl.conatacoch.app.data.fake

import cl.conatacoch.app.domain.model.Colectivo
import cl.conatacoch.app.domain.model.DatosPago
import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.Pago
import cl.conatacoch.app.domain.model.Reserva
import cl.conatacoch.app.domain.model.TipoNotificacion
import cl.conatacoch.app.domain.repository.PasajeroRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import kotlin.random.Random

class FakePasajeroRepository : PasajeroRepository {

    private val tarifas = mapOf("Recorrido 1" to 800, "Recorrido 2" to 900)

    private val colectivos = listOf(
        Colectivo("ABCD-12", "Recorrido 1", asientosDisponibles = 2, minutosLlegada = 4),
        Colectivo("GHJK-34", "Recorrido 1", asientosDisponibles = 3, minutosLlegada = 11),
        Colectivo("BCDF-21", "Recorrido 2", asientosDisponibles = 0, minutosLlegada = 5),
        Colectivo("LMNP-56", "Recorrido 2", asientosDisponibles = null, minutosLlegada = 8),
    )

    override val recorridos = tarifas.keys.toList()

    override fun colectivosCercanos(recorrido: String): Flow<List<Colectivo>> =
        flowOf(colectivos.filter { it.recorrido == recorrido })

    override suspend fun colectivo(patente: String) = colectivos.find { it.patente == patente }

    private val _reserva = MutableStateFlow<Reserva?>(null)
    override val reservaActiva: StateFlow<Reserva?> = _reserva

    override suspend fun reservar(patente: String) {
        _reserva.value = colectivo(patente)?.let { Reserva(it) }
    }

    override suspend fun cancelarReserva() {
        _reserva.value = null
    }

    override suspend fun datosPago(patente: String): DatosPago {
        val recorrido = colectivo(patente)?.recorrido ?: "Recorrido 1"
        return DatosPago(patente, recorrido, tarifas.getValue(recorrido))
    }

    // Para poder ver ambas pantallas: el primer intento se rechaza y el siguiente se aprueba.
    private var intentos = 0

    override suspend fun pagar(datos: DatosPago): Pago? {
        if (intentos++ % 2 == 0) return null
        val pago = Pago(
            operacion = "RP-" + Random.nextInt(100_000, 999_999),
            fecha = LocalDateTime.now(),
            patente = datos.patente,
            monto = datos.monto,
        )
        _historial.update { listOf(pago) + it }
        return pago
    }

    private val _historial = MutableStateFlow(
        listOf(
            pago("RP-482913", "2026-10-15T08:12", "ABCD-12", 800),
            pago("RP-482107", "2026-10-14T19:40", "GHJK-34", 800),
            pago("RP-481655", "2026-10-14T07:58", "ABCD-12", 800),
            pago("RP-480932", "2026-10-13T18:25", "LMNP-56", 900),
            pago("RP-479804", "2026-10-12T13:10", "BCDF-21", 800),
            pago("RP-478311", "2026-10-10T08:05", "ABCD-12", 800),
            pago("RP-477560", "2026-10-09T19:15", "GHJK-34", 800),
        )
    )
    override val historial: Flow<List<Pago>> = _historial

    override val notificaciones: Flow<List<Notificacion>> = flowOf(
        listOf(
            Notificacion(
                TipoNotificacion.RESERVA_CONFIRMADA, "Reserva confirmada",
                "Tienes 1 asiento reservado en ABCD-12 · Recorrido 1.", "08:06",
            ),
            Notificacion(
                TipoNotificacion.RETRASO, "Tu colectivo va con retraso",
                "El conductor de ABCD-12 informó un retraso.", "08:09",
            ),
            Notificacion(
                TipoNotificacion.DESVIO, "Desvío en el recorrido",
                "El conductor de GHJK-34 informó un desvío en el Recorrido 1.", "Ayer",
            ),
            Notificacion(
                TipoNotificacion.SIN_UBICACION, "Ubicación del colectivo no disponible temporalmente",
                "No estamos recibiendo la posición de LMNP-56. Se actualizará apenas vuelva la señal.", "Ayer",
            ),
        )
    )

    private fun pago(op: String, fecha: String, patente: String, monto: Int) =
        Pago(op, LocalDateTime.parse(fecha), patente, monto)
}
