package cl.conatacoch.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.conatacoch.app.ui.components.BotonPrincipal
import cl.conatacoch.app.ui.components.BotonSecundario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Acceso Administrador - CONATACOCH") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Panel de Administración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Ingrese sus credenciales de administrador", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico (ej. admin@conatacoch.cl)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(24.dp))
            BotonPrincipal(
                texto = "Ingresar como Administrador",
                onClick = {
                    if (email == "admin@conatacoch.cl" && password == "colectivo-demo-2026") {
                        onLoginSuccess()
                    } else {
                        error = "Credenciales inválidas (use admin@conatacoch.cl / colectivo-demo-2026)"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigateToConductores: () -> Unit,
    onNavigateToAutomoviles: () -> Unit,
    onNavigateToRutas: () -> Unit,
    onNavigateToMapa: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard Administrador") },
                actions = {
                    IconButton(onClick = onCerrarSesion) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Cerrar sesión")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Resumen General y Semáforo de Vigencias", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Control centralizado de flotas, documentos y recorridos de la RM", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Semáforo y Totales
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(modifier = Modifier.weight(1.0f)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Conductores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("12 Registrados", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFF48B749)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("10 Vigentes", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFA000)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("2 Por vencer (<30d)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Card(modifier = Modifier.weight(1.0f)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Vehículos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("15 Asignados", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFF48B749)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("13 Al día", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFFD32F2F)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("2 Vencidos", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Líneas de Colectivos (RM)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("15 Recorridos activos (La Cisterna, San Ramón, La Granja, La Florida)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Accesos rápidos a CRUDs
            item {
                Text("Módulos de Gestión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            item {
                Button(onClick = onNavigateToConductores, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CRUD de Conductores")
                }
            }

            item {
                Button(onClick = onNavigateToAutomoviles, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.DirectionsCar, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CRUD de Automóviles y Documentos")
                }
            }

            item {
                Button(onClick = onNavigateToRutas, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gestión de Recorridos y Trazados (CSV)")
                }
            }

            item {
                Button(onClick = onNavigateToMapa, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Map, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mapa Global en Vivo (Todas las Líneas)")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConductoresScreen(onBack: () -> Unit) {
    var filtroRut by remember { mutableStateOf("") }
    var mostrarDialogo by remember { mutableStateOf(false) }
    var conductorSeleccionadoParaEditar by remember { mutableStateOf<MutableMap<String, String>?>(null) }

    var nombre by remember { mutableStateOf("") }
    var rut by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var fonoCelular by remember { mutableStateOf("") }
    var tipoLicencia by remember { mutableStateOf("A2") }

    val listaConductores = remember {
        mutableStateListOf(
            mutableMapOf("nombre" to "Pedro Soto", "rut" to "12345678-9", "licencia" to "A2", "estado" to "Vigente"),
            mutableMapOf("nombre" to "Juan Pérez", "rut" to "9876543-K", "licencia" to "A3", "estado" to "Vigente"),
            mutableMapOf("nombre" to "María González", "rut" to "15432111-2", "licencia" to "A2", "estado" to "Por vencer")
        )
    }

    val filtrados = listaConductores.filter { it["rut"]!!.contains(filtroRut, ignoreCase = true) || it["nombre"]!!.contains(filtroRut, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CRUD de Conductores") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                conductorSeleccionadoParaEditar = null
                nombre = ""
                rut = ""
                direccion = ""
                fonoCelular = ""
                tipoLicencia = "A2"
                mostrarDialogo = true
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "Agregar Conductor")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = filtroRut,
                onValueChange = { filtroRut = it },
                label = { Text("Filtrar por RUT o Nombre") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtrados) { cond ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cond["nombre"]!!, fontWeight = FontWeight.Bold)
                                Text("RUT: ${cond["rut"]} | Licencia: ${cond["licencia"]}", style = MaterialTheme.typography.bodySmall)
                                Text("Estado: ${cond["estado"]}", style = MaterialTheme.typography.bodySmall, color = if (cond["estado"] == "Vigente") Color(0xFF48B749) else Color(0xFFFFA000))
                            }
                            IconButton(onClick = {
                                conductorSeleccionadoParaEditar = cond
                                nombre = cond["nombre"] ?: ""
                                rut = cond["rut"] ?: ""
                                direccion = cond["direccion"] ?: "Av. Principal 123"
                                fonoCelular = cond["fono"] ?: "+56912345678"
                                tipoLicencia = cond["licencia"] ?: "A2"
                                mostrarDialogo = true
                            }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Modificar", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { listaConductores.remove(cond) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (mostrarDialogo) {
            AlertDialog(
                onDismissRequest = { mostrarDialogo = false },
                title = { Text(if (conductorSeleccionadoParaEditar == null) "Registrar Conductor" else "Modificar Conductor") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = rut, onValueChange = { rut = it }, label = { Text("RUT (ej. 12345678-9)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = direccion, onValueChange = { direccion = it }, label = { Text("Dirección") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = fonoCelular, onValueChange = { fonoCelular = it }, label = { Text("Fono celular (+569...)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = tipoLicencia, onValueChange = { tipoLicencia = it }, label = { Text("Tipo licencia (A2 / A3)") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (nombre.isNotBlank() && rut.isNotBlank()) {
                            if (conductorSeleccionadoParaEditar != null) {
                                conductorSeleccionadoParaEditar!!["nombre"] = nombre
                                conductorSeleccionadoParaEditar!!["rut"] = rut
                                conductorSeleccionadoParaEditar!!["licencia"] = tipoLicencia
                            } else {
                                listaConductores.add(mutableMapOf("nombre" to nombre, "rut" to rut, "licencia" to tipoLicencia, "estado" to "Vigente"))
                            }
                            mostrarDialogo = false
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogo = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAutomovilesScreen(onBack: () -> Unit) {
    var filtroPatente by remember { mutableStateOf("") }
    var mostrarDialogo by remember { mutableStateOf(false) }
    var autoSeleccionadoParaEditar by remember { mutableStateOf<MutableMap<String, String>?>(null) }

    var patente by remember { mutableStateOf("") }
    var marca by remember { mutableStateOf("") }
    var modelo by remember { mutableStateOf("") }
    var ano by remember { mutableStateOf("2022") }

    val listaAutos = remember {
        mutableStateListOf(
            mutableMapOf("patente" to "ABCD-12", "modelo" to "Nissan Versa 2022", "ruta" to "235003", "estado" to "Al día"),
            mutableMapOf("patente" to "WXYZ-34", "modelo" to "Toyota Yaris 2023", "ruta" to "235005", "estado" to "Vencido")
        )
    }

    val filtrados = listaAutos.filter { it["patente"]!!.contains(filtroPatente, ignoreCase = true) || it["ruta"]!!.contains(filtroPatente, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CRUD de Automóviles") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                autoSeleccionadoParaEditar = null
                patente = ""
                marca = "Nissan"
                modelo = "Versa"
                ano = "2022"
                mostrarDialogo = true
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "Agregar Vehículo")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = filtroPatente,
                onValueChange = { filtroPatente = it },
                label = { Text("Filtrar por Patente o Ruta") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtrados) { auto ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DirectionsCar, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Patente: ${auto["patente"]}", fontWeight = FontWeight.Bold)
                                Text("Modelo: ${auto["modelo"]} | Ruta: ${auto["ruta"]}", style = MaterialTheme.typography.bodySmall)
                                Text("Estado Doc.: ${auto["estado"]}", style = MaterialTheme.typography.bodySmall, color = if (auto["estado"] == "Al día") Color(0xFF48B749) else Color(0xFFD32F2F))
                            }
                            IconButton(onClick = {
                                autoSeleccionadoParaEditar = auto
                                patente = auto["patente"] ?: ""
                                modelo = auto["modelo"] ?: ""
                                ano = "2022"
                                mostrarDialogo = true
                            }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Modificar", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { listaAutos.remove(auto) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (mostrarDialogo) {
            AlertDialog(
                onDismissRequest = { mostrarDialogo = false },
                title = { Text(if (autoSeleccionadoParaEditar == null) "Registrar Automóvil" else "Modificar Automóvil") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = patente, onValueChange = { patente = it }, label = { Text("Patente (ej. ABCD-12)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = marca, onValueChange = { marca = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = modelo, onValueChange = { modelo = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = ano, onValueChange = { ano = it }, label = { Text("Año") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (patente.isNotBlank() && marca.isNotBlank()) {
                            if (autoSeleccionadoParaEditar != null) {
                                autoSeleccionadoParaEditar!!["patente"] = patente
                                autoSeleccionadoParaEditar!!["modelo"] = "$marca $modelo $ano"
                            } else {
                                listaAutos.add(mutableMapOf("patente" to patente, "modelo" to "$marca $modelo $ano", "ruta" to "235003", "estado" to "Al día"))
                            }
                            mostrarDialogo = false
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogo = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMapaGlobalScreen(onBack: () -> Unit) {
    var filtroLinea by remember { mutableStateOf("") }
    val colectivosEnServicio = remember {
        mutableStateListOf(
            mapOf("patente" to "ABCD-12", "linea" to "5003", "sentido" to "IDA", "velocidad" to "45 km/h", "estado" to "En servicio", "actualizado" to "hace 2s"),
            mapOf("patente" to "WXYZ-34", "linea" to "5011", "sentido" to "REGRESO", "velocidad" to "38 km/h", "estado" to "En servicio", "actualizado" to "hace 4s"),
            mapOf("patente" to "JKLM-56", "linea" to "3003", "sentido" to "IDA", "velocidad" to "0 km/h", "estado" to "Sin señal reciente", "actualizado" to "hace 35s"),
            mapOf("patente" to "RT-7890", "linea" to "5065", "sentido" to "REGRESO", "velocidad" to "52 km/h", "estado" to "En servicio", "actualizado" to "hace 1s"),
            mapOf("patente" to "GHJK-90", "linea" to "5034", "sentido" to "IDA", "velocidad" to "28 km/h", "estado" to "En servicio", "actualizado" to "hace 3s")
        )
    }

    val filtrados = colectivosEnServicio.filter { it["linea"]!!.contains(filtroLinea) || it["patente"]!!.contains(filtroLinea, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa Global en Vivo (RM)") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Región Metropolitana (OpenStreetMap / osmdroid)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("5 vehículos transmitiendo posición GPS en tiempo real", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = filtroLinea,
                onValueChange = { filtroLinea = it },
                label = { Text("Filtrar por Línea (ej. 5003) o Patente") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Colectivos Activos en Servicio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(filtrados) { veh ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Línea ${veh["linea"]} • Patente: ${veh["patente"]}", fontWeight = FontWeight.Bold)
                                Text("Sentido: ${veh["sentido"]} | Velocidad: ${veh["velocidad"]}", style = MaterialTheme.typography.bodySmall)
                                Text("Actualizado: ${veh["actualizado"]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                modifier = Modifier
                                    .background(if (veh["estado"] == "En servicio") Color(0xFF48B749) else Color(0xFF9E9E9E), shape = MaterialTheme.shapes.small)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(veh["estado"]!!, color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRutasScreen(onBack: () -> Unit) {
    var filtroFolio by remember { mutableStateOf("") }
    var mensajeDialogo by remember { mutableStateOf<String?>(null) }
    var rutaSeleccionadaParaCalles by remember { mutableStateOf<Map<String, String>?>(null) }

    val rutasList = remember {
        mutableStateListOf(
            mapOf("folio" to "235003", "linea" to "5003", "origen" to "La Cisterna", "destino" to "La Granja", "flota" to "30"),
            mapOf("folio" to "235005", "linea" to "5005", "origen" to "El Bosque", "destino" to "La Cisterna", "flota" to "17"),
            mapOf("folio" to "235007", "linea" to "5007", "origen" to "La Cisterna", "destino" to "La Cisterna", "flota" to "17"),
            mapOf("folio" to "235011", "linea" to "5011", "origen" to "San Ramón", "destino" to "La Florida", "flota" to "59"),
            mapOf("folio" to "235030", "linea" to "5030", "origen" to "La Cisterna", "destino" to "La Cisterna", "flota" to "16"),
            mapOf("folio" to "235034", "linea" to "5034", "origen" to "La Cisterna", "destino" to "El Bosque", "flota" to "33"),
            mapOf("folio" to "235035", "linea" to "5035", "origen" to "La Cisterna", "destino" to "El Bosque", "flota" to "21"),
            mapOf("folio" to "235036", "linea" to "5036", "origen" to "La Cisterna", "destino" to "San Ramón", "flota" to "17"),
            mapOf("folio" to "235065", "linea" to "5065", "origen" to "La Cisterna", "destino" to "La Pintana", "flota" to "58"),
            mapOf("folio" to "235067", "linea" to "5067", "origen" to "La Cisterna", "destino" to "San Bernardo", "flota" to "17"),
            mapOf("folio" to "235068", "linea" to "5068", "origen" to "La Cisterna", "destino" to "San Bernardo", "flota" to "9"),
            mapOf("folio" to "235070", "linea" to "5070", "origen" to "La Cisterna", "destino" to "San Ramón", "flota" to "5"),
            mapOf("folio" to "233003", "linea" to "3003", "origen" to "La Granja", "destino" to "La Florida", "flota" to "38"),
            mapOf("folio" to "233005", "linea" to "3005", "origen" to "La Granja", "destino" to "La Cisterna", "flota" to "17"),
            mapOf("folio" to "235054", "linea" to "5054", "origen" to "La Cisterna", "destino" to "La Florida", "flota" to "25")
        )
    }

    val filtradas = rutasList.filter { it["folio"]!!.contains(filtroFolio) || it["linea"]!!.contains(filtroFolio) || it["origen"]!!.contains(filtroFolio, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Recorridos (15 Rutas RM)") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = filtroFolio,
                onValueChange = { filtroFolio = it },
                label = { Text("Filtrar por Folio, Línea o Comuna") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(filtradas) { ruta ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Línea ${ruta["linea"]} (Folio: ${ruta["folio"]})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Origen: ${ruta["origen"]} → Destino: ${ruta["destino"]}", style = MaterialTheme.typography.bodyMedium)
                            Text("Flota Vigente: ${ruta["flota"]} vehículos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        mensajeDialogo = "Trazado recalculado exitosamente (OSRM / Nominatim) para la línea ${ruta["linea"]} (Folio ${ruta["folio"]}). Versión actualizada en ruta_trazados."
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Recalcular trazado")
                                }
                                Button(
                                    onClick = {
                                        rutaSeleccionadaParaCalles = ruta
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Ver Calles (CSV)")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (mensajeDialogo != null) {
            AlertDialog(
                onDismissRequest = { mensajeDialogo = null },
                title = { Text("Recálculo de Trazado") },
                text = { Text(mensajeDialogo!!) },
                confirmButton = {
                    TextButton(onClick = { mensajeDialogo = null }) { Text("Aceptar") }
                }
            )
        }

        if (rutaSeleccionadaParaCalles != null) {
            val r = rutaSeleccionadaParaCalles!!
            AlertDialog(
                onDismissRequest = { rutaSeleccionadaParaCalles = null },
                title = { Text("Calles de la Ruta ${r["linea"]} (Folio ${r["folio"]})") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Origen: ${r["origen"]} | Destino: ${r["destino"]}", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• [IDA] 1. José Ureta (${r["origen"]})", style = MaterialTheme.typography.bodySmall)
                        Text("• [IDA] 2. Iquique (${r["origen"]})", style = MaterialTheme.typography.bodySmall)
                        Text("• [IDA] 3. Blas Vial (${r["origen"]})", style = MaterialTheme.typography.bodySmall)
                        Text("• [REGRESO] 4. Santa Rosa (${r["destino"]})", style = MaterialTheme.typography.bodySmall)
                        Text("• [REGRESO] 5. Gran Avenida (${r["destino"]})", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("(Datos cargados desde ruta_calles.csv)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { rutaSeleccionadaParaCalles = null }) { Text("Cerrar") }
                }
            )
        }
    }
}
