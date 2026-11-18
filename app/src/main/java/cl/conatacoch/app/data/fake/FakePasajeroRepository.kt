package cl.conatacoch.app.data.fake

import cl.conatacoch.app.data.local.CatalogoRecorridos
import cl.conatacoch.app.domain.model.Colectivo
import cl.conatacoch.app.domain.model.DatosPago
import cl.conatacoch.app.domain.model.EstadoReserva
import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.Pago
import cl.conatacoch.app.domain.model.Recorrido
import cl.conatacoch.app.domain.model.Reserva
import cl.conatacoch.app.domain.model.TipoNotificacion
import cl.conatacoch.app.domain.model.puntoEn
import cl.conatacoch.app.domain.repository.MapaRepository
import cl.conatacoch.app.domain.repository.PasajeroRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import kotlin.random.Random

class FakePasajeroRepository(private val mapa: MapaRepository) : PasajeroRepository {

    override val recorridos: StateFlow<List<Recorrido>> = MutableStateFlow(CatalogoRecorridos.todos)

    /** El monto lo fija la administración; los que no aparecen usan la tarifa base. */
    private val tarifas = mapOf("Línea 5003" to 800, "Línea 5011" to 900)
    private fun tarifa(recorrido: String) = tarifas[recorrido] ?: 800

    private val colectivos: List<Colectivo> = listOf(
        Colectivo("ABCD-12", "Línea 5003", asientosDisponibles = 2, minutosLlegada = 4),
        Colectivo("GHJK-34", "Línea 5003", asientosDisponibles = 3, minutosLlegada = 11),
        Colectivo("BCDF-21", "Línea 5011", asientosDisponibles = 0, minutosLlegada = 5),
        Colectivo("LMNP-56", "Línea 5011", asientosDisponibles = null, minutosLlegada = 8),
    ) + generarColectivos()

    /** Dos colectivos de ejemplo para cada uno de los otros recorridos. */
    private fun generarColectivos(): List<Colectivo> {
        val letras = "BCDFGHJKLPRSTVWXYZ"
        return CatalogoRecorridos.todos.drop(2).flatMapIndexed { i, r ->
            listOf(0, 1).map { j ->
                val n = i * 2 + j
                val patente = "" + letras[n % letras.length] + letras[(n * 7 + 3) % letras.length] +
                    letras[(n * 5 + 1) % letras.length] + letras[(n * 3 + 2) % letras.length] + "-" + (10 + n * 3)
                Colectivo(patente, r.nombre, asientosDisponibles = listOf(1, 3, 0, null)[n % 4], minutosLlegada = 3 + n % 9)
            }
        }
    }

    /** Dónde va cada colectivo en el recorrido de ida (0 = inicio, 1 = fin). Simula el GPS. */
    private val avance: Map<String, Double> = colectivos.mapIndexed { i, c -> c.patente to (0.1 + (i * 0.37) % 0.8) }.toMap()

    override fun colectivosCercanos(recorrido: Recorrido): Flow<List<Colectivo>> = flow {
        val base = colectivos.filter { it.recorrido == recorrido.nombre }
        emit(base)
        // Sin trazado (sin internet o sin clave) se muestran igual en la lista, pero sin posición.
        val trazado = runCatching { mapa.trazado(recorrido) }.getOrNull() ?: return@flow
        var paso = 0
        while (true) {
            emit(base.map { c -> c.copy(posicion = puntoEn(trazado.ida, (avance.getValue(c.patente) + paso * 0.002) % 1.0)) })
            paso++
            delay(2_000)
        }
    }

    override suspend fun colectivo(patente: String) = colectivos.find { it.patente == patente }

    private val _reserva = MutableStateFlow<Reserva?>(null)
    override val reservaActiva: StateFlow<Reserva?> = _reserva

    private val _historialReservas = MutableStateFlow(
        listOf(
            reservaPasada("GHJK-34", "Línea 5003", "2026-10-14T19:32", EstadoReserva.COMPLETADA),
            reservaPasada("ABCD-12", "Línea 5003", "2026-10-14T07:50", EstadoReserva.COMPLETADA),
            reservaPasada("LMNP-56", "Línea 5011", "2026-10-13T18:15", EstadoReserva.EXPIRADA),
            reservaPasada("BCDF-21", "Línea 5011", "2026-10-12T13:02", EstadoReserva.CANCELADA),
        )
    )
    override val reservas: Flow<List<Reserva>> =
        combine(_reserva, _historialReservas) { activa, pasadas -> listOfNotNull(activa) + pasadas }

    override suspend fun reservar(patente: String) {
        _reserva.value = colectivo(patente)?.let { Reserva(it, LocalDateTime.now()) }
    }

    override suspend fun cancelarReserva() {
        val actual = _reserva.value ?: return
        _historialReservas.update { listOf(actual.copy(estado = EstadoReserva.CANCELADA)) + it }
        _reserva.value = null
    }

    private fun reservaPasada(patente: String, recorrido: String, fecha: String, estado: EstadoReserva) =
        Reserva(Colectivo(patente, recorrido, asientosDisponibles = null, minutosLlegada = 0), LocalDateTime.parse(fecha), estado)

    override suspend fun datosPago(patente: String): DatosPago {
        val recorrido = colectivo(patente)?.recorrido ?: "Línea 5003"
        return DatosPago(patente, recorrido, tarifa(recorrido))
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
                "Tienes 1 asiento reservado en ABCD-12 · Línea 5003.", "08:06",
            ),
            Notificacion(
                TipoNotificacion.RETRASO, "Tu colectivo va con retraso",
                "El conductor de ABCD-12 informó un retraso.", "08:09",
            ),
            Notificacion(
                TipoNotificacion.DESVIO, "Desvío en el recorrido",
                "El conductor de GHJK-34 informó un desvío en la Línea 5003.", "Ayer",
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
