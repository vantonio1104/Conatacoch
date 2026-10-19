package cl.conatacoch.app.domain.repository

import cl.conatacoch.app.domain.model.Aviso
import cl.conatacoch.app.domain.model.Colectivo
import cl.conatacoch.app.domain.model.DatosPago
import cl.conatacoch.app.domain.model.EstadoCuenta
import cl.conatacoch.app.domain.model.EstadoServicio
import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.Pago
import cl.conatacoch.app.domain.model.Preferencias
import cl.conatacoch.app.domain.model.RegistroConductor
import cl.conatacoch.app.domain.model.Reserva
import cl.conatacoch.app.domain.model.Turno
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Contratos entre la UI y los datos. Hoy los implementa data/fake;
// después los implementará la API Django REST + WebSockets.

interface AuthRepository {
    suspend fun registrarPasajero(nombre: String, correo: String, password: String)
    suspend fun reenviarCodigo(correo: String)
    suspend fun verificarCodigo(correo: String, codigo: String): Boolean
    /** Devuelve el nombre del pasajero o null si el correo o la contraseña no coinciden. */
    suspend fun loginPasajero(correo: String, password: String): String?

    suspend fun registrarConductor(datos: RegistroConductor)
    /** null si el correo o la contraseña no coinciden. */
    suspend fun loginConductor(correo: String, password: String): EstadoCuenta?
}

interface PasajeroRepository {
    val recorridos: List<String>
    fun colectivosCercanos(recorrido: String): Flow<List<Colectivo>>
    suspend fun colectivo(patente: String): Colectivo?
    val reservaActiva: StateFlow<Reserva?>
    suspend fun reservar(patente: String)
    suspend fun cancelarReserva()
    /** Datos que trae el QR del vehículo más la tarifa del recorrido. */
    suspend fun datosPago(patente: String): DatosPago
    /** null = pago rechazado. */
    suspend fun pagar(datos: DatosPago): Pago?
    val historial: Flow<List<Pago>>
    val notificaciones: Flow<List<Notificacion>>
}

interface PreferenciasRepository {
    val preferencias: StateFlow<Preferencias>
    fun actualizar(cambio: (Preferencias) -> Preferencias)
}

interface ConductorRepository {
    val nombre: String
    /** El primero es el turno de hoy. */
    suspend fun turnos(): List<Turno>
    val servicio: StateFlow<EstadoServicio>
    suspend fun iniciarServicio()
    suspend fun finalizarServicio()
    /** En el prototipo se simula; luego vendrá del GPS. */
    fun cambiarDetenido(detenido: Boolean)
    suspend fun confirmarAbordaje(nombre: String)
    suspend fun enviarAviso(aviso: Aviso)
    suspend fun corregirOcupacion(aBordo: Int)
    val notificaciones: Flow<List<Notificacion>>
}
