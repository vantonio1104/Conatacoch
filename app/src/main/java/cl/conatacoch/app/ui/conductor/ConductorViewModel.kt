package cl.conatacoch.app.ui.conductor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.conatacoch.app.domain.model.Aviso
import cl.conatacoch.app.domain.model.Turno
import cl.conatacoch.app.domain.repository.ConductorRepository
import cl.conatacoch.app.domain.repository.PreferenciasRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConductorViewModel(
    private val repo: ConductorRepository,
    private val prefs: PreferenciasRepository,
) : ViewModel() {

    val nombre = repo.nombre
    val servicio = repo.servicio
    val preferencias = prefs.preferencias
    val notificaciones = repo.notificaciones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var turnos by mutableStateOf<List<Turno>>(emptyList())
        private set

    init {
        viewModelScope.launch { turnos = repo.turnos() }
    }

    fun iniciarServicio() {
        viewModelScope.launch { repo.iniciarServicio() }
    }

    fun finalizarServicio() {
        viewModelScope.launch { repo.finalizarServicio() }
    }

    fun cambiarDetenido(detenido: Boolean) = repo.cambiarDetenido(detenido)

    fun confirmarAbordaje(nombre: String) {
        viewModelScope.launch { repo.confirmarAbordaje(nombre) }
    }

    /** Ignora el toque si el aviso no está habilitado (vehículo en movimiento). */
    fun enviarAviso(aviso: Aviso, onEnviado: () -> Unit) {
        if (!aviso.habilitado(servicio.value.detenido)) return
        viewModelScope.launch {
            repo.enviarAviso(aviso)
            onEnviado()
        }
    }

    fun guardarCorreccion(aBordo: Int, onListo: () -> Unit) {
        viewModelScope.launch {
            repo.corregirOcupacion(aBordo)
            onListo()
        }
    }

    fun modoOscuro(activo: Boolean) = prefs.actualizar { it.copy(modoOscuro = activo) }
}
