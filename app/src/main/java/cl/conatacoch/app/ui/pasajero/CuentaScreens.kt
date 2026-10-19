package cl.conatacoch.app.ui.pasajero

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cl.conatacoch.app.domain.model.EstadoReserva
import cl.conatacoch.app.domain.model.Preferencias
import cl.conatacoch.app.ui.components.BotonSecundario
import cl.conatacoch.app.ui.components.Etiqueta
import cl.conatacoch.app.ui.components.CirculoIcono
import cl.conatacoch.app.ui.components.CuerpoLista
import cl.conatacoch.app.ui.components.Encabezado
import cl.conatacoch.app.ui.components.FilaNotificacion
import cl.conatacoch.app.ui.components.FormatoFechaHora
import cl.conatacoch.app.ui.components.MargenPantalla
import cl.conatacoch.app.ui.components.Tarjeta
import cl.conatacoch.app.ui.components.pesos
import cl.conatacoch.app.ui.theme.Extra

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(vm: PasajeroViewModel, onVerReserva: () -> Unit, modifier: Modifier = Modifier) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        if (pestana == 0) {
            Encabezado("Historial de pagos", "Pagos realizados con Rutpay")
        } else {
            Encabezado("Mis reservas", "Activas e historial de reservas")
        }
        PrimaryTabRow(selectedTabIndex = pestana, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            listOf("Pagos", "Reservas").forEachIndexed { i, titulo ->
                Tab(
                    selected = pestana == i,
                    onClick = { pestana = i },
                    text = { Text(titulo, style = MaterialTheme.typography.titleSmall) },
                    selectedContentColor = Extra.verdeTexto,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (pestana == 0) ListaPagos(vm) else ListaReservas(vm, onVerReserva)
    }
}

@Composable
private fun ListaReservas(vm: PasajeroViewModel, onVerReserva: () -> Unit) {
    val reservas by vm.reservas.collectAsState()
    val cs = MaterialTheme.colorScheme
    CuerpoLista {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (reservas.none { it.estado == EstadoReserva.ACTIVA }) {
                item { Text("No tienes reservas activas.", color = cs.onSurfaceVariant) }
            }
            items(reservas) { r ->
                if (r.estado == EstadoReserva.ACTIVA) {
                    TarjetaReservaActiva(r, onVerReserva)
                } else {
                    val (texto, fondo, color) = when (r.estado) {
                        EstadoReserva.COMPLETADA -> Triple("COMPLETADA", cs.primaryContainer, cs.onPrimaryContainer)
                        EstadoReserva.EXPIRADA -> Triple("EXPIRADA", Extra.avisoFondo, Extra.avisoTexto)
                        else -> Triple("CANCELADA", Extra.neutroFondo, cs.onSurfaceVariant)
                    }
                    Tarjeta {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CirculoIcono(Icons.Filled.DirectionsCar, cs.primaryContainer, Extra.verdeTexto)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${r.colectivo.patente} · ${r.colectivo.recorrido}", style = MaterialTheme.typography.titleMedium)
                                Text(r.fecha.format(FormatoFechaHora), color = cs.onSurfaceVariant)
                            }
                            Etiqueta(texto, fondo, color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListaPagos(vm: PasajeroViewModel) {
    val pagos by vm.historial.collectAsState()
    CuerpoLista {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(pagos, key = { it.operacion }) { p ->
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CirculoIcono(Icons.Filled.DirectionsCar, MaterialTheme.colorScheme.primaryContainer, Extra.verdeTexto)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.patente, style = MaterialTheme.typography.titleMedium)
                            Text(p.fecha.format(FormatoFechaHora), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(pesos(p.monto), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificacionesPasajeroScreen(vm: PasajeroViewModel, modifier: Modifier = Modifier) {
    val lista by vm.notificaciones.collectAsState()
    Column(modifier.fillMaxSize()) {
        Encabezado("Notificaciones", "Avisos sobre tus viajes y reservas")
        CuerpoLista {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lista) { FilaNotificacion(it) }
                item {
                    Text(
                        "Solo avisos predefinidos. No hay chat ni mensajes escritos con el conductor.",
                        Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun PreferenciasScreen(vm: PasajeroViewModel, nombre: String, onCerrarSesion: () -> Unit, modifier: Modifier = Modifier) {
    val p by vm.preferencias.collectAsState()
    val oscuro = p.modoOscuro ?: isSystemInDarkTheme()
    val cambiar = vm::actualizarPreferencias

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(MargenPantalla), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Extra.cabecera),
                contentAlignment = Alignment.Center,
            ) { Text(iniciales(nombre), color = Color.White, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Preferencias", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
                Text(nombre, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CuerpoLista(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            TituloSeccion("Apariencia")
            FilaModoOscuro(oscuro) { activo -> cambiar { it.copy(modoOscuro = activo) } }
            TituloSeccion("Notificaciones")
            Tarjeta(relleno = PaddingValues(0.dp)) {
                FilaSwitch("Llegada del colectivo", p.llegada) { v -> cambiar { it.copy(llegada = v) } }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Avisarme antes", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Preferencias.OPCIONES_ANTICIPACION.forEach { min ->
                        val elegido = p.minutosAnticipacion == min
                        FilterChip(
                            selected = elegido,
                            onClick = { cambiar { it.copy(minutosAnticipacion = min) } },
                            enabled = p.llegada,
                            label = { Text("$min min") },
                            shape = CircleShape,
                            modifier = Modifier.padding(start = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                Divisor()
                FilaSwitch("Reserva confirmada", p.reservaConfirmada) { v -> cambiar { it.copy(reservaConfirmada = v) } }
                Divisor()
                FilaSwitch("Retrasos del colectivo", p.retrasos) { v -> cambiar { it.copy(retrasos = v) } }
                Divisor()
                FilaSwitch("Desvíos del recorrido", p.desvios) { v -> cambiar { it.copy(desvios = v) } }
                Divisor()
                FilaSwitch("Ubicación del colectivo no disponible", p.sinUbicacion) { v -> cambiar { it.copy(sinUbicacion = v) } }
            }
            Spacer(Modifier.height(24.dp))
            BotonSecundario(
                "Cerrar sesión", onCerrarSesion,
                color = MaterialTheme.colorScheme.error, borde = MaterialTheme.colorScheme.error,
            )
        }
    }
}

fun iniciales(nombre: String): String =
    nombre.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun TituloSeccion(texto: String) {
    Text(texto, Modifier.padding(top = 8.dp, bottom = 10.dp), color = Extra.verdeTexto, style = MaterialTheme.typography.titleMedium)
}

@Composable
fun FilaModoOscuro(activo: Boolean, subtitulo: String? = "Descansa la vista de noche", onCambio: (Boolean) -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CirculoIcono(Icons.Outlined.DarkMode, MaterialTheme.colorScheme.primaryContainer, Extra.verdeTexto)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Modo oscuro", style = MaterialTheme.typography.titleMedium)
                if (subtitulo != null) Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(activo, onCambio)
        }
    }
}

@Composable
private fun FilaSwitch(texto: String, activo: Boolean, onCambio: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(activo, onCambio)
    }
}

@Composable
private fun Divisor() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
