package cl.conatacoch.app.domain.model

import java.time.LocalDateTime

/** Semáforo de vencimientos de licencias y documentos. */
enum class Semaforo { VIGENTE, POR_VENCER, VENCIDO, SIN_DATOS }

/** Estado de la cuenta de un conductor según la administración. */
enum class EstadoConductor { PENDIENTE, APROBADA, RECHAZADA }

data class ConductorAdmin(
    val id: Int,
    val nombre: String,
    val rut: String,
    val direccion: String,
    val comuna: String,
    val celular: String,
    val licencia: String,
    val municipio: String,
    /** Fechas en formato aaaa-mm-dd. */
    val obtencion: String,
    val vencimiento: String,
    val estado: EstadoConductor,
    /** Correo de su cuenta; null = no puede iniciar sesión en la app. */
    val email: String?,
    /** Patente del vehículo que maneja, si tiene. */
    val patente: String?,
    val semaforo: Semaforo,
)

/** Datos del formulario de conductor (crear o modificar). Fechas en dd-mm-aaaa. */
data class DatosConductorAdmin(
    val nombre: String = "",
    val rut: String = "",
    val direccion: String = "",
    val comuna: String = "",
    val celular: String = "",
    val licencia: String = "A2",
    val municipio: String = "",
    val obtencion: String = "",
    val vencimiento: String = "",
    /** Solo al crear: cuenta para iniciar sesión (opcional). */
    val email: String = "",
    val password: String = "",
)

data class AutoAdmin(
    val id: Int,
    val patente: String,
    val marca: String,
    val modelo: String,
    val ano: Int,
    val combustible: String,
    val capacidad: Int,
    val rutaFolio: String?,
    val linea: String?,
    val conductor: String?,
    val conductorRut: String?,
    /** Fechas en formato aaaa-mm-dd; null = sin registrar. */
    val vencimientoPermiso: String?,
    val vencimientoRevision: String?,
    val vencimientoSeguro: String?,
    val semaforo: Semaforo,
    val enServicio: Boolean,
)

/** Datos del formulario de vehículo. Fechas en dd-mm-aaaa; vacío = no cambiar. */
data class DatosAutoAdmin(
    val patente: String = "",
    val marca: String = "",
    val modelo: String = "",
    val ano: String = "",
    val combustible: String = "bencina",
    val capacidad: String = "4",
    val rutaFolio: String = "",
    val conductorRut: String = "",
    val vencimientoPermiso: String = "",
    val vencimientoRevision: String = "",
    val vencimientoSeguro: String = "",
)

data class ConteoSemaforo(val total: Int, val vigentes: Int, val porVencer: Int, val vencidos: Int, val otros: Int)

data class AvisoRegistrado(
    val codigo: String,
    val titulo: String,
    val fecha: LocalDateTime,
    val patente: String?,
    val linea: String?,
    val conductor: String?,
)

data class ResumenAdmin(
    /** otros = pendientes de validación. */
    val conductores: ConteoSemaforo,
    /** vigentes = al día; otros = sin documentos. */
    val vehiculos: ConteoSemaforo,
    val vehiculosAsignados: Int,
    val rutas: Int,
    val enServicio: Int,
    val reservasActivas: Int,
    val pasajeros: Int,
    val avisosRecientes: List<AvisoRegistrado>,
)

data class RecorridoAdmin(
    val recorrido: Recorrido,
    val tarifa: Int,
    val flotaVigente: Int,
    val vehiculosAsignados: Int,
    /** Versión del trazado guardado en la base de datos; null = aún no se guarda. */
    val versionTrazado: Int?,
)

data class VehiculoEnVivo(
    val patente: String,
    val linea: String,
    val conductor: String?,
    val enServicio: Boolean,
    val posicion: Punto?,
    /** m/s, según el GPS del teléfono del conductor. */
    val velocidad: Double?,
    val actualizado: LocalDateTime?,
)

data class PerfilConductor(
    val nombre: String,
    /** "Línea 5003 · ABCD-12"; null = sin vehículo asignado. */
    val asignacion: String?,
)

/** Turno de un día: mañana (06:00–14:00), tarde (14:00–22:00) o libre. */
enum class TipoTurno(val texto: String, val horario: String?) {
    MANANA("Mañana", "06:00–14:00"),
    TARDE("Tarde", "14:00–22:00"),
    LIBRE("Libre", null),
}

data class DiaTurno(val fecha: java.time.LocalDate, val tipo: TipoTurno)

/** Un conductor aprobado en el panel de turnos: su vehículo asignado y su calendario. */
data class TurnosConductor(
    val conductorId: Int,
    val nombre: String,
    val rut: String,
    val automovilId: Int?,
    /** "Línea 5003 · ABCD-12"; null = sin vehículo. */
    val asignacion: String?,
    val dias: List<DiaTurno>,
)
