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
import cl.conatacoch.app.domain.repository.PasajeroRepository
import cl.conatacoch.app.domain.repository.PreferenciasRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PasajeroViewModel(
    private val repo: PasajeroRepository,
    private val prefs: PreferenciasRepository,
) : ViewModel() {

    val recorridos = repo.recorridos
    val recorrido = MutableStateFlow(recorridos.first())

    @OptIn(ExperimentalCoroutinesApi::class)
    val colectivos: StateFlow<List<Colectivo>> = recorrido
        .flatMapLatest { repo.colectivosCercanos(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reserva = repo.reservaActiva
    val reservas = repo.reservas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val historial = repo.historial.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notificaciones = repo.notificaciones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val preferencias: StateFlow<Preferencias> = prefs.preferencias

    /** Datos leídos del QR; null mientras no se escanea. */
    var datosPago by mutableStateOf<DatosPago?>(null)
        private set
    var ultimoPago by mutableStateOf<Pago?>(null)
        private set

    fun colectivo(patente: String): Colectivo? = colectivos.value.find { it.patente == patente }

    fun reservar(patente: String, onListo: () -> Unit) {
        viewModelScope.launch {
            repo.reservar(patente)
            onListo()
        }
    }

    fun cancelarReserva(onListo: () -> Unit) {
        viewModelScope.launch {
            repo.cancelarReserva()
            onListo()
        }
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
