package cl.conatacoch.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.conatacoch.app.domain.model.AutoAdmin
import cl.conatacoch.app.domain.model.ConductorAdmin
import cl.conatacoch.app.domain.model.ConteoSemaforo
import cl.conatacoch.app.domain.model.DatosAutoAdmin
import cl.conatacoch.app.domain.model.DatosConductorAdmin
import cl.conatacoch.app.domain.model.EstadoConductor
import cl.conatacoch.app.domain.model.RecorridoAdmin
import cl.conatacoch.app.domain.model.Semaforo
import cl.conatacoch.app.domain.model.TipoTurno
import cl.conatacoch.app.domain.model.TurnosConductor
import cl.conatacoch.app.ui.components.BotonPrincipal
import cl.conatacoch.app.ui.pasajero.ErrorGenerico
import cl.conatacoch.app.ui.pasajero.MapaFlota
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Panel de administración. Todo lo que se ve y se guarda aquí viene de la base de datos (AdminViewModel).

private val VerdeSemaforo = Color(0xFF48B749)
private val AmbarSemaforo = Color(0xFFFFA000)
private val RojoSemaforo = Color(0xFFD32F2F)
private val GrisSemaforo = Color(0xFF9E9E9E)

private fun colorSemaforo(s: Semaforo) = when (s) {
    Semaforo.VIGENTE -> VerdeSemaforo
    Semaforo.POR_VENCER -> AmbarSemaforo
    Semaforo.VENCIDO -> RojoSemaforo
    Semaforo.SIN_DATOS -> GrisSemaforo
}

private fun textoSemaforo(s: Semaforo, documentos: Boolean) = when (s) {
    Semaforo.VIGENTE -> if (documentos) "Al día" else "Vigente"
    Semaforo.POR_VENCER -> "Por vencer (<30 días)"
    Semaforo.VENCIDO -> "Vencido"
    Semaforo.SIN_DATOS -> "Sin documentos"
}

/** aaaa-mm-dd → dd-mm-aaaa. */
private fun fechaCl(iso: String?): String =
    iso?.split("-")?.takeIf { it.size == 3 }?.let { (a, m, d) -> "$d-$m-$a" } ?: "—"

private val FormatoAviso = DateTimeFormatter.ofPattern("dd-MM HH:mm")

/** Errores y mensajes del servidor, arriba de cada pantalla. */
@Composable
private fun Mensajes(vm: AdminViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { ErrorGenerico(it) }
        vm.mensaje?.let {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(it, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun PuntoSemaforo(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraAdmin(titulo: String, onBack: () -> Unit, onActualizar: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(titulo) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") } },
        actions = {
            if (onActualizar != null) {
                IconButton(onClick = onActualizar) { Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar") }
            }
        },
    )
}

// ---------------------------------------------------------------- Login

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(vm: AdminViewModel, onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.limpiarMensajes() }

    Scaffold(topBar = { TopAppBar(title = { Text("Acceso Administrador - CONATACOCH") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Panel de Administración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Ingrese sus credenciales de administrador", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Mensajes(vm)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Correo electrónico") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Contraseña") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            BotonPrincipal("Ingresar como Administrador", { vm.ingresar(email, password, onLoginSuccess) }, enabled = !vm.cargando)
        }
    }
}

// ---------------------------------------------------------------- Dashboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    vm: AdminViewModel,
    onNavigateToConductores: () -> Unit,
    onNavigateToAutomoviles: () -> Unit,
    onNavigateToRutas: () -> Unit,
    onNavigateToMapa: () -> Unit,
    onNavigateToTurnos: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.cargarResumen() }
    val r = vm.resumen

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard Administrador") },
                actions = {
                    IconButton(onClick = vm::cargarResumen) { Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar") }
                    IconButton(onClick = {
                        vm.cerrarSesion()
                        onCerrarSesion()
                    }) { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Cerrar sesión") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Resumen General y Semáforo de Vigencias", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Datos en vivo desde la base de datos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Mensajes(vm)
            }
            if (r != null) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TarjetaConteo("Conductores", "${r.conductores.total} registrados", r.conductores, Modifier.weight(1f)) {
                            if (r.conductores.otros > 0) PuntoSemaforo(MaterialTheme.colorScheme.primary, "${r.conductores.otros} por validar")
                        }
                        TarjetaConteo("Vehículos", "${r.vehiculosAsignados} asignados", r.vehiculos, Modifier.weight(1f)) {
                            if (r.vehiculos.otros > 0) PuntoSemaforo(GrisSemaforo, "${r.vehiculos.otros} sin documentos")
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Operación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${r.rutas} recorridos · ${r.enServicio} vehículos en servicio ahora")
                            Text("${r.reservasActivas} reservas activas · ${r.pasajeros} pasajeros registrados")
                        }
                    }
                }
                if (r.avisosRecientes.isNotEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Avisos recientes de conductores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                r.avisosRecientes.take(5).forEach { a ->
                                    val critico = a.codigo == "AC-06" || a.codigo == "AC-03"
                                    Text(
                                        "${a.fecha.format(FormatoAviso)} · ${a.codigo} ${a.titulo} · ${a.patente ?: "—"}" +
                                            (a.linea?.let { " · Línea $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (critico) RojoSemaforo else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Text("Módulos de Gestión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { BotonModulo(Icons.Outlined.Person, "Conductores (crear, modificar, validar)", onNavigateToConductores) }
            item { BotonModulo(Icons.Outlined.Schedule, "Turnos y asignación de conductores", onNavigateToTurnos) }
            item { BotonModulo(Icons.Outlined.DirectionsCar, "Automóviles, documentos y asignación", onNavigateToAutomoviles) }
            item { BotonModulo(Icons.Outlined.CalendarMonth, "Recorridos y trazados", onNavigateToRutas) }
            item { BotonModulo(Icons.Outlined.Map, "Mapa global en vivo", onNavigateToMapa) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TarjetaConteo(titulo: String, subtitulo: String, c: ConteoSemaforo, modifier: Modifier, extra: @Composable () -> Unit) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitulo, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            PuntoSemaforo(VerdeSemaforo, "${c.vigentes} al día")
            PuntoSemaforo(AmbarSemaforo, "${c.porVencer} por vencer (<30d)")
            PuntoSemaforo(RojoSemaforo, "${c.vencidos} vencidos")
            extra()
        }
    }
}

@Composable
private fun BotonModulo(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icono, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(texto)
    }
}

// ---------------------------------------------------------------- Conductores

@Composable
fun AdminConductoresScreen(vm: AdminViewModel, onBack: () -> Unit) {
    var filtro by remember { mutableStateOf("") }
    /** null = cerrado; id null dentro = crear. */
    var formulario by remember { mutableStateOf<Pair<Int?, DatosConductorAdmin>?>(null) }
    var porEliminar by remember { mutableStateOf<ConductorAdmin?>(null) }
    LaunchedEffect(Unit) {
        vm.limpiarMensajes()
        vm.cargarConductores()
    }
    val filtrados = vm.conductores.filter {
        it.rut.contains(filtro, ignoreCase = true) || it.nombre.contains(filtro, ignoreCase = true)
    }

    Scaffold(
        topBar = { BarraAdmin("Conductores", onBack, vm::cargarConductores) },
        floatingActionButton = {
            FloatingActionButton(onClick = { formulario = null to DatosConductorAdmin() }) {
                Icon(Icons.Outlined.Add, contentDescription = "Agregar conductor")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Mensajes(vm)
            OutlinedTextField(
                value = filtro, onValueChange = { filtro = it }, label = { Text("Filtrar por RUT o nombre") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtrados, key = { it.id }) { c ->
                    TarjetaConductor(
                        c,
                        onEditar = { formulario = c.id to datosDe(c) },
                        onEliminar = { porEliminar = c },
                        onEstado = { estado -> vm.cambiarEstadoConductor(c.id, estado) },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    formulario?.let { (id, datos) ->
        FormularioConductor(
            id = id, inicial = datos, vm = vm,
            onCerrar = { formulario = null },
            onGuardar = { nuevos -> vm.guardarConductor(id, nuevos) { formulario = null } },
        )
    }
    porEliminar?.let { c ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("Eliminar conductor") },
            text = { Text("Se eliminará a ${c.nombre} (${c.rut})" + (c.email?.let { " y su cuenta $it" } ?: "") + ". Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.eliminarConductor(c.id)
                    porEliminar = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar") } },
        )
    }
}

private fun datosDe(c: ConductorAdmin) = DatosConductorAdmin(
    nombre = c.nombre, rut = c.rut, direccion = c.direccion, comuna = c.comuna, celular = c.celular,
    licencia = c.licencia, municipio = c.municipio, obtencion = fechaCl(c.obtencion), vencimiento = fechaCl(c.vencimiento),
)

@Composable
private fun TarjetaConductor(c: ConductorAdmin, onEditar: () -> Unit, onEliminar: () -> Unit, onEstado: (EstadoConductor) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.nombre, fontWeight = FontWeight.Bold)
                    Text("RUT ${c.rut} · Licencia ${c.licencia} · vence ${fechaCl(c.vencimiento)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        listOfNotNull(c.patente?.let { "Vehículo $it" }, c.email ?: "Sin cuenta en la app").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PuntoSemaforo(colorSemaforo(c.semaforo), "Licencia: ${textoSemaforo(c.semaforo, false)}")
                }
                IconButton(onClick = onEditar) { Icon(Icons.Outlined.Edit, contentDescription = "Modificar", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onEliminar) { Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error) }
            }
            when (c.estado) {
                EstadoConductor.PENDIENTE -> {
                    Spacer(Modifier.height(8.dp))
                    Text("Pendiente de validación", color = AmbarSemaforo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onEstado(EstadoConductor.APROBADA) }, Modifier.weight(1f)) { Text("Aprobar") }
                        OutlinedButton(onClick = { onEstado(EstadoConductor.RECHAZADA) }, Modifier.weight(1f)) { Text("Rechazar") }
                    }
                }
                EstadoConductor.RECHAZADA -> {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rechazado", color = RojoSemaforo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onEstado(EstadoConductor.APROBADA) }) { Text("Aprobar") }
                    }
                }
                EstadoConductor.APROBADA -> Unit
            }
        }
    }
}

@Composable
private fun FormularioConductor(
    id: Int?,
    inicial: DatosConductorAdmin,
    vm: AdminViewModel,
    onCerrar: () -> Unit,
    onGuardar: (DatosConductorAdmin) -> Unit,
) {
    var d by remember(inicial) { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (id == null) "Registrar conductor" else "Modificar conductor") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.error?.let { ErrorGenerico(it) }
                Campo("Nombre completo", d.nombre) { d = d.copy(nombre = it) }
                Campo("RUT (ej. 12345678-5)", d.rut) { d = d.copy(rut = it) }
                Campo("Dirección", d.direccion) { d = d.copy(direccion = it) }
                Campo("Comuna", d.comuna) { d = d.copy(comuna = it) }
                Campo("Celular (+569...)", d.celular, KeyboardType.Phone) { d = d.copy(celular = it) }
                Text("Clase de licencia", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A2", "A3").forEach { clase ->
                        FilterChip(selected = d.licencia == clase, onClick = { d = d.copy(licencia = clase) }, label = { Text(clase) })
                    }
                }
                Campo("Municipalidad que otorgó la licencia", d.municipio) { d = d.copy(municipio = it) }
                Campo("Obtención (dd-mm-aaaa)", d.obtencion, KeyboardType.Number) { d = d.copy(obtencion = it) }
                Campo("Vencimiento (dd-mm-aaaa)", d.vencimiento, KeyboardType.Number) { d = d.copy(vencimiento = it) }
                if (id == null) {
                    HorizontalDivider()
                    Text("Cuenta para la app (opcional)", style = MaterialTheme.typography.labelLarge)
                    Campo("Correo", d.email, KeyboardType.Email) { d = d.copy(email = it) }
                    OutlinedTextField(
                        value = d.password, onValueChange = { d = d.copy(password = it) },
                        label = { Text("Contraseña (8 a 64 caracteres)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onGuardar(d) }, enabled = !vm.cargando) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun Campo(etiqueta: String, valor: String, teclado: KeyboardType = KeyboardType.Text, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio, label = { Text(etiqueta) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado), modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- Automóviles

@Composable
fun AdminAutomovilesScreen(vm: AdminViewModel, onBack: () -> Unit) {
    var filtro by remember { mutableStateOf("") }
    var formulario by remember { mutableStateOf<Pair<Int?, DatosAutoAdmin>?>(null) }
    var porDarDeBaja by remember { mutableStateOf<AutoAdmin?>(null) }
    LaunchedEffect(Unit) {
        vm.limpiarMensajes()
        vm.cargarAutomoviles()
    }
    val filtrados = vm.automoviles.filter {
        it.patente.contains(filtro, ignoreCase = true) || (it.linea ?: "").contains(filtro) ||
            (it.conductor ?: "").contains(filtro, ignoreCase = true)
    }

    Scaffold(
        topBar = { BarraAdmin("Automóviles y documentos", onBack, vm::cargarAutomoviles) },
        floatingActionButton = {
            FloatingActionButton(onClick = { formulario = null to DatosAutoAdmin() }) {
                Icon(Icons.Outlined.Add, contentDescription = "Agregar vehículo")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Mensajes(vm)
            OutlinedTextField(
                value = filtro, onValueChange = { filtro = it }, label = { Text("Filtrar por patente, línea o conductor") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtrados, key = { it.id }) { a ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DirectionsCar, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.patente + if (a.enServicio) " · en servicio" else "", fontWeight = FontWeight.Bold)
                                Text("${a.marca} ${a.modelo} ${a.ano} · ${a.capacidad} asientos", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    (a.linea?.let { "Línea $it" } ?: "Sin recorrido") + " · " + (a.conductor ?: "Sin conductor"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Permiso ${fechaCl(a.vencimientoPermiso)} · Revisión ${fechaCl(a.vencimientoRevision)} · Seguro ${fechaCl(a.vencimientoSeguro)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                PuntoSemaforo(colorSemaforo(a.semaforo), "Documentos: ${textoSemaforo(a.semaforo, true)}")
                            }
                            IconButton(onClick = { formulario = a.id to datosDe(a) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Modificar", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { porDarDeBaja = a }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Dar de baja", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    formulario?.let { (id, datos) ->
        FormularioAuto(
            id = id, inicial = datos, vm = vm,
            onCerrar = { formulario = null },
            onGuardar = { nuevos -> vm.guardarAuto(id, nuevos) { formulario = null } },
        )
    }
    porDarDeBaja?.let { a ->
        AlertDialog(
            onDismissRequest = { porDarDeBaja = null },
            title = { Text("Dar de baja ${a.patente}") },
            text = { Text("El vehículo deja de operar y se cancelan sus reservas activas. Su historial se conserva en la base de datos.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.darDeBajaAuto(a.id)
                    porDarDeBaja = null
                }) { Text("Dar de baja", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { porDarDeBaja = null }) { Text("Cancelar") } },
        )
    }
}

private fun datosDe(a: AutoAdmin) = DatosAutoAdmin(
    patente = a.patente, marca = a.marca, modelo = a.modelo, ano = a.ano.toString(), combustible = a.combustible,
    capacidad = a.capacidad.toString(), rutaFolio = a.rutaFolio.orEmpty(), conductorRut = a.conductorRut.orEmpty(),
    vencimientoPermiso = a.vencimientoPermiso?.let { fechaCl(it) }.orEmpty(),
    vencimientoRevision = a.vencimientoRevision?.let { fechaCl(it) }.orEmpty(),
    vencimientoSeguro = a.vencimientoSeguro?.let { fechaCl(it) }.orEmpty(),
)

@Composable
private fun FormularioAuto(
    id: Int?,
    inicial: DatosAutoAdmin,
    vm: AdminViewModel,
    onCerrar: () -> Unit,
    onGuardar: (DatosAutoAdmin) -> Unit,
) {
    var d by remember(inicial) { mutableStateOf(inicial) }
    val opcionesRuta = listOf("" to "Sin recorrido") + vm.recorridos.map { it.recorrido.folio to "${it.recorrido.nombre} · ${it.recorrido.tramo}" }
    val opcionesConductor = listOf("" to "Sin conductor") +
        vm.conductores.filter { it.estado == EstadoConductor.APROBADA }.map { it.rut to "${it.nombre} (${it.rut})" }
    val combustibles = listOf("bencina", "diésel", "eléctrico", "híbrido", "gas").map { it to it.replaceFirstChar { c -> c.uppercase() } }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (id == null) "Registrar automóvil" else "Modificar automóvil") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.error?.let { ErrorGenerico(it) }
                Campo("Patente (ej. ABCD-12)", d.patente) { d = d.copy(patente = it) }
                Campo("Marca", d.marca) { d = d.copy(marca = it) }
                Campo("Modelo", d.modelo) { d = d.copy(modelo = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Campo("Año", d.ano, KeyboardType.Number) { d = d.copy(ano = it) } }
                    Box(Modifier.weight(1f)) { Campo("Asientos", d.capacidad, KeyboardType.Number) { d = d.copy(capacidad = it) } }
                }
                Selector("Combustible", d.combustible, combustibles) { d = d.copy(combustible = it) }
                HorizontalDivider()
                Text("Asignación", style = MaterialTheme.typography.labelLarge)
                Selector("Recorrido", d.rutaFolio, opcionesRuta) { d = d.copy(rutaFolio = it) }
                Selector("Conductor", d.conductorRut, opcionesConductor) { d = d.copy(conductorRut = it) }
                HorizontalDivider()
                Text("Vencimiento de documentos (dd-mm-aaaa)", style = MaterialTheme.typography.labelLarge)
                Campo("Permiso de circulación", d.vencimientoPermiso, KeyboardType.Number) { d = d.copy(vencimientoPermiso = it) }
                Campo("Revisión técnica", d.vencimientoRevision, KeyboardType.Number) { d = d.copy(vencimientoRevision = it) }
                Campo("Seguro obligatorio (SOAP)", d.vencimientoSeguro, KeyboardType.Number) { d = d.copy(vencimientoSeguro = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onGuardar(d) }, enabled = !vm.cargando) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

/** Lista desplegable simple: opciones (valor, texto). */
@Composable
private fun Selector(etiqueta: String, valor: String, opciones: List<Pair<String, String>>, onElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Column {
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) {
                Text(opciones.find { it.first == valor }?.second ?: valor.ifBlank { "Elegir" }, Modifier.weight(1f), maxLines = 1)
            }
            DropdownMenu(abierto, onDismissRequest = { abierto = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                opciones.forEach { (v, t) ->
                    DropdownMenuItem(text = { Text(t) }, onClick = {
                        onElegir(v)
                        abierto = false
                    })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Recorridos

@Composable
fun AdminRutasScreen(vm: AdminViewModel, onBack: () -> Unit) {
    var filtro by remember { mutableStateOf("") }
    var verCalles by remember { mutableStateOf<RecorridoAdmin?>(null) }
    LaunchedEffect(Unit) {
        vm.limpiarMensajes()
        vm.cargarRecorridos()
    }
    val filtradas = vm.recorridos.filter {
        val r = it.recorrido
        r.folio.contains(filtro) || r.linea.contains(filtro) || r.origen.contains(filtro, ignoreCase = true) ||
            r.destino.contains(filtro, ignoreCase = true)
    }

    Scaffold(topBar = { BarraAdmin("Recorridos y trazados", onBack, vm::cargarRecorridos) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Mensajes(vm)
            OutlinedTextField(
                value = filtro, onValueChange = { filtro = it }, label = { Text("Filtrar por folio, línea o comuna") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(filtradas, key = { it.recorrido.folio }) { ra ->
                    val r = ra.recorrido
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${r.nombre} (Folio ${r.folio})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${r.origen} → ${r.destino} · ${r.operador}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Flota vigente ${ra.flotaVigente} · ${ra.vehiculosAsignados} vehículos en la app · tarifa $${ra.tarifa}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                ra.versionTrazado?.let { "Trazado guardado: versión $it" } ?: "Trazado aún no guardado en la base de datos",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.recalcularTrazado(ra) }, enabled = vm.recalculando == null, modifier = Modifier.weight(1f)) {
                                    if (vm.recalculando == r.folio) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("Recalcular trazado")
                                }
                                Button(onClick = { verCalles = ra }, modifier = Modifier.weight(1f)) { Text("Ver calles") }
                            }
                        }
                    }
                }
            }
        }
    }

    verCalles?.let { ra ->
        val r = ra.recorrido
        AlertDialog(
            onDismissRequest = { verCalles = null },
            title = { Text("Calles de la ${r.nombre}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Ida", fontWeight = FontWeight.Bold)
                    r.ida.forEachIndexed { i, c -> Text("${i + 1}. ${c.nombre} (${c.comuna})", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    Text("Regreso", fontWeight = FontWeight.Bold)
                    r.regreso.forEachIndexed { i, c -> Text("${i + 1}. ${c.nombre} (${c.comuna})", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { verCalles = null }) { Text("Cerrar") } },
        )
    }
}

// ---------------------------------------------------------------- Mapa global

@Composable
fun AdminMapaGlobalScreen(vm: AdminViewModel, onBack: () -> Unit) {
    var filtro by remember { mutableStateOf("") }
    // Se actualiza cada 10 segundos mientras la pantalla está abierta.
    LaunchedEffect(Unit) {
        vm.limpiarMensajes()
        while (true) {
            vm.actualizarFlota()
            delay(10_000)
        }
    }
    val filtrados = vm.flota.filter { it.linea.contains(filtro) || it.patente.contains(filtro, ignoreCase = true) }
    val conGps = vm.flota.count { it.posicion != null && it.enServicio }

    Scaffold(topBar = { BarraAdmin("Mapa global en vivo (RM)", onBack, vm::actualizarFlota) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vm.error?.let { ErrorGenerico(it) }
            Card(Modifier.fillMaxWidth().height(240.dp)) {
                MapaFlota(vm.flota.filter { it.posicion != null }, Modifier.fillMaxSize())
            }
            Text(
                "${vm.flota.count { it.enServicio }} en servicio · $conGps enviando GPS · ${vm.flota.size} vehículos asignados",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = filtro, onValueChange = { filtro = it }, label = { Text("Filtrar por línea o patente") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(filtrados, key = { it.patente }) { v ->
                    val senal = v.actualizado?.let { Duration.between(it, LocalDateTime.now()).seconds }
                    val estado = when {
                        !v.enServicio -> "Fuera de servicio"
                        senal == null || senal > 120 -> "Sin señal reciente"
                        else -> "En servicio"
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Línea ${v.linea} · ${v.patente}", fontWeight = FontWeight.Bold)
                                Text(
                                    (v.conductor ?: "Sin conductor") +
                                        (v.velocidad?.let { " · ${(it * 3.6).toInt()} km/h" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "GPS: " + (senal?.let { textoHace(it) } ?: "sin datos"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(
                                Modifier.background(
                                    if (estado == "En servicio") VerdeSemaforo else GrisSemaforo, MaterialTheme.shapes.small,
                                ).padding(horizontal = 8.dp, vertical = 4.dp),
                            ) { Text(estado, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

private fun textoHace(segundos: Long): String = when {
    segundos < 60 -> "hace ${segundos.coerceAtLeast(0)} s"
    segundos < 3600 -> "hace ${segundos / 60} min"
    segundos < 86_400 -> "hace ${segundos / 3600} h"
    else -> "hace ${segundos / 86_400} días"
}

// ---------------------------------------------------------------- Turnos y asignación

private val DiaCorto = DateTimeFormatter.ofPattern("EEE d", java.util.Locale.forLanguageTag("es-CL"))
private val AzulTurno = Color(0xFF1E88E5)

private fun colorTurno(t: TipoTurno) = when (t) {
    TipoTurno.MANANA -> VerdeSemaforo
    TipoTurno.TARDE -> AzulTurno
    TipoTurno.LIBRE -> GrisSemaforo
}

/** Mañana → Tarde → Libre → Mañana… */
private fun siguiente(t: TipoTurno) = when (t) {
    TipoTurno.MANANA -> TipoTurno.TARDE
    TipoTurno.TARDE -> TipoTurno.LIBRE
    TipoTurno.LIBRE -> TipoTurno.MANANA
}

/**
 * Panel de turnos: para cada conductor aprobado, el vehículo (y la línea) que maneja
 * y sus turnos de los próximos 14 días. Tocar un día lo cambia: Mañana → Tarde → Libre.
 */
@Composable
fun AdminTurnosScreen(vm: AdminViewModel, onBack: () -> Unit) {
    var filtro by remember { mutableStateOf("") }
    var cambiarVehiculoDe by remember { mutableStateOf<TurnosConductor?>(null) }
    LaunchedEffect(Unit) {
        vm.limpiarMensajes()
        vm.cargarTurnos()
    }
    val filtrados = vm.turnos.filter { it.nombre.contains(filtro, ignoreCase = true) || it.rut.contains(filtro, ignoreCase = true) }

    Scaffold(topBar = { BarraAdmin("Turnos y asignación", onBack, vm::cargarTurnos) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Mensajes(vm)
            Text(
                "Toca un día para cambiar el turno: Mañana (06–14) → Tarde (14–22) → Libre. El conductor lo ve en Rutas y recibe un aviso.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TipoTurno.entries.forEach { PuntoSemaforo(colorTurno(it), it.texto) }
            }
            OutlinedTextField(
                value = filtro, onValueChange = { filtro = it }, label = { Text("Filtrar por nombre o RUT") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(filtrados, key = { it.conductorId }) { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Person, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.nombre, fontWeight = FontWeight.Bold)
                                    Text(
                                        t.asignacion ?: "Sin vehículo asignado",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (t.asignacion == null) AmbarSemaforo else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { cambiarVehiculoDe = t }) { Text("Vehículo") }
                            }
                            // Dos semanas: una fila por semana.
                            t.dias.chunked(7).forEach { semana ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    semana.forEach { d ->
                                        Surface(
                                            onClick = { vm.cambiarTurno(t.conductorId, d.fecha, siguiente(d.tipo)) },
                                            enabled = !vm.cargando && (t.automovilId != null || d.tipo != TipoTurno.LIBRE),
                                            color = colorTurno(d.tipo),
                                            contentColor = Color.White,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(d.fecha.format(DiaCorto), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                                Text(d.tipo.texto.take(1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                if (filtrados.isEmpty() && !vm.cargando) {
                    item { Text("No hay conductores aprobados.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    cambiarVehiculoDe?.let { t ->
        var elegido by remember(t) { mutableStateOf(t.automovilId?.toString().orEmpty()) }
        val opciones = listOf("" to "Sin vehículo") + vm.automoviles.filter { it.rutaFolio != null }.map { a ->
            a.id.toString() to "${a.patente} · Línea ${a.linea}" + (a.conductor?.takeIf { a.conductorRut != t.rut }?.let { " (hoy: $it)" } ?: "")
        }
        AlertDialog(
            onDismissRequest = { cambiarVehiculoDe = null },
            title = { Text("Vehículo de ${t.nombre}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "El conductor maneja este vehículo en la línea del vehículo. Si ya lo tenía otro conductor, ese conductor queda sin vehículo.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Selector("Vehículo", elegido, opciones) { elegido = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.asignarVehiculo(t.conductorId, elegido.toIntOrNull())
                    cambiarVehiculoDe = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { cambiarVehiculoDe = null }) { Text("Cancelar") } },
        )
    }
}
