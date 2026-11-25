package cl.conatacoch.app.ui.pasajero

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.conatacoch.app.domain.model.Colectivo
import cl.conatacoch.app.domain.model.DatosPago
import cl.conatacoch.app.domain.model.Pago
import cl.conatacoch.app.domain.model.Preferencias
import cl.conatacoch.app.domain.model.Recorrido
import cl.conatacoch.app.domain.model.Trazado
import cl.conatacoch.app.domain.repository.MapaRepository
import cl.conatacoch.app.domain.repository.PasajeroRepository
import cl.conatacoch.app.domain.repository.PreferenciasRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la línea del recorrido sobre el mapa. */
sealed interface EstadoTrazado {
    data object Cargando : EstadoTrazado
    data class Listo(val trazado: Trazado) : EstadoTrazado
    data class Error(val mensaje: String) : EstadoTrazado
}

class PasajeroViewModel(
    private val repo: PasajeroRepository,
    private val prefs: PreferenciasRepository,
    private val mapa: MapaRepository,
) : ViewModel() {

    /** Recorridos oficiales (desde la base de datos si hay servidor). */
    val recorridos: StateFlow<List<Recorrido>> = repo.recorridos
    val recorrido = MutableStateFlow(recorridos.value.first())

    /** Error al reservar o cancelar (sin asientos, sin conexión, etc.). null = sin error. */
    var errorReserva by mutableStateOf<String?>(null)
        private set

    private val reintentos = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val trazado: StateFlow<EstadoTrazado> = combine(recorrido, reintentos) { r, _ -> r }
        .flatMapLatest { r ->
            flow<EstadoTrazado> {
                emit(EstadoTrazado.Cargando)
                emit(
                    runCatching { mapa.trazado(r) }.fold(
                        { EstadoTrazado.Listo(it) },
                        { EstadoTrazado.Error(it.message ?: "No se pudo cargar el recorrido") },
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoTrazado.Cargando)

    fun reintentarTrazado() {
        reintentos.value++
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val colectivos: StateFlow<List<Colectivo>> = recorrido
        .flatMapLatest { repo.colectivosCercanos(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reserva = repo.reservaActiva
    val reservas = repo.reservas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val historial = repo.historial.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notificaciones = combine(repo.notificaciones, prefs.preferencias) { lista, p -> lista.filter { p.permite(it.tipo) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val preferencias: StateFlow<Preferencias> = prefs.preferencias

    /** Datos leídos del QR; null mientras no se escanea. */
    var datosPago by mutableStateOf<DatosPago?>(null)
        private set
    var ultimoPago by mutableStateOf<Pago?>(null)
        private set

    fun colectivo(patente: String): Colectivo? = colectivos.value.find { it.patente == patente }

    fun reservar(patente: String, onListo: () -> Unit) {
        errorReserva = null
        viewModelScope.launch {
            try {
                repo.reservar(patente)
                onListo()
            } catch (e: Exception) {
                errorReserva = e.message ?: "No se pudo reservar. Inténtalo de nuevo."
            }
        }
    }

    fun cancelarReserva(onListo: () -> Unit) {
        errorReserva = null
        viewModelScope.launch {
            try {
                repo.cancelarReserva()
                onListo()
            } catch (e: Exception) {
                errorReserva = e.message ?: "No se pudo cancelar. Inténtalo de nuevo."
            }
        }
    }

    fun limpiarErrorReserva() {
        errorReserva = null
    }

    /** Simula la lectura del QR impreso en el colectivo (aún sin cámara ni lector). */
    fun escanearQr() {
        viewModelScope.launch { datosPago = repo.datosPago("ABCD-12") }
    }

    fun pagar(onResultado: (aprobado: Boolean) -> Unit) {
        val datos = datosPago ?: return
        viewModelScope.launch {
            ultimoPago = repo.pagar(datos)
            onResultado(ultimoPago != null)
        }
    }

    fun terminarPago() {
        datosPago = null
    }

    fun actualizarPreferencias(cambio: (Preferencias) -> Preferencias) = prefs.actualizar(cambio)
}
