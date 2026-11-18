package cl.conatacoch.app.data.remote

import cl.conatacoch.app.data.fake.FakePasajeroRepository
import cl.conatacoch.app.data.local.CatalogoRecorridos
import cl.conatacoch.app.domain.model.Calle
import cl.conatacoch.app.domain.model.Colectivo
import cl.conatacoch.app.domain.model.DatosPago
import cl.conatacoch.app.domain.model.EstadoReserva
import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.Punto
import cl.conatacoch.app.domain.model.Recorrido
import cl.conatacoch.app.domain.model.Reserva
import cl.conatacoch.app.domain.model.puntoEn
import cl.conatacoch.app.domain.repository.MapaRepository
import cl.conatacoch.app.domain.repository.PasajeroRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Pasajero conectado al backend: recorridos, colectivos y reservas vienen de la base de datos.
 * Los pagos siguen siendo de ejemplo ([demo]) hasta que se conecte Rutpay.
 */
class ApiPasajeroRepository(
    private val api: ApiCliente,
    private val mapa: MapaRepository,
    private val demo: FakePasajeroRepository,
) : PasajeroRepository by demo {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Mientras llega la respuesta del servidor se usa el catálogo incluido en la app (mismos datos).
    private val _recorridos = MutableStateFlow(CatalogoRecorridos.todos)
    override val recorridos: StateFlow<List<Recorrido>> = _recorridos
    private val tarifas = ConcurrentHashMap<String, Int>()

    /** Últimos colectivos recibidos, para el detalle y la reserva. */
    private val ultimos = ConcurrentHashMap<String, Colectivo>()

    private val _reserva = MutableStateFlow<Reserva?>(null)
    override val reservaActiva: StateFlow<Reserva?> = _reserva
    private val _reservas = MutableStateFlow<List<Reserva>>(emptyList())
    override val reservas: Flow<List<Reserva>> = _reservas

    /** Reserva confirmada, retrasos y desvíos que informan los conductores (MongoDB). */
    override val notificaciones: Flow<List<Notificacion>> = api.flujoNotificaciones()

    init {
        alcance.launch { runCatching { cargarRecorridos() } }
        // Al iniciar o cerrar sesión se cargan (o se borran) las reservas de esa cuenta.
        alcance.launch {
            api.token.collect { token ->
                if (token == null) {
                    _reserva.value = null
                    _reservas.value = emptyList()
                } else {
                    runCatching { actualizarReservas() }
                }
            }
        }
        // La reserva activa puede expirar (3 min) o el conductor puede eliminarla: se revisa cada 10 s.
        alcance.launch {
            while (true) {
                delay(10_000)
                if (api.token.value != null && _reserva.value != null) runCatching { actualizarReservas() }
            }
        }
    }

    private suspend fun cargarRecorridos() {
        val arr = api.getArray("/api/rutas")
        val lista = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val r = Recorrido(
                folio = o.getString("folio"),
                linea = o.getString("linea"),
                operador = o.optString("operador"),
                origen = o.getString("origen_comuna"),
                destino = o.getString("destino_comuna"),
                ida = calles(o.getJSONArray("ida")),
                regreso = calles(o.getJSONArray("regreso")),
            )
            tarifas[r.nombre] = o.optInt("tarifa", TARIFA_BASE)
            r
        }
        if (lista.isNotEmpty()) _recorridos.value = lista
    }

    private fun calles(a: JSONArray) = List(a.length()) { a.getJSONObject(it).let { c -> Calle(c.getString("calle"), c.getString("comuna")) } }

    /**
     * Pide los colectivos al servidor cada 10 s. Si un vehículo no envía GPS todavía, su posición y su
     * tiempo de llegada se simulan sobre el recorrido (igual que en el prototipo) para que el mapa se vea vivo.
     */
    override fun colectivosCercanos(recorrido: Recorrido): Flow<List<Colectivo>> = flow {
        val trazado = runCatching { mapa.trazado(recorrido) }.getOrNull()
        var datos: List<Colectivo> = emptyList()
        var paso = 0
        while (true) {
            if (paso % 5 == 0) {
                datos = runCatching { pedirColectivos(recorrido) }.getOrDefault(datos)
            }
            val visibles = datos.map { c ->
                if (c.posicion != null || trazado == null) {
                    c
                } else {
                    val inicio = (abs(c.patente.hashCode()) % 80) / 100.0 + 0.1
                    c.copy(posicion = puntoEn(trazado.ida, (inicio + paso * 0.002) % 1.0))
                }
            }
            visibles.forEach { ultimos[it.patente] = it }
            emit(visibles)
            paso++
            delay(2_000)
        }
    }

    private suspend fun pedirColectivos(recorrido: Recorrido): List<Colectivo> {
        // Solo se muestran los colectivos en servicio (son los únicos que se pueden reservar).
        val arr = api.getArray("/api/rutas/${recorrido.folio}/colectivos")
        return List(arr.length()) { arr.getJSONObject(it) }.filter { it.optBoolean("en_servicio") }.map { o ->
            val patente = o.getString("patente")
            Colectivo(
                patente = patente,
                recorrido = "Línea ${o.getString("linea")}",
                asientosDisponibles = if (o.isNull("asientos_disponibles")) null else o.getInt("asientos_disponibles"),
                // ETA simulado hasta que el servidor lo calcule con el GPS.
                minutosLlegada = 3 + abs(patente.hashCode()) % 9,
                posicion = o.optJSONObject("posicion")?.let { p -> Punto(p.getDouble("lat"), p.getDouble("lng")) },
            )
        }
    }

    override suspend fun colectivo(patente: String): Colectivo? = ultimos[patente]

    override suspend fun reservar(patente: String) {
        api.post("/api/reservas", JSONObject().put("patente", patente))
        actualizarReservas()
    }

    override suspend fun cancelarReserva() {
        api.delete("/api/reservas/activa")
        actualizarReservas()
    }

    private suspend fun actualizarReservas() {
        val arr = api.getArray("/api/reservas")
        val lista = List(arr.length()) { reserva(arr.getJSONObject(it)) }
        _reservas.value = lista
        _reserva.value = lista.firstOrNull { it.estado == EstadoReserva.ACTIVA }
    }

    private fun reserva(o: JSONObject): Reserva {
        val patente = o.getString("patente")
        val colectivo = ultimos[patente]
            ?: Colectivo(patente, "Línea ${o.getString("linea")}", asientosDisponibles = null, minutosLlegada = 3 + abs(patente.hashCode()) % 9)
        val fecha = LocalDateTime.ofInstant(Instant.parse(o.getString("creada_en")), ZoneId.systemDefault())
        return Reserva(colectivo, fecha, EstadoReserva.valueOf(o.getString("estado")))
    }

    override suspend fun datosPago(patente: String): DatosPago {
        val recorrido = ultimos[patente]?.recorrido ?: return demo.datosPago(patente)
        return DatosPago(patente, recorrido, tarifas[recorrido] ?: TARIFA_BASE)
    }

    private companion object {
        const val TARIFA_BASE = 800
    }
}
