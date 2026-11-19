package cl.conatacoch.app.data.remote

import cl.conatacoch.app.domain.model.EstadoCuenta
import cl.conatacoch.app.domain.model.RegistroConductor
import cl.conatacoch.app.domain.repository.AuthRepository
import org.json.JSONObject
import java.util.Base64

/** Cuentas guardadas en PostgreSQL a través del backend. */
class ApiAuthRepository(private val api: ApiCliente) : AuthRepository {

    /** Crea la cuenta en la base de datos y luego inicia sesión con ella. */
    override suspend fun registrarPasajero(nombre: String, correo: String, password: String) {
        api.post("/api/auth/registro", JSONObject().put("nombre", nombre).put("email", correo).put("password", password))
        login(correo, password, "PASAJERO") ?: throw ErrorApi("La cuenta se creó, pero no se pudo iniciar sesión. Inténtalo desde Iniciar sesión.")
    }

    override suspend fun loginPasajero(correo: String, password: String): String? =
        login(correo, password, "PASAJERO")?.getString("nombre")

    override suspend fun registrarConductor(datos: RegistroConductor) {
        api.post(
            "/api/auth/registro-conductor",
            JSONObject()
                .put("rut", datos.rut).put("nombre", datos.nombre).put("direccion", datos.direccion)
                .put("comuna", datos.comuna).put("celular", datos.celular).put("telefonoFijo", datos.telefonoFijo)
                .put("telefonoEmergencia", datos.telefonoEmergencia).put("correo", datos.correo)
                .put("password", datos.password).put("licencia", datos.licencia.name)
                .put("emisionLicencia", datos.emisionLicencia).put("vencimientoLicencia", datos.vencimientoLicencia)
                .put("comunaEmision", datos.comunaEmision)
                .put("fotoFrente", datos.fotoFrente?.let { Base64.getEncoder().encodeToString(it) })
                .put("fotoReverso", datos.fotoReverso?.let { Base64.getEncoder().encodeToString(it) }),
        )
    }

    override suspend fun loginConductor(correo: String, password: String): EstadoCuenta? {
        val r = login(correo, password, "CONDUCTOR") ?: return null
        return if (r.optString("estado") == "APROBADA") EstadoCuenta.APROBADA else EstadoCuenta.PENDIENTE
    }

    override fun cerrarSesion() = api.cerrarSesion()

    /** null si el correo o la contraseña no coinciden (401); otros errores se lanzan. */
    private suspend fun login(correo: String, password: String, rol: String): JSONObject? = try {
        api.postObjeto("/api/auth/login", JSONObject().put("email", correo).put("password", password).put("rol", rol))
            .also { api.iniciarSesion(it.getString("token")) }
    } catch (e: ErrorApi) {
        if (e.codigo == 401) null else throw e
    }
}
