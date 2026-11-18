package cl.conatacoch.app.data.remote

import cl.conatacoch.app.domain.model.Aviso
import cl.conatacoch.app.domain.model.EstadoServicio
import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.PasajeroEnEspera
import cl.conatacoch.app.domain.model.PerfilConductor
import cl.conatacoch.app.domain.model.Turno
import cl.conatacoch.app.domain.repository.ConductorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Conductor conectado al backend: turnos, servicio, panel de asientos (ocupados y reservas), avisos
 * y GPS se guardan en la base de datos. No hay cámaras: el conductor marca los asientos ocupados.
 */
class ApiConductorRepository(private val api: ApiCliente) : ConductorRepository {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _servicio = MutableStateFlow(EstadoServicio())
    override val servicio: StateFlow<EstadoServicio> = _servicio
    override val notificaciones: Flow<List<Notificacion>> = api.flujoNotificaciones()

    /** Revisa cada 5 s si llegaron reservas nuevas mientras está en servicio. */
    private var sondeo: Job? = null

    init {
        // Al cerrar sesión se olvida el servicio de la pantalla (en el servidor sigue abierto hasta finalizarlo).
        alcance.launch {
            api.token.collect { token ->
                if (token == null) {
                    sondeo?.cancel()
                    _servicio.value = EstadoServicio()
                }
            }
        }
    }

    override suspend fun perfil(): PerfilConductor {
        val o = JSONObject(api.get("/api/conductor/perfil"))
        val a = o.optJSONObject("asignacion")
        return PerfilConductor(o.getString("nombre"), a?.let { "Línea ${it.getString("linea")} · ${it.getString("patente")}" })
    }

    override suspend fun turnos(): List<Turno> = api.getArray("/api/conductor/turnos").mapear { o ->
        Turno(
            fecha = LocalDate.parse(o.getString("fecha")),
            recorrido = o.textoONulo("linea")?.let { "Línea $it" },
            patente = o.textoONulo("patente"),
            horario = o.textoONulo("horario"),
        )
    }

    override suspend fun actualizarServicio() {
        aplicar(JSONObject(api.get("/api/conductor/servicio")))
    }

    override suspend fun iniciarServicio() {
        aplicar(api.postObjeto("/api/conductor/servicio/iniciar", JSONObject()))
    }

    override suspend fun finalizarServicio() {
        api.post("/api/conductor/servicio/finalizar", JSONObject())
        aplicar(JSONObject().put("en_servicio", false))
    }

    // Sin sensor dedicado: se cambia a mano o con la velocidad que informa el GPS (enviarPosicion).
    override fun cambiarDetenido(detenido: Boolean) = _servicio.update { it.copy(detenido = detenido) }

    override suspend fun confirmarAbordaje(reservaId: Int) {
        aplicar(api.postObjeto("/api/conductor/abordaje", JSONObject().put("reserva_id", reservaId)))
    }

    override suspend fun cancelarReserva(reservaId: Int) {
        aplicar(api.postObjeto("/api/conductor/reservas/$reservaId/cancelar", JSONObject()))
    }

    override suspend fun enviarAviso(aviso: Aviso) {
        api.post("/api/conductor/avisos", JSONObject().put("codigo", aviso.codigo))
    }

    override suspend fun cambiarOcupados(ocupados: Int) {
        aplicar(api.postObjeto("/api/conductor/ocupacion", JSONObject().put("a_bordo", ocupados)))
    }

    override suspend fun enviarPosicion(lat: Double, lng: Double, velocidad: Float?, rumbo: Float?) {
        if (!_servicio.value.enServicio) return
        val cuerpo = JSONObject().put("lat", lat).put("lng", lng)
        velocidad?.let { cuerpo.put("velocidad", it.toDouble()) }
        rumbo?.let { cuerpo.put("rumbo", it.toDouble()) }
        api.post("/api/conductor/posicion", cuerpo)
        // Menos de ~5 km/h se considera detenido (habilita los avisos AC-01 a AC-05).
        if (velocidad != null) cambiarDetenido(velocidad < 1.5f)
    }

    /** Convierte la respuesta del servidor al estado que muestra la pantalla, conservando lo local. */
    private fun aplicar(o: JSONObject) {
        val anterior = _servicio.value
        if (!o.optBoolean("en_servicio")) {
            sondeo?.cancel()
            sondeo = null
            _servicio.value = EstadoServicio()
            return
        }
        val vigencia = o.optInt("minutos_vigencia", 3) * 60L
        val espera = o.getJSONArray("en_espera").mapear { e ->
            val transcurrido = Duration.between(fechaLocal(e.getString("creada_en")), LocalDateTime.now()).seconds
            PasajeroEnEspera(
                nombre = e.getString("nombre"),
                asientos = e.getInt("asientos"),
                enEsteParadero = true,
                minutosAlParadero = 0,
                // La reserva vence a los 3 minutos de hecha.
                segundosParaAbordar = (vigencia - transcurrido).coerceIn(0, vigencia).toInt(),
                reservaId = e.getInt("reserva_id"),
            )
        }
        _servicio.value = EstadoServicio(
            enServicio = true,
            detenido = anterior.detenido,
            patente = o.getString("patente"),
            recorrido = "Línea ${o.getString("linea")}",
            folio = o.getString("folio"),
            capacidad = o.getInt("capacidad"),
            aBordo = o.optInt("a_bordo"),
            reservados = o.optInt("reservados"),
            enEspera = espera,
            ultimoPago = null,
        )
        if (sondeo?.isActive != true) {
            sondeo = alcance.launch {
                while (true) {
                    delay(5_000)
                    runCatching { actualizarServicio() }
                }
            }
        }
    }
}
