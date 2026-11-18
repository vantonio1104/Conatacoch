package cl.conatacoch.app.ui.conductor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.conatacoch.app.domain.model.Aviso
import cl.conatacoch.app.domain.model.Recorrido
import cl.conatacoch.app.domain.model.Turno
import cl.conatacoch.app.domain.repository.ConductorRepository
import cl.conatacoch.app.domain.repository.MapaRepository
import cl.conatacoch.app.domain.repository.PreferenciasRepository
import cl.conatacoch.app.ui.pasajero.EstadoTrazado
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConductorViewModel(
    private val repo: ConductorRepository,
    private val prefs: PreferenciasRepository,
    private val mapa: MapaRepository,
    private val recorridos: StateFlow<List<Recorrido>>,
) : ViewModel() {

    val servicio = repo.servicio

    private val reintentos = MutableStateFlow(0)

    /** Línea del recorrido asignado, para el mapa que ve el conductor mientras maneja. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trazado: StateFlow<EstadoTrazado> = combine(servicio.map { it.folio }.distinctUntilChanged(), reintentos) { f, _ -> f }
        .flatMapLatest { folio ->
            flow<EstadoTrazado> {
                val r = recorridos.value.find { it.folio == folio }
                if (r == null) {
                    emit(EstadoTrazado.Error("Sin recorrido asignado"))
                } else {
                    emit(EstadoTrazado.Cargando)
                    emit(
                        runCatching { mapa.trazado(r) }.fold(
                            { EstadoTrazado.Listo(it) },
                            { EstadoTrazado.Error(it.message ?: "No se pudo cargar el recorrido") },
                        )
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoTrazado.Cargando)

    fun reintentarTrazado() {
        reintentos.value++
    }
    val preferencias = prefs.preferencias
    val notificaciones = repo.notificaciones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var nombre by mutableStateOf("")
        private set
    /** "Línea 5003 · ABCD-12"; null = sin vehículo asignado. */
    var asignacion by mutableStateOf<String?>(null)
        private set
    var turnos by mutableStateOf<List<Turno>>(emptyList())
        private set

    /** Error del servidor para mostrar (sin vehículo asignado, sin conexión...). */
    var error by mutableStateOf<String?>(null)
        private set

    fun limpiarError() {
        error = null
    }

    /** Ejecuta una llamada al servidor mostrando el error en pantalla en vez de cerrar la app. */
    private fun conServidor(accion: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                accion()
            } catch (e: Exception) {
                error = e.message ?: "Ocurrió un error. Inténtalo de nuevo."
            }
        }
    }

    /** Se llama al entrar a las pantallas del conductor (después de iniciar sesión). */
    fun cargar() = conServidor {
        val p = repo.perfil()
        nombre = p.nombre
        asignacion = p.asignacion
        turnos = repo.turnos()
        repo.actualizarServicio()
    }

    fun iniciarServicio() = conServidor { repo.iniciarServicio() }

    fun finalizarServicio() = conServidor { repo.finalizarServicio() }

    fun cambiarDetenido(detenido: Boolean) = repo.cambiarDetenido(detenido)

    // --- Panel de asientos ---

    fun confirmarAbordaje(reservaId: Int) = conServidor { repo.confirmarAbordaje(reservaId) }

    fun eliminarReserva(reservaId: Int) = conServidor { repo.cancelarReserva(reservaId) }

    /** Subió un pasajero sin reserva (se toca un asiento libre). */
    fun ocuparAsiento() = conServidor {
        val s = servicio.value
        if (s.libres > 0) repo.cambiarOcupados(s.aBordo + 1)
    }

    /** Bajó un pasajero (se toca un asiento ocupado). */
    fun liberarAsiento() = conServidor {
        val s = servicio.value
        if (s.aBordo > 0) repo.cambiarOcupados(s.aBordo - 1)
    }

    /** GPS del teléfono: si falla un envío no se muestra error (llega otra posición en segundos). */
    fun enviarPosicion(lat: Double, lng: Double, velocidad: Float?, rumbo: Float?) {
        viewModelScope.launch { runCatching { repo.enviarPosicion(lat, lng, velocidad, rumbo) } }
    }

    /** Ignora el toque si el aviso no está habilitado (vehículo en movimiento). */
    fun enviarAviso(aviso: Aviso, onEnviado: () -> Unit) {
        if (!aviso.habilitado(servicio.value.detenido)) return
        conServidor {
            repo.enviarAviso(aviso)
            onEnviado()
        }
    }

    fun modoOscuro(activo: Boolean) = prefs.actualizar { it.copy(modoOscuro = activo) }
}
