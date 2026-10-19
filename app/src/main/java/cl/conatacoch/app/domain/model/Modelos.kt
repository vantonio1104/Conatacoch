package cl.conatacoch.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class EstadoOcupacion { CON_ASIENTOS, LLENO, SIN_DATO }

data class Colectivo(
    val patente: String,
    val recorrido: String,
    /** null = las cámaras no informan. */
    val asientosDisponibles: Int?,
    val minutosLlegada: Int,
) {
    val estado: EstadoOcupacion
        get() = when {
            asientosDisponibles == null -> EstadoOcupacion.SIN_DATO
            asientosDisponibles == 0 -> EstadoOcupacion.LLENO
            else -> EstadoOcupacion.CON_ASIENTOS
        }
}

enum class EstadoReserva { ACTIVA, COMPLETADA, EXPIRADA, CANCELADA }

/** La reserva vence 3 minutos después de que el colectivo llega al paradero. */
data class Reserva(
    val colectivo: Colectivo,
    val fecha: LocalDateTime,
    val estado: EstadoReserva = EstadoReserva.ACTIVA,
) {
    companion object { const val MINUTOS_PARA_ABORDAR = 3 }
}

/** El monto lo fija la administración por recorrido; el pasajero no lo edita. */
data class DatosPago(val patente: String, val recorrido: String, val monto: Int)

data class Pago(
    val operacion: String,
    val fecha: LocalDateTime,
    val patente: String,
    val monto: Int,
    val medio: String = "Rutpay",
)

enum class TipoNotificacion {
    RESERVA_CONFIRMADA, RETRASO, DESVIO, SIN_UBICACION,
    NUEVA_RESERVA, RESERVA_CANCELADA, NUEVA_ASIGNACION,
}

data class Notificacion(
    val tipo: TipoNotificacion,
    val titulo: String,
    val detalle: String,
    /** Texto ya formateado: "08:06" o "Ayer". */
    val cuando: String,
)

data class Preferencias(
    /** null = sigue al sistema. */
    val modoOscuro: Boolean? = null,
    val llegada: Boolean = true,
    val minutosAnticipacion: Int = 5,
    val reservaConfirmada: Boolean = true,
    val retrasos: Boolean = true,
    val desvios: Boolean = true,
    val sinUbicacion: Boolean = false,
) {
    companion object { val OPCIONES_ANTICIPACION = listOf(2, 5, 10) }
}

// --- Conductor ---

enum class ClaseLicencia { A2, A3 }

enum class EstadoCuenta { PENDIENTE, APROBADA }

data class RegistroConductor(
    val rut: String,
    val nombre: String,
    val direccion: String,
    val comuna: String,
    val celular: String,
    val telefonoFijo: String,
    val telefonoEmergencia: String,
    val correo: String,
    val password: String,
    val licencia: ClaseLicencia,
    val emisionLicencia: String,
    val vencimientoLicencia: String,
    val comunaEmision: String,
)

/** Un día del calendario. recorrido == null es un día libre. */
data class Turno(
    val fecha: LocalDate,
    val recorrido: String?,
    val patente: String?,
    val horario: String?,
)

data class PasajeroEnEspera(
    val nombre: String,
    val asientos: Int,
    /** true = está en el paradero actual y puede abordar. */
    val enEsteParadero: Boolean,
    val minutosAlParadero: Int,
    /** Segundos que quedan de los 3 minutos para abordar. */
    val segundosParaAbordar: Int,
)

enum class Aviso(val codigo: String, val titulo: String) {
    AC01("AC-01", "Voy con retraso"),
    AC02("AC-02", "Desvío"),
    AC03("AC-03", "Desperfecto"),
    AC04("AC-04", "Falla de cámara"),
    AC05("AC-05", "QR dañado"),
    AC06("AC-06", "Emergencia");

    /** AC-06 siempre está habilitado; los demás solo con el vehículo detenido. */
    fun habilitado(detenido: Boolean): Boolean = this == AC06 || detenido
}

data class EstadoServicio(
    val enServicio: Boolean = false,
    val detenido: Boolean = true,
    val patente: String = "",
    val recorrido: String = "",
    val capacidad: Int = 4,
    /** Pasajeros a bordo según cámaras (o según la corrección manual). */
    val aBordo: Int = 0,
    val reservados: Int = 0,
    val enEspera: List<PasajeroEnEspera> = emptyList(),
    val ultimoPago: Pago? = null,
    /** Solo true después de enviar AC-04. */
    val correccionHabilitada: Boolean = false,
) {
    val libres: Int get() = (capacidad - aBordo - reservados).coerceAtLeast(0)
}
