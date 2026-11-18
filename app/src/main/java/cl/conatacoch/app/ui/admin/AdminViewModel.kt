package cl.conatacoch.app.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.conatacoch.app.domain.model.AutoAdmin
import cl.conatacoch.app.domain.model.ConductorAdmin
import cl.conatacoch.app.domain.model.DatosAutoAdmin
import cl.conatacoch.app.domain.model.DatosConductorAdmin
import cl.conatacoch.app.domain.model.EstadoConductor
import cl.conatacoch.app.domain.model.RecorridoAdmin
import cl.conatacoch.app.domain.model.ResumenAdmin
import cl.conatacoch.app.domain.model.TipoTurno
import cl.conatacoch.app.domain.model.TurnosConductor
import cl.conatacoch.app.domain.model.VehiculoEnVivo
import cl.conatacoch.app.domain.repository.AdminRepository
import cl.conatacoch.app.domain.repository.MapaRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Estado del panel de administración. Todo se lee y se guarda en la base de datos a través de [repo]. */
class AdminViewModel(
    private val repo: AdminRepository,
    private val mapa: MapaRepository,
) : ViewModel() {

    var resumen by mutableStateOf<ResumenAdmin?>(null)
        private set
    var conductores by mutableStateOf<List<ConductorAdmin>>(emptyList())
        private set
    var automoviles by mutableStateOf<List<AutoAdmin>>(emptyList())
        private set
    var recorridos by mutableStateOf<List<RecorridoAdmin>>(emptyList())
        private set
    var flota by mutableStateOf<List<VehiculoEnVivo>>(emptyList())
        private set
    var turnos by mutableStateOf<List<TurnosConductor>>(emptyList())
        private set

    /** true mientras se espera al servidor. */
    var cargando by mutableStateOf(false)
        private set
    /** Error para mostrar (sin conexión, RUT inválido, patente repetida...). */
    var error by mutableStateOf<String?>(null)
        private set
    /** Mensaje de éxito (por ejemplo, al recalcular un trazado). */
    var mensaje by mutableStateOf<String?>(null)
        private set
    /** Folio del recorrido cuyo trazado se está recalculando. */
    var recalculando by mutableStateOf<String?>(null)
        private set

    fun limpiarMensajes() {
        error = null
        mensaje = null
    }

    /** Ejecuta una llamada al servidor mostrando el error en pantalla en vez de cerrar la app. */
    private fun conServidor(accion: suspend () -> Unit) {
        error = null
        cargando = true
        viewModelScope.launch {
            try {
                accion()
            } catch (e: Exception) {
                error = e.message ?: "Ocurrió un error. Inténtalo de nuevo."
            } finally {
                cargando = false
            }
        }
    }

    fun ingresar(email: String, password: String, onListo: () -> Unit) = conServidor {
        val correcto = repo.login(email.trim(), password)
        if (!correcto) error = "Correo o contraseña incorrectos"
        if (correcto) onListo()
    }

    fun cerrarSesion() {
        repo.cerrarSesion()
        resumen = null
        conductores = emptyList()
        automoviles = emptyList()
        flota = emptyList()
    }

    fun cargarResumen() = conServidor { resumen = repo.resumen() }

    // --- Conductores ---

    fun cargarConductores() = conServidor { conductores = repo.conductores() }

    /** id null = crear. [onListo] se llama solo si se guardó (para cerrar el formulario). */
    fun guardarConductor(id: Int?, datos: DatosConductorAdmin, onListo: () -> Unit) = conServidor {
        repo.guardarConductor(id, datos)
        conductores = repo.conductores()
        onListo()
    }

    fun eliminarConductor(id: Int) = conServidor {
        repo.eliminarConductor(id)
        conductores = repo.conductores()
    }

    fun cambiarEstadoConductor(id: Int, estado: EstadoConductor) = conServidor {
        repo.cambiarEstadoConductor(id, estado)
        conductores = repo.conductores()
    }

    // --- Automóviles ---

    /** También trae recorridos y conductores, que el formulario ofrece para asignar. */
    fun cargarAutomoviles() = conServidor {
        automoviles = repo.automoviles()
        if (recorridos.isEmpty()) recorridos = repo.recorridos()
        conductores = repo.conductores()
    }

    fun guardarAuto(id: Int?, datos: DatosAutoAdmin, onListo: () -> Unit) = conServidor {
        repo.guardarAuto(id, datos)
        automoviles = repo.automoviles()
        onListo()
    }

    fun darDeBajaAuto(id: Int) = conServidor {
        repo.darDeBajaAuto(id)
        automoviles = repo.automoviles()
    }

    // --- Recorridos ---

    fun cargarRecorridos() = conServidor { recorridos = repo.recorridos() }

    /** Recalcula el trazado con OpenStreetMap y guarda la nueva versión en la base de datos. */
    fun recalcularTrazado(r: RecorridoAdmin) {
        if (recalculando != null) return
        limpiarMensajes()
        recalculando = r.recorrido.folio
        viewModelScope.launch {
            try {
                val trazado = mapa.recalcular(r.recorrido)
                val version = repo.guardarTrazado(r.recorrido.folio, trazado)
                mensaje = "Trazado de la ${r.recorrido.nombre} guardado (versión $version): " +
                    "${trazado.ida.size} puntos de ida y ${trazado.regreso.size} de regreso."
                recorridos = repo.recorridos()
            } catch (e: Exception) {
                error = e.message ?: "No se pudo recalcular el trazado."
            } finally {
                recalculando = null
            }
        }
    }

    // --- Turnos y asignación de conductores ---

    /** También trae los vehículos, que se ofrecen para asignar. */
    fun cargarTurnos() = conServidor {
        turnos = repo.turnos()
        automoviles = repo.automoviles()
    }

    /** Cambia el turno de un día y actualiza solo ese día en pantalla. */
    fun cambiarTurno(conductorId: Int, fecha: LocalDate, tipo: TipoTurno) = conServidor {
        repo.guardarTurno(conductorId, fecha, tipo)
        turnos = turnos.map { t ->
            if (t.conductorId != conductorId) t else t.copy(dias = t.dias.map { if (it.fecha == fecha) it.copy(tipo = tipo) else it })
        }
    }

    fun asignarVehiculo(conductorId: Int, automovilId: Int?) = conServidor {
        repo.asignarVehiculo(conductorId, automovilId)
        turnos = repo.turnos()
        automoviles = repo.automoviles()
    }

    // --- Flota en vivo ---

    /** Sin indicador de carga: se llama cada pocos segundos mientras la pantalla está abierta. */
    fun actualizarFlota() {
        viewModelScope.launch {
            try {
                flota = repo.flota()
                error = null
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
}
