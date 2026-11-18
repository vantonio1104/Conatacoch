package cl.conatacoch.app.data.remote

import cl.conatacoch.app.domain.model.AutoAdmin
import cl.conatacoch.app.domain.model.AvisoRegistrado
import cl.conatacoch.app.domain.model.Calle
import cl.conatacoch.app.domain.model.ConductorAdmin
import cl.conatacoch.app.domain.model.ConteoSemaforo
import cl.conatacoch.app.domain.model.DatosAutoAdmin
import cl.conatacoch.app.domain.model.DatosConductorAdmin
import cl.conatacoch.app.domain.model.DiaTurno
import cl.conatacoch.app.domain.model.EstadoConductor
import cl.conatacoch.app.domain.model.Punto
import cl.conatacoch.app.domain.model.Recorrido
import cl.conatacoch.app.domain.model.RecorridoAdmin
import cl.conatacoch.app.domain.model.ResumenAdmin
import cl.conatacoch.app.domain.model.Semaforo
import cl.conatacoch.app.domain.model.TipoTurno
import cl.conatacoch.app.domain.model.Trazado
import cl.conatacoch.app.domain.model.TurnosConductor
import cl.conatacoch.app.domain.model.VehiculoEnVivo
import cl.conatacoch.app.domain.repository.AdminRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Panel de administración conectado al backend (PostgreSQL + MongoDB). */
class ApiAdminRepository(private val api: ApiCliente) : AdminRepository {

    override suspend fun login(email: String, password: String): Boolean = try {
        val r = api.postObjeto("/api/auth/login", JSONObject().put("email", email).put("password", password).put("rol", "ADMIN"))
        api.iniciarSesion(r.getString("token"))
        true
    } catch (e: ErrorApi) {
        if (e.codigo == 401) false else throw e
    }

    override fun cerrarSesion() = api.cerrarSesion()

    override suspend fun resumen(): ResumenAdmin {
        val o = JSONObject(api.get("/api/admin/resumen"))
        val c = o.getJSONObject("conductores")
        val v = o.getJSONObject("vehiculos")
        return ResumenAdmin(
            conductores = ConteoSemaforo(c.getInt("total"), c.getInt("vigentes"), c.getInt("por_vencer"), c.getInt("vencidos"), c.getInt("pendientes")),
            vehiculos = ConteoSemaforo(v.getInt("total"), v.getInt("al_dia"), v.getInt("por_vencer"), v.getInt("vencidos"), v.getInt("sin_documentos")),
            vehiculosAsignados = v.getInt("asignados"),
            rutas = o.getInt("rutas"),
            enServicio = o.getInt("en_servicio"),
            reservasActivas = o.getInt("reservas_activas"),
            pasajeros = o.getInt("pasajeros"),
            avisosRecientes = o.getJSONArray("avisos_recientes").mapear { a ->
                AvisoRegistrado(
                    codigo = a.getString("codigo"), titulo = a.getString("titulo"), fecha = fechaLocal(a.getString("creado_en")),
                    patente = a.textoONulo("patente"), linea = a.textoONulo("linea"), conductor = a.textoONulo("conductor"),
                )
            },
        )
    }

    // --- Conductores ---

    override suspend fun conductores(): List<ConductorAdmin> = api.getArray("/api/conductores").mapear { o ->
        ConductorAdmin(
            id = o.getInt("id"),
            nombre = o.getString("nombre_completo"),
            rut = o.getString("rut"),
            direccion = o.getString("direccion"),
            comuna = o.textoONulo("comuna").orEmpty(),
            celular = o.getString("fono_celular"),
            licencia = o.getString("tipo_licencia"),
            municipio = o.getString("municipio_licencia"),
            obtencion = o.getString("fecha_obtencion_licencia"),
            vencimiento = o.getString("fecha_vencimiento_licencia"),
            estado = EstadoConductor.valueOf(o.getString("estado")),
            email = o.textoONulo("email"),
            patente = o.textoONulo("patente"),
            semaforo = Semaforo.valueOf(o.getString("semaforo")),
        )
    }

    override suspend fun guardarConductor(id: Int?, datos: DatosConductorAdmin) {
        val cuerpo = JSONObject()
            .put("nombre_completo", datos.nombre).put("rut", datos.rut).put("direccion", datos.direccion)
            .put("comuna", datos.comuna).put("fono_celular", datos.celular).put("tipo_licencia", datos.licencia)
            .put("municipio_licencia", datos.municipio).put("fecha_obtencion_licencia", datos.obtencion)
            .put("fecha_vencimiento_licencia", datos.vencimiento)
        if (id == null) {
            if (datos.email.isNotBlank()) cuerpo.put("email", datos.email).put("password", datos.password)
            api.post("/api/conductores", cuerpo)
        } else {
            api.put("/api/conductores/$id", cuerpo)
        }
    }

    override suspend fun eliminarConductor(id: Int) {
        api.delete("/api/conductores/$id")
    }

    override suspend fun cambiarEstadoConductor(id: Int, estado: EstadoConductor) {
        api.post("/api/conductores/$id/estado", JSONObject().put("estado", estado.name))
    }

    // --- Automóviles ---

    override suspend fun automoviles(): List<AutoAdmin> = api.getArray("/api/automoviles").mapear { o ->
        AutoAdmin(
            id = o.getInt("id"),
            patente = o.getString("patente"),
            marca = o.getString("marca"),
            modelo = o.getString("modelo"),
            ano = o.getInt("ano"),
            combustible = o.getString("combustible"),
            capacidad = o.getInt("capacidad"),
            rutaFolio = o.textoONulo("ruta_folio"),
            linea = o.textoONulo("linea"),
            conductor = o.textoONulo("conductor"),
            conductorRut = o.textoONulo("conductor_rut"),
            vencimientoPermiso = o.textoONulo("vencimiento_permiso"),
            vencimientoRevision = o.textoONulo("vencimiento_revision"),
            vencimientoSeguro = o.textoONulo("vencimiento_seguro"),
            semaforo = Semaforo.valueOf(o.getString("semaforo")),
            enServicio = o.optBoolean("en_servicio"),
        )
    }

    override suspend fun guardarAuto(id: Int?, datos: DatosAutoAdmin) {
        val cuerpo = JSONObject()
            .put("patente", datos.patente).put("marca", datos.marca).put("modelo", datos.modelo)
            .put("ano", datos.ano.trim().toIntOrNull() ?: 0).put("combustible", datos.combustible)
            .put("capacidad", datos.capacidad.trim().toIntOrNull() ?: 0)
            .put("ruta_folio", datos.rutaFolio).put("conductor_rut", datos.conductorRut)
            .put("vencimiento_permiso", datos.vencimientoPermiso).put("vencimiento_revision", datos.vencimientoRevision)
            .put("vencimiento_seguro", datos.vencimientoSeguro)
        if (id == null) api.post("/api/automoviles", cuerpo) else api.put("/api/automoviles/$id", cuerpo)
    }

    override suspend fun darDeBajaAuto(id: Int) {
        api.delete("/api/automoviles/$id")
    }

    // --- Recorridos ---

    override suspend fun recorridos(): List<RecorridoAdmin> = api.getArray("/api/rutas").mapear { o ->
        RecorridoAdmin(
            recorrido = Recorrido(
                folio = o.getString("folio"),
                linea = o.getString("linea"),
                operador = o.textoONulo("operador").orEmpty(),
                origen = o.getString("origen_comuna"),
                destino = o.getString("destino_comuna"),
                ida = calles(o.getJSONArray("ida")),
                regreso = calles(o.getJSONArray("regreso")),
            ),
            tarifa = o.optInt("tarifa", 800),
            flotaVigente = o.optInt("flota_vigente"),
            vehiculosAsignados = o.optInt("vehiculos_asignados"),
            versionTrazado = if (o.isNull("version_trazado")) null else o.getInt("version_trazado"),
        )
    }

    private fun calles(a: JSONArray) = a.mapear { Calle(it.getString("calle"), it.getString("comuna")) }

    override suspend fun guardarTrazado(folio: String, trazado: Trazado): Int {
        fun puntos(l: List<Punto>) = JSONArray().apply { l.forEach { put(JSONArray().put(it.lat).put(it.lng)) } }
        val r = JSONObject(api.put("/api/rutas/$folio/trazado", JSONObject().put("ida", puntos(trazado.ida)).put("regreso", puntos(trazado.regreso))))
        return r.getInt("version")
    }

    // --- Turnos y asignación ---

    override suspend fun turnos(dias: Int): List<TurnosConductor> = api.getArray("/api/admin/turnos?dias=$dias").mapear { o ->
        TurnosConductor(
            conductorId = o.getInt("id"),
            nombre = o.getString("nombre"),
            rut = o.getString("rut"),
            automovilId = if (o.isNull("automovil_id")) null else o.getInt("automovil_id"),
            asignacion = o.textoONulo("patente")?.let { "Línea ${o.getString("linea")} · $it" },
            dias = o.getJSONArray("dias").mapear { d ->
                DiaTurno(LocalDate.parse(d.getString("fecha")), TipoTurno.valueOf(d.getString("tipo")))
            },
        )
    }

    override suspend fun guardarTurno(conductorId: Int, fecha: LocalDate, tipo: TipoTurno) {
        api.put("/api/admin/turnos", JSONObject().put("conductor_id", conductorId).put("fecha", fecha.toString()).put("tipo", tipo.name))
    }

    override suspend fun asignarVehiculo(conductorId: Int, automovilId: Int?) {
        api.post(
            "/api/admin/asignaciones",
            JSONObject().put("conductor_id", conductorId).put("automovil_id", automovilId ?: JSONObject.NULL),
        )
    }

    // --- Flota en vivo ---

    override suspend fun flota(): List<VehiculoEnVivo> = api.getArray("/api/admin/flota").mapear { o ->
        val p = o.optJSONObject("posicion")
        VehiculoEnVivo(
            patente = o.getString("patente"),
            linea = o.getString("linea"),
            conductor = o.textoONulo("conductor"),
            enServicio = o.optBoolean("en_servicio"),
            posicion = p?.let { Punto(it.getDouble("lat"), it.getDouble("lng")) },
            velocidad = p?.let { if (it.isNull("velocidad")) null else it.getDouble("velocidad") },
            actualizado = p?.textoONulo("timestamp")?.let { fechaLocal(it) },
        )
    }
}
