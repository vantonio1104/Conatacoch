package cl.conatacoch.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class EstadoOcupacion { CON_ASIENTOS, LLENO, SIN_DATO }

/** Coordenada geográfica (sin depender de clases de Google Maps). */
data class Punto(val lat: Double, val lng: Double)

data class Calle(val nombre: String, val comuna: String)

/** Recorrido oficial de CONATACOCH: calles en orden para la ida y el regreso. */
data class Recorrido(
    val folio: String,
    val linea: String,
    val operador: String,
    val origen: String,
    val destino: String,
    val ida: List<Calle>,
    val regreso: List<Calle>,
) {
    val nombre: String get() = "Línea $linea"
    val tramo: String get() = if (origen == destino) origen else "$origen → $destino"
}

/** Recorrido convertido a líneas sobre el mapa (calles reales). */
data class Trazado(val ida: List<Punto>, val regreso: List<Punto>)

/**
 * Punto que está a [fraccion] (0..1) del largo total de [ruta].
 * Distancias aproximadas (sirve para tramos urbanos). null si la ruta está vacía.
 */
fun puntoEn(ruta: List<Punto>, fraccion: Double): Punto? {
    if (ruta.isEmpty()) return null
    if (ruta.size == 1) return ruta.first()
    fun dist(a: Punto, b: Punto): Double {
        val dx = (b.lng - a.lng) * kotlin.math.cos(Math.toRadians((a.lat + b.lat) / 2))
        val dy = b.lat - a.lat
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    val tramos = ruta.zipWithNext()
    val total = tramos.sumOf { (a, b) -> dist(a, b) }
    if (total == 0.0) return ruta.first()
    var restante = fraccion.coerceIn(0.0, 1.0) * total
    for ((a, b) in tramos) {
        val d = dist(a, b)
        if (restante <= d && d > 0) {
            val t = restante / d
            return Punto(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
        }
        restante -= d
    }
    return ruta.last()
}

data class Colectivo(
    val patente: String,
    val recorrido: String,
    /** null = sin dato (fuera de servicio). */
    val asientosDisponibles: Int?,
    val minutosLlegada: Int,
    /** Última posición GPS; null = sin ubicación. */
    val posicion: Punto? = null,
) {
    val estado: EstadoOcupacion
        get() = when {
            asientosDisponibles == null -> EstadoOcupacion.SIN_DATO
            asientosDisponibles == 0 -> EstadoOcupacion.LLENO
            else -> EstadoOcupacion.CON_ASIENTOS
        }
}

enum class EstadoReserva { ACTIVA, COMPLETADA, EXPIRADA, CANCELADA }

/** Texto de asientos que se muestra en el mapa y en el detalle. */
fun textoAsientos(c: Colectivo): String = when (c.estado) {
    EstadoOcupacion.CON_ASIENTOS ->
        if (c.asientosDisponibles == 1) "1 asiento disponible" else "${c.asientosDisponibles} asientos disponibles"
    EstadoOcupacion.LLENO -> "Lleno"
    EstadoOcupacion.SIN_DATO -> "Sin dato de asientos"
}

/** La reserva vence 3 minutos después de hacerla si el conductor no confirma el abordaje (o la elimina antes). */
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
    /** Reserva en la base de datos (0 en los datos de ejemplo). */
    val reservaId: Int = 0,
)

enum class Aviso(val codigo: String, val titulo: String) {
    AC01("AC-01", "Voy con retraso"),
    AC02("AC-02", "Desvío"),
    AC03("AC-03", "Desperfecto"),
    // AC-04 (falla de cámara) ya no existe: los vehículos no tienen cámaras.
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
    /** Folio del recorrido asignado (para dibujarlo en el mapa del conductor). */
    val folio: String = "",
    val capacidad: Int = 4,
    /** Asientos ocupados: los marca el conductor en su panel (no hay cámaras). */
    val aBordo: Int = 0,
    val reservados: Int = 0,
    val enEspera: List<PasajeroEnEspera> = emptyList(),
    val ultimoPago: Pago? = null,
) {
    val libres: Int get() = (capacidad - aBordo - reservados).coerceAtLeast(0)
}
