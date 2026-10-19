package cl.conatacoch.app.data.fake

import cl.conatacoch.app.domain.model.EstadoCuenta
import cl.conatacoch.app.domain.model.RegistroConductor
import cl.conatacoch.app.domain.repository.AuthRepository

/** Cuentas en memoria. Se pierden al cerrar la app. */
class FakeAuthRepository : AuthRepository {

    private data class Cuenta(val nombre: String, val password: String, val estado: EstadoCuenta)

    private val pasajeros = mutableMapOf(
        "camila.reyes@correo.cl" to Cuenta("Camila Reyes", DEMO_PASSWORD, EstadoCuenta.APROBADA),
    )
    private val conductores = mutableMapOf(
        "p.soto@correo.cl" to Cuenta("Pedro Soto Muñoz", DEMO_PASSWORD, EstadoCuenta.APROBADA),
    )

    override suspend fun registrarPasajero(nombre: String, correo: String, password: String) {
        pasajeros[correo.trim().lowercase()] = Cuenta(nombre, password, EstadoCuenta.APROBADA)
    }

    override suspend fun reenviarCodigo(correo: String) = Unit

    // Sin backend no hay correo real: se acepta cualquier código de 6 dígitos.
    override suspend fun verificarCodigo(correo: String, codigo: String): Boolean =
        codigo.length == 6 && codigo.all { it.isDigit() }

    override suspend fun loginPasajero(correo: String, password: String): String? =
        pasajeros[correo.trim().lowercase()]?.takeIf { it.password == password }?.nombre

    override suspend fun registrarConductor(datos: RegistroConductor) {
        conductores[datos.correo.trim().lowercase()] =
            Cuenta(datos.nombre, datos.password, EstadoCuenta.PENDIENTE)
    }

    override suspend fun loginConductor(correo: String, password: String): EstadoCuenta? =
        conductores[correo.trim().lowercase()]?.takeIf { it.password == password }?.estado

    companion object {
        const val DEMO_PASSWORD = "colectivo-demo-2026"
    }
}
