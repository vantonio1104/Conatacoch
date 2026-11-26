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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import cl.conatacoch.app.ui.pasajero.MapaRecorrido
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
    LaunchedEffect(Unit) { vm.cargar() }
    ErrorComoAviso(vm)
    // En servicio, la ubicación del teléfono se guarda en la base de datos y la ven los pasajeros.
    SeguirUbicacion(s.enServicio) { u ->
        vm.enviarPosicion(u.latitude, u.longitude, if (u.hasSpeed()) u.speed else null, if (u.hasBearing()) u.bearing else null)
    }
    if (s.enServicio) PantallaServicio(vm, s, modifier) else InicioServicio(vm, modifier)
}

/** Muestra los errores del servidor (sin vehículo asignado, sin conexión...) como aviso breve. */
@Composable
fun ErrorComoAviso(vm: ConductorViewModel) {
    val contexto = LocalContext.current
    val error = vm.error
    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(contexto, error, Toast.LENGTH_LONG).show()
            vm.limpiarError()
        }
    }
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
                } else {
                    Tarjeta {
                        Text("Hoy no tienes turno", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Para iniciar servicio, la administración debe asignarte un turno. Revisa tus turnos en Rutas.",
                            style = MaterialTheme.typography.titleMedium,
                        )
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
                        // Solo con turno hoy; fuera de su horario, el servidor responde con el motivo.
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
                Text(titulo, color = Color.White, style = MaterialTheme.typography.titleLarge)
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
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Icon(Icons.Outlined.EventSeat, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Asientos", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${s.libres} ${if (s.libres == 1) "libre" else "libres"} de ${s.capacidad}", style = MaterialTheme.typography.headlineMedium)
                        Text("${s.aBordo} ocupados · ${s.reservados} reservados", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        // Mapa de la línea asignada; sigue la ubicación del conductor mientras maneja.
        val trazado by vm.trazado.collectAsState()
        MapaRecorrido(
            estado = trazado,
            colectivos = emptyList(),
            onColectivo = {},
            onReintentar = vm::reintentarTrazado,
            margenSuperior = 0.dp,
            seguirUbicacion = true,
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
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
                PanelAsientos(s, onLibre = vm::ocuparAsiento, onOcupado = vm::liberarAsiento)
                if (s.enEspera.isNotEmpty()) {
                    Text("Reservas", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
                    s.enEspera.forEach { p ->
                        ReservaEnPanel(p, onAbordo = { vm.confirmarAbordaje(p.reservaId) }, onEliminar = { vm.eliminarReserva(p.reservaId) })
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

/**
 * Panel interactivo de asientos (no hay cámaras):
 * toca un asiento libre cuando sube un pasajero sin reserva, y uno ocupado cuando baja.
 * Los reservados se confirman o eliminan en la lista de reservas.
 */
@Composable
private fun PanelAsientos(s: EstadoServicio, onLibre: () -> Unit, onOcupado: () -> Unit) {
    val reservados = s.enEspera.flatMap { p -> List(p.asientos) { p.nombre } }
    Tarjeta {
        Text("Tu vehículo", style = MaterialTheme.typography.titleMedium)
        Text(
            "Toca un asiento libre si sube alguien sin reserva, o uno ocupado si se baja.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        val asientos = List(s.capacidad) { i ->
            when {
                i < s.aBordo -> EstadoAsiento.OCUPADO to null
                i < s.aBordo + reservados.size -> EstadoAsiento.RESERVADO to reservados[i - s.aBordo]
                else -> EstadoAsiento.LIBRE to null
            }
        }
        asientos.chunked(2).forEachIndexed { fila, par ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                par.forEachIndexed { j, (estado, nombre) ->
                    Asiento(fila * 2 + j + 1, estado, nombre, Modifier.weight(1f)) {
                        when (estado) {
                            EstadoAsiento.LIBRE -> onLibre()
                            EstadoAsiento.OCUPADO -> onOcupado()
                            EstadoAsiento.RESERVADO -> Unit
                        }
                    }
                }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EstadoAsiento.entries.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(colorAsiento(e), RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(e.texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private enum class EstadoAsiento(val texto: String) { LIBRE("Libre"), RESERVADO("Reservado"), OCUPADO("Ocupado") }

private fun colorAsiento(e: EstadoAsiento) = when (e) {
    EstadoAsiento.LIBRE -> VerdeMarca
    EstadoAsiento.RESERVADO -> Color(0xFFFFA000)
    EstadoAsiento.OCUPADO -> Color(0xFF616161)
}

@Composable
private fun Asiento(numero: Int, estado: EstadoAsiento, nombre: String?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = estado != EstadoAsiento.RESERVADO,
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(16.dp),
        color = colorAsiento(estado),
        contentColor = if (estado == EstadoAsiento.LIBRE) Color.Black else Color.White,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EventSeat, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Asiento $numero", style = MaterialTheme.typography.titleMedium)
            }
            Text(nombre ?: estado.texto, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

/** Una reserva activa: vence a los 3 minutos; el conductor confirma que subió o la elimina. */
@Composable
private fun ReservaEnPanel(p: PasajeroEnEspera, onAbordo: () -> Unit, onEliminar: () -> Unit) {
    var segundos by rememberSaveable(p.reservaId) { mutableIntStateOf(p.segundosParaAbordar) }
    LaunchedEffect(p.reservaId) {
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
                Text("${p.asientos} ${if (p.asientos == 1) "asiento reservado" else "asientos reservados"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%02d:%02d".format(segundos / 60, segundos % 60), style = MaterialTheme.typography.headlineMedium)
                Text("para que expire", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onEliminar,
                modifier = Modifier.weight(1f).height(56.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Eliminar", style = MaterialTheme.typography.titleMedium) }
            Button(onAbordo, Modifier.weight(1f).height(56.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Subió", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun AvisosConductorScreen(vm: ConductorViewModel, modifier: Modifier = Modifier) {
    val s by vm.servicio.collectAsState()
    ErrorComoAviso(vm)
    val contexto = LocalContext.current
    fun enviar(a: Aviso) = vm.enviarAviso(a) {
        Toast.makeText(contexto, "${a.codigo} enviado", Toast.LENGTH_SHORT).show()
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
                    "Un toque y listo, sin texto libre. Los avisos AC-01, AC-02, AC-03 y AC-05 se habilitan solo con el vehículo detenido; " +
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
fun RutasScreen(vm: ConductorViewModel, modifier: Modifier = Modifier) {
    val turnos = vm.turnos
    LaunchedEffect(Unit) { vm.cargar() }
    ErrorComoAviso(vm)
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
                            // Turno de mañana en verde; de tarde en azul.
                            when {
                                t.recorrido == null -> Etiqueta("Libre", Extra.neutroFondo, MaterialTheme.colorScheme.onSurfaceVariant)
                                t.horario?.startsWith("06") == true ->
                                    Etiqueta(t.horario.orEmpty(), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
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
                if (lista.isEmpty()) {
                    item { Text("Aún no tienes notificaciones.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(lista) { FilaNotificacion(it) }
            }
        }
    }
}
