package cl.conatacoch.app.domain.repository

import cl.conatacoch.app.domain.model.AutoAdmin
import cl.conatacoch.app.domain.model.Aviso
import cl.conatacoch.app.domain.model.Colectivo
import cl.conatacoch.app.domain.model.ConductorAdmin
import cl.conatacoch.app.domain.model.DatosAutoAdmin
import cl.conatacoch.app.domain.model.DatosConductorAdmin
import cl.conatacoch.app.domain.model.DatosPago
import cl.conatacoch.app.domain.model.EstadoConductor
import cl.conatacoch.app.domain.model.EstadoCuenta
import cl.conatacoch.app.domain.model.EstadoServicio
import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.Pago
import cl.conatacoch.app.domain.model.PerfilConductor
import cl.conatacoch.app.domain.model.Preferencias
import cl.conatacoch.app.domain.model.Recorrido
import cl.conatacoch.app.domain.model.RecorridoAdmin
import cl.conatacoch.app.domain.model.RegistroConductor
import cl.conatacoch.app.domain.model.Reserva
import cl.conatacoch.app.domain.model.ResumenAdmin
import cl.conatacoch.app.domain.model.Trazado
import cl.conatacoch.app.domain.model.TipoTurno
import cl.conatacoch.app.domain.model.Turno
import cl.conatacoch.app.domain.model.TurnosConductor
import cl.conatacoch.app.domain.model.VehiculoEnVivo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

// Contratos entre la UI y los datos. Los implementan data/fake (datos de ejemplo)
// y data/remote (servidor con PostgreSQL + MongoDB). Se elige en AppContainer.
// Las funciones que van al servidor pueden lanzar ErrorApi con un mensaje para el usuario.

interface AuthRepository {
    /** Crea la cuenta y deja la sesión iniciada (sin código de verificación por correo). */
    suspend fun registrarPasajero(nombre: String, correo: String, password: String)
    /** Devuelve el nombre del pasajero o null si el correo o la contraseña no coinciden. */
    suspend fun loginPasajero(correo: String, password: String): String?

    suspend fun registrarConductor(datos: RegistroConductor)
    /** null si el correo o la contraseña no coinciden. */
    suspend fun loginConductor(correo: String, password: String): EstadoCuenta?

    /** Olvida la sesión iniciada (token). */
    fun cerrarSesion() {}
}

/** Convierte las calles de un recorrido en líneas sobre el mapa. */
interface MapaRepository {
    /** Lanza una excepción si no se puede calcular (sin internet, etc.). */
    suspend fun trazado(recorrido: Recorrido): Trazado

    /** Vuelve a calcular el trazado desde las calles, sin usar lo guardado. */
    suspend fun recalcular(recorrido: Recorrido): Trazado
}

interface PasajeroRepository {
    /** Recorridos oficiales; con el servidor se actualizan desde la base de datos. */
    val recorridos: StateFlow<List<Recorrido>>
    fun colectivosCercanos(recorrido: Recorrido): Flow<List<Colectivo>>
    suspend fun colectivo(patente: String): Colectivo?
    val reservaActiva: StateFlow<Reserva?>
    /** La activa (si hay) primero y luego el historial, del más reciente al más antiguo. */
    val reservas: Flow<List<Reserva>>
    suspend fun reservar(patente: String)
    suspend fun cancelarReserva()
    /** Datos que trae el QR del vehículo más la tarifa del recorrido. */
    suspend fun datosPago(patente: String): DatosPago
    /** null = pago rechazado. */
    suspend fun pagar(datos: DatosPago): Pago?
    val historial: Flow<List<Pago>>
    val notificaciones: Flow<List<Notificacion>>
}

/** Administración: todo lo que se guarda en la base de datos desde el panel. */
interface AdminRepository {
    /** false si el correo o la contraseña no son de un administrador. */
    suspend fun login(email: String, password: String): Boolean
    fun cerrarSesion()
    suspend fun resumen(): ResumenAdmin
    suspend fun conductores(): List<ConductorAdmin>
    /** id null = crear. */
    suspend fun guardarConductor(id: Int?, datos: DatosConductorAdmin)
    suspend fun eliminarConductor(id: Int)
    suspend fun cambiarEstadoConductor(id: Int, estado: EstadoConductor)
    suspend fun automoviles(): List<AutoAdmin>
    /** id null = crear. */
    suspend fun guardarAuto(id: Int?, datos: DatosAutoAdmin)
    suspend fun darDeBajaAuto(id: Int)
    suspend fun recorridos(): List<RecorridoAdmin>
    /** Guarda una nueva versión del trazado y devuelve su número. */
    suspend fun guardarTrazado(folio: String, trazado: Trazado): Int
    suspend fun flota(): List<VehiculoEnVivo>
    /** Conductores aprobados con su vehículo y sus turnos de los próximos [dias] días. */
    suspend fun turnos(dias: Int = 14): List<TurnosConductor>
    suspend fun guardarTurno(conductorId: Int, fecha: LocalDate, tipo: TipoTurno)
    /** automovilId null = dejar al conductor sin vehículo. */
    suspend fun asignarVehiculo(conductorId: Int, automovilId: Int?)
}

interface PreferenciasRepository {
    val preferencias: StateFlow<Preferencias>
    fun actualizar(cambio: (Preferencias) -> Preferencias)
}

interface ConductorRepository {
    /** Nombre y vehículo asignado del conductor con sesión iniciada. */
    suspend fun perfil(): PerfilConductor
    /** El primero es el turno de hoy. */
    suspend fun turnos(): List<Turno>
    /** Vuelve a leer el estado del servicio (por ejemplo, al iniciar sesión con un servicio abierto). */
    suspend fun actualizarServicio() {}
    /** Posición del GPS del teléfono mientras está en servicio. */
    suspend fun enviarPosicion(lat: Double, lng: Double, velocidad: Float?, rumbo: Float?) {}
    val servicio: StateFlow<EstadoServicio>
    suspend fun iniciarServicio()
    suspend fun finalizarServicio()
    /** En el prototipo se simula; luego vendrá del GPS. */
    fun cambiarDetenido(detenido: Boolean)
    /** El pasajero con reserva subió: su asiento queda ocupado. */
    suspend fun confirmarAbordaje(reservaId: Int)
    /** El conductor elimina una reserva de su vehículo. */
    suspend fun cancelarReserva(reservaId: Int)
    suspend fun enviarAviso(aviso: Aviso)
    /** Asientos ocupados que marca el conductor (sube o baja un pasajero sin reserva). */
    suspend fun cambiarOcupados(ocupados: Int)
    val notificaciones: Flow<List<Notificacion>>
}
