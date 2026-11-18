package cl.conatacoch.app.data.fake

import cl.conatacoch.app.data.local.CatalogoRecorridos
import cl.conatacoch.app.domain.model.AutoAdmin
import cl.conatacoch.app.domain.model.ConductorAdmin
import cl.conatacoch.app.domain.model.ConteoSemaforo
import cl.conatacoch.app.domain.model.DatosAutoAdmin
import cl.conatacoch.app.domain.model.DatosConductorAdmin
import cl.conatacoch.app.domain.model.DiaTurno
import cl.conatacoch.app.domain.model.EstadoConductor
import cl.conatacoch.app.domain.model.RecorridoAdmin
import cl.conatacoch.app.domain.model.ResumenAdmin
import cl.conatacoch.app.domain.model.Semaforo
import cl.conatacoch.app.domain.model.TipoTurno
import cl.conatacoch.app.domain.model.Trazado
import cl.conatacoch.app.domain.model.TurnosConductor
import cl.conatacoch.app.domain.model.VehiculoEnVivo
import cl.conatacoch.app.domain.repository.AdminRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Panel de administración sin servidor: datos en memoria (se pierden al cerrar la app). */
class FakeAdminRepository : AdminRepository {

    private val conductores = mutableListOf(
        ConductorAdmin(1, "Pedro Soto", "12345678-5", "Av. La Marina 123", "San Ramón", "+56912345678", "A2", "San Ramón",
            "2022-03-15", "2028-03-15", EstadoConductor.APROBADA, "p.soto@correo.cl", "ABCD-12", Semaforo.VIGENTE),
    )
    private val autos = mutableListOf(
        AutoAdmin(1, "ABCD-12", "Nissan", "Versa", 2022, "bencina", 4, "235003", "5003", "Pedro Soto", "12345678-5",
            "2027-04-01", "2027-05-15", "2027-03-30", Semaforo.VIGENTE, false),
    )
    private var siguienteId = 100

    override suspend fun login(email: String, password: String) =
        email.trim().lowercase() == "admin@conatacoch.cl" && password == FakeAuthRepository.DEMO_PASSWORD

    override fun cerrarSesion() = Unit

    override suspend fun resumen() = ResumenAdmin(
        conductores = ConteoSemaforo(conductores.size, conductores.count { it.semaforo == Semaforo.VIGENTE },
            conductores.count { it.semaforo == Semaforo.POR_VENCER }, conductores.count { it.semaforo == Semaforo.VENCIDO },
            conductores.count { it.estado == EstadoConductor.PENDIENTE }),
        vehiculos = ConteoSemaforo(autos.size, autos.count { it.semaforo == Semaforo.VIGENTE },
            autos.count { it.semaforo == Semaforo.POR_VENCER }, autos.count { it.semaforo == Semaforo.VENCIDO },
            autos.count { it.semaforo == Semaforo.SIN_DATOS }),
        vehiculosAsignados = autos.count { it.rutaFolio != null },
        rutas = CatalogoRecorridos.todos.size,
        enServicio = 0,
        reservasActivas = 0,
        pasajeros = 1,
        avisosRecientes = emptyList(),
    )

    override suspend fun conductores() = conductores.toList()

    override suspend fun guardarConductor(id: Int?, datos: DatosConductorAdmin) {
        val c = ConductorAdmin(
            id ?: siguienteId++, datos.nombre, datos.rut, datos.direccion, datos.comuna, datos.celular, datos.licencia,
            datos.municipio, iso(datos.obtencion), iso(datos.vencimiento), EstadoConductor.APROBADA,
            datos.email.ifBlank { null }, null, semaforo(iso(datos.vencimiento)),
        )
        val i = conductores.indexOfFirst { it.id == id }
        if (i >= 0) conductores[i] = c.copy(email = conductores[i].email, patente = conductores[i].patente) else conductores += c
    }

    override suspend fun eliminarConductor(id: Int) {
        conductores.removeAll { it.id == id }
    }

    override suspend fun cambiarEstadoConductor(id: Int, estado: EstadoConductor) {
        val i = conductores.indexOfFirst { it.id == id }
        if (i >= 0) conductores[i] = conductores[i].copy(estado = estado)
    }

    override suspend fun automoviles() = autos.toList()

    override suspend fun guardarAuto(id: Int?, datos: DatosAutoAdmin) {
        val ruta = CatalogoRecorridos.todos.find { it.folio == datos.rutaFolio }
        val conductor = conductores.find { it.rut == datos.conductorRut }
        val anterior = autos.find { it.id == id }
        val vencimientos = listOf(datos.vencimientoPermiso, datos.vencimientoRevision, datos.vencimientoSeguro).map { iso(it) }
        val a = AutoAdmin(
            id ?: siguienteId++, datos.patente.uppercase(), datos.marca, datos.modelo, datos.ano.toIntOrNull() ?: 0,
            datos.combustible, datos.capacidad.toIntOrNull() ?: 4, ruta?.folio, ruta?.linea, conductor?.nombre, conductor?.rut,
            vencimientos[0].ifBlank { anterior?.vencimientoPermiso }, vencimientos[1].ifBlank { anterior?.vencimientoRevision },
            vencimientos[2].ifBlank { anterior?.vencimientoSeguro }, Semaforo.SIN_DATOS, false,
        )
        val conSemaforo = a.copy(
            semaforo = listOfNotNull(a.vencimientoPermiso, a.vencimientoRevision, a.vencimientoSeguro)
                .takeIf { it.size == 3 }?.minOrNull()?.let { semaforo(it) } ?: Semaforo.SIN_DATOS,
        )
        val i = autos.indexOfFirst { it.id == id }
        if (i >= 0) autos[i] = conSemaforo else autos += conSemaforo
    }

    override suspend fun darDeBajaAuto(id: Int) {
        autos.removeAll { it.id == id }
    }

    override suspend fun recorridos() = CatalogoRecorridos.todos.map { r ->
        RecorridoAdmin(r, if (r.linea == "5011") 900 else 800, 0, autos.count { it.rutaFolio == r.folio }, null)
    }

    override suspend fun guardarTrazado(folio: String, trazado: Trazado) = 1

    override suspend fun flota() = autos.filter { it.linea != null }.map {
        VehiculoEnVivo(it.patente, it.linea!!, it.conductor, enServicio = false, posicion = null, velocidad = null, actualizado = null)
    }

    private val turnosDefinidos = mutableMapOf<Pair<Int, LocalDate>, TipoTurno>()

    override suspend fun turnos(dias: Int) = conductores.filter { it.estado == EstadoConductor.APROBADA }.map { c ->
        val auto = autos.find { it.conductorRut == c.rut }
        TurnosConductor(
            c.id, c.nombre, c.rut, auto?.id, auto?.let { "Línea ${it.linea} · ${it.patente}" },
            List(dias) { i ->
                val f = LocalDate.now().plusDays(i.toLong())
                DiaTurno(f, turnosDefinidos[c.id to f] ?: if (auto == null || i % 3 == 2) TipoTurno.LIBRE else TipoTurno.MANANA)
            },
        )
    }

    override suspend fun guardarTurno(conductorId: Int, fecha: LocalDate, tipo: TipoTurno) {
        turnosDefinidos[conductorId to fecha] = tipo
    }

    override suspend fun asignarVehiculo(conductorId: Int, automovilId: Int?) {
        val c = conductores.find { it.id == conductorId } ?: return
        autos.replaceAll { a ->
            when {
                a.id == automovilId -> a.copy(conductor = c.nombre, conductorRut = c.rut)
                a.conductorRut == c.rut -> a.copy(conductor = null, conductorRut = null)
                else -> a
            }
        }
    }

    /** dd-mm-aaaa → aaaa-mm-dd ("" si no es válida). */
    private fun iso(fecha: String): String =
        runCatching { LocalDate.parse(fecha.trim(), DateTimeFormatter.ofPattern("d-M-yyyy")).toString() }.getOrDefault("")

    private fun semaforo(iso: String): Semaforo {
        val f = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return Semaforo.SIN_DATOS
        val hoy = LocalDate.now()
        return when {
            f.isBefore(hoy) -> Semaforo.VENCIDO
            f.isBefore(hoy.plusDays(30)) -> Semaforo.POR_VENCER
            else -> Semaforo.VIGENTE
        }
    }
}
