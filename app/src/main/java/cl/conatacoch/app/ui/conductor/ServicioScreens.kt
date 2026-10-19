package cl.conatacoch.app.ui.conductor

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.conatacoch.app.domain.model.Aviso
import cl.conatacoch.app.domain.model.EstadoServicio
import cl.conatacoch.app.domain.model.PasajeroEnEspera
import cl.conatacoch.app.ui.components.BarraAtras
import cl.conatacoch.app.ui.components.BotonPrincipal
import cl.conatacoch.app.ui.components.CirculoIcono
import cl.conatacoch.app.ui.components.CuerpoLista
import cl.conatacoch.app.ui.components.Encabezado
import cl.conatacoch.app.ui.components.Etiqueta
import cl.conatacoch.app.ui.components.FilaNotificacion
import cl.conatacoch.app.ui.components.FormatoDiaCorto
import cl.conatacoch.app.ui.components.FormatoDiaSemana
import cl.conatacoch.app.ui.components.FormatoFecha
import cl.conatacoch.app.ui.components.MargenPantalla
import cl.conatacoch.app.ui.components.Tarjeta
import cl.conatacoch.app.ui.components.pesos
import cl.conatacoch.app.ui.pasajero.FilaModoOscuro
import cl.conatacoch.app.ui.pasajero.TituloSeccion
import cl.conatacoch.app.ui.pasajero.iniciales
import cl.conatacoch.app.ui.theme.Extra
import cl.conatacoch.app.ui.theme.RojoLleno
import cl.conatacoch.app.ui.theme.VerdeMarca
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/** Pestaña Servicio: inicio de servicio o pantalla de servicio según el estado. */
@Composable
fun ServicioScreen(vm: ConductorViewModel, modifier: Modifier = Modifier) {
    val s by vm.servicio.collectAsState()
    if (s.enServicio) PantallaServicio(vm, s, modifier) else InicioServicio(vm, modifier)
}

@Composable
private fun InicioServicio(vm: ConductorViewModel, modifier: Modifier) {
    val prefs by vm.preferencias.collectAsState()
    val hoy = vm.turnos.firstOrNull()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(MargenPantalla), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hola, ${vm.nombre.substringBefore(" ")}", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium))
                if (hoy != null) {
                    Text(
                        "${hoy.fecha.format(FormatoDiaSemana).replaceFirstChar { it.uppercase() }} ${hoy.fecha.format(FormatoFecha)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Etiqueta("FUERA DE SERVICIO", Extra.neutroFondo, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CuerpoLista(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (hoy?.recorrido != null) {
                    Tarjeta {
                        Text("Tu asignación de hoy", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(hoy.recorrido, style = MaterialTheme.typography.headlineMedium)
                        Text("${hoy.patente} · ${hoy.horario}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FilaModoOscuro(prefs.modoOscuro ?: isSystemInDarkTheme(), subtitulo = null) { vm.modoOscuro(it) }
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                        .size(228.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = vm::iniciarServicio,
                        modifier = Modifier.size(200.dp),
                        shape = CircleShape,
                        enabled = hoy?.recorrido != null,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(56.dp))
                            Text("Iniciar\nservicio", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
                Text(
                    "Al iniciar se activa el envío de tu ubicación GPS cada 5 segundos.",
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Cabecera negra de servicio con el chip Detenido / En movimiento (simulado). */
@Composable
private fun CabeceraServicio(estado: String, colorEstado: Color, titulo: String, s: EstadoServicio, vm: ConductorViewModel, extra: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(Extra.cabecera).padding(MargenPantalla)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(estado, color = colorEstado, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
                Text(titulo, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            if (s.enServicio) {
                // En el prototipo se cambia a mano; después vendrá del GPS.
                Surface(
                    onClick = { vm.cambiarDetenido(!s.detenido) },
                    shape = CircleShape,
                    color = if (s.detenido) VerdeMarca else Color.Transparent,
                    contentColor = if (s.detenido) Color.Black else Color.White,
                    border = if (s.detenido) null else BorderStroke(1.dp, Color.White),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (s.detenido) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (s.detenido) "Detenido" else "En movimiento", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        extra()
    }
}

@Composable
private fun PantallaServicio(vm: ConductorViewModel, s: EstadoServicio, modifier: Modifier) {
    val lleno = s.libres == 0
    Column(modifier.fillMaxSize()) {
        CabeceraServicio(
            if (lleno) "EN SERVICIO · LLENO" else "EN SERVICIO · CON ASIENTOS",
            if (lleno) RojoLleno else VerdeMarca,
            "${s.patente} · ${s.recorrido}", s, vm,
        ) {
            Spacer(Modifier.height(16.dp))
            Surface(color = Color.White.copy(alpha = 0.08f), contentColor = Color.White, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Ocupación según cámaras", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${s.aBordo} de ${s.capacidad}", style = MaterialTheme.typography.headlineMedium)
                        Text("${s.reservados} reservados · ${s.libres} ${if (s.libres == 1) "libre" else "libres"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        CuerpoLista(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                s.ultimoPago?.let { pago ->
                    val minutos = Duration.between(pago.fecha, LocalDateTime.now()).toMinutes().coerceAtLeast(1)
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = Extra.verdeTexto, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Pago recibido") }
                                    append(" · ${pesos(pago.monto)} · hace $minutos min")
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                if (s.enEspera.isNotEmpty()) {
                    Text("Pasajeros en espera", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
                    s.enEspera.forEach { p ->
                        if (p.enEsteParadero) PasajeroEnParadero(p) { vm.confirmarAbordaje(p.nombre) }
                        else Tarjeta {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.nombre, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Text("próximo paradero · ${p.minutosAlParadero} min", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = vm::finalizarServicio,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Fuera de servicio", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PasajeroEnParadero(p: PasajeroEnEspera, onConfirmar: () -> Unit) {
    // Cuenta regresiva de los 3 minutos para abordar.
    var segundos by rememberSaveable(p.nombre) { mutableIntStateOf(p.segundosParaAbordar) }
    LaunchedEffect(p.nombre) {
        while (segundos > 0) {
            delay(1_000)
            segundos--
        }
    }
    Tarjeta(borde = MaterialTheme.colorScheme.secondary, grosor = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(iniciales(p.nombre), color = Extra.verdeTexto, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(p.nombre, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
                Text("En este paradero · ${p.asientos} ${if (p.asientos == 1) "asiento" else "asientos"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%02d:%02d".format(segundos / 60, segundos % 60), style = MaterialTheme.typography.headlineMedium)
                Text("para abordar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onConfirmar, Modifier.fillMaxWidth().height(64.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Confirmar abordaje", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun AvisosConductorScreen(vm: ConductorViewModel, onCorreccion: () -> Unit, modifier: Modifier = Modifier) {
    val s by vm.servicio.collectAsState()
    val contexto = LocalContext.current
    fun enviar(a: Aviso) = vm.enviarAviso(a) {
        Toast.makeText(contexto, "${a.codigo} enviado", Toast.LENGTH_SHORT).show()
        if (a == Aviso.AC04) onCorreccion()
    }

    Column(modifier.fillMaxSize()) {
        CabeceraServicio(
            if (s.enServicio) "EN SERVICIO" else "FUERA DE SERVICIO",
            if (s.enServicio) VerdeMarca else Color.White.copy(alpha = 0.7f),
            "Enviar aviso", s, vm,
        )
        CuerpoLista(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = { enviar(Aviso.AC06) },
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFB3261E),
                    contentColor = Color.White,
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, Modifier.size(44.dp))
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text("${Aviso.AC06.codigo} ${Aviso.AC06.titulo}", style = MaterialTheme.typography.headlineSmall)
                            Text("Siempre disponible · envía tu ubicación")
                        }
                    }
                }
                val avisos = Aviso.entries - Aviso.AC06
                avisos.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { a -> BotonAviso(a, a.habilitado(s.detenido), Modifier.weight(1f)) { enviar(a) } }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text(
                    "Un toque y listo, sin texto libre. Los avisos AC-01 a AC-05 se habilitan solo con el vehículo detenido; " +
                        "la emergencia está siempre disponible.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BotonAviso(a: Aviso, habilitado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val icono = when (a) {
        Aviso.AC01 -> Icons.Outlined.Schedule
        Aviso.AC02 -> Icons.Outlined.TurnRight
        Aviso.AC03 -> Icons.Outlined.Build
        Aviso.AC04 -> Icons.Outlined.NoPhotography
        Aviso.AC05 -> Icons.Outlined.QrCode2
        Aviso.AC06 -> Icons.Outlined.WarningAmber
    }
    Tarjeta(modifier.alpha(if (habilitado) 1f else 0.4f), onClick = if (habilitado) onClick else null) {
        Icon(icono, contentDescription = null, tint = Extra.verdeTexto, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(20.dp))
        Text(a.codigo, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(a.titulo, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun CorreccionOcupacionScreen(vm: ConductorViewModel, onAtras: () -> Unit) {
    val s by vm.servicio.collectAsState()
    // Solo se llega aquí después de enviar AC-04.
    LaunchedEffect(s.correccionHabilitada) { if (!s.correccionHabilitada) onAtras() }
    var aBordo by rememberSaveable { mutableIntStateOf(s.aBordo) }

    Scaffold(
        bottomBar = {
            Column(Modifier.padding(MargenPantalla), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Esta pantalla solo aparece después de enviar el aviso AC-04.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                BotonPrincipal("Guardar", { vm.guardarCorreccion(aBordo, onAtras) })
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BarraAtras("Corrección de ocupación", onAtras)
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = MargenPantalla),
                color = Extra.avisoFondo, contentColor = Extra.avisoTexto, shape = RoundedCornerShape(16.dp),
            ) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Outlined.NoPhotography, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("AC-04 enviado · Falla de cámara", style = MaterialTheme.typography.titleMedium)
                        Text("Indica cuántos pasajeros van realmente a bordo.")
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
            Text("Pasajeros a bordo", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                OutlinedIconButton(
                    onClick = { if (aBordo > 0) aBordo-- },
                    modifier = Modifier.size(84.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                ) { Icon(Icons.Filled.Remove, contentDescription = "Restar", Modifier.size(36.dp)) }
                Text("$aBordo", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold))
                FilledIconButton(
                    onClick = { if (aBordo < s.capacidad) aBordo++ },
                    modifier = Modifier.size(84.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.Filled.Add, contentDescription = "Sumar", Modifier.size(36.dp)) }
            }
            Text("de ${s.capacidad} asientos", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun RutasScreen(vm: ConductorViewModel, modifier: Modifier = Modifier) {
    val turnos = vm.turnos
    Column(modifier.fillMaxSize()) {
        Encabezado("Mis rutas y horarios", vm.nombre)
        CuerpoLista {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                turnos.firstOrNull()?.let { hoy ->
                    item {
                        Surface(color = Extra.cabecera, contentColor = Color.White, shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Text("Hoy · ${hoy.fecha.format(FormatoDiaSemana)} ${hoy.fecha.format(FormatoFecha)}", color = Color.White.copy(alpha = 0.85f))
                                Text(hoy.recorrido ?: "Libre", style = MaterialTheme.typography.headlineMedium)
                                if (hoy.recorrido != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text(hoy.patente.orEmpty(), style = MaterialTheme.typography.titleLarge)
                                        Text(hoy.horario.orEmpty(), color = VerdeMarca, style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            }
                        }
                    }
                    item { TituloSeccion("Próximos días") }
                }
                items(turnos.drop(1)) { t ->
                    Tarjeta {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t.fecha.format(FormatoDiaCorto).uppercase().trimEnd('.'), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${t.fecha.dayOfMonth}", style = MaterialTheme.typography.headlineSmall)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.recorrido ?: "Libre", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
                                Text(t.patente?.let { "Vehículo $it" } ?: "Sin asignación", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when (t.recorrido) {
                                null -> Etiqueta("Libre", Extra.neutroFondo, MaterialTheme.colorScheme.onSurfaceVariant)
                                "Recorrido 1" -> Etiqueta(t.horario.orEmpty(), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                else -> Etiqueta(t.horario.orEmpty(), Extra.infoFondo, Extra.infoTexto)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertasConductorScreen(vm: ConductorViewModel, modifier: Modifier = Modifier) {
    val lista by vm.notificaciones.collectAsState()
    Column(modifier.fillMaxSize()) {
        Encabezado("Notificaciones", "Avisos del sistema para ti")
        CuerpoLista {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lista) { FilaNotificacion(it) }
            }
        }
    }
}
