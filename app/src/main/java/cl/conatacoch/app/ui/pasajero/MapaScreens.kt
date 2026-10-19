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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cl.conatacoch.app.domain.model.Colectivo
import cl.conatacoch.app.domain.model.EstadoOcupacion
import cl.conatacoch.app.domain.model.Reserva
import cl.conatacoch.app.ui.components.BotonPrincipal
import cl.conatacoch.app.ui.components.BotonSecundario
import cl.conatacoch.app.ui.components.BarraAtras
import cl.conatacoch.app.ui.components.CirculoIcono
import cl.conatacoch.app.ui.components.Etiqueta
import cl.conatacoch.app.ui.components.MargenPantalla
import cl.conatacoch.app.ui.components.Tarjeta
import cl.conatacoch.app.ui.theme.Extra
import cl.conatacoch.app.ui.theme.GrisSinDato
import cl.conatacoch.app.ui.theme.Negro
import cl.conatacoch.app.ui.theme.RojoLleno
import cl.conatacoch.app.ui.theme.VerdeMarca

fun colorEstado(estado: EstadoOcupacion): Color = when (estado) {
    EstadoOcupacion.CON_ASIENTOS -> VerdeMarca
    EstadoOcupacion.LLENO -> RojoLleno
    EstadoOcupacion.SIN_DATO -> GrisSinDato
}

fun textoAsientos(c: Colectivo): String = when (c.estado) {
    EstadoOcupacion.CON_ASIENTOS ->
        if (c.asientosDisponibles == 1) "1 asiento disponible" else "${c.asientosDisponibles} asientos disponibles"
    EstadoOcupacion.LLENO -> "Lleno"
    EstadoOcupacion.SIN_DATO -> "Sin dato de asientos"
}

@Composable
fun MapaScreen(vm: PasajeroViewModel, onColectivo: (String) -> Unit, onVerReserva: () -> Unit, modifier: Modifier = Modifier) {
    val colectivos by vm.colectivos.collectAsState()
    val recorrido by vm.recorrido.collectAsState()
    val reserva by vm.reserva.collectAsState()

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Extra.neutroFondo)) {
            // Placeholder: aquí irá Google Maps.
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Map, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Text("Mapa disponible próximamente", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectorRecorrido(recorrido, vm.recorridos) { vm.recorrido.value = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstadoOcupacion.entries.forEach { ChipLeyenda(it) }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
                        .size(width = 36.dp, height = 4.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Text("Cerca de tu paradero", style = MaterialTheme.typography.titleLarge)
                reserva?.let { TarjetaReservaActiva(it, onVerReserva) }
                colectivos.forEach { c -> TarjetaColectivo(c) { onColectivo(c.patente) } }
            }
        }
    }
}

/** Reserva activa del pasajero; se muestra en el mapa y en Historial > Reservas. */
@Composable
fun TarjetaReservaActiva(r: Reserva, onVer: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Tarjeta(borde = cs.secondary, grosor = 2.dp, onClick = onVer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CirculoIcono(Icons.Filled.Check, cs.primaryContainer, Extra.verdeTexto)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Etiqueta("RESERVA ACTIVA", cs.primaryContainer, cs.onPrimaryContainer)
                Text("${r.colectivo.patente} · ${r.colectivo.recorrido}", style = MaterialTheme.typography.titleMedium)
                Text("Llega en ${r.colectivo.minutosLlegada} min", color = cs.onSurfaceVariant)
            }
            Text("Ver", color = Extra.verdeTexto, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SelectorRecorrido(actual: String, opciones: List<String>, onElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { abierto = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Text(actual, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Elegir recorrido")
            }
        }
        DropdownMenu(abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { r ->
                DropdownMenuItem(text = { Text(r) }, onClick = { onElegir(r); abierto = false })
            }
        }
    }
}

@Composable
private fun ChipLeyenda(estado: EstadoOcupacion) {
    val texto = when (estado) {
        EstadoOcupacion.CON_ASIENTOS -> "Con asientos"
        EstadoOcupacion.LLENO -> "Lleno"
        EstadoOcupacion.SIN_DATO -> "Sin dato"
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(colorEstado(estado)))
            Spacer(Modifier.width(6.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TarjetaColectivo(c: Colectivo, onClick: () -> Unit) {
    val colorTexto = when (c.estado) {
        EstadoOcupacion.CON_ASIENTOS -> Extra.verdeTexto
        EstadoOcupacion.LLENO -> MaterialTheme.colorScheme.error
        EstadoOcupacion.SIN_DATO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Tarjeta(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CirculoIcono(Icons.Filled.DirectionsCar, colorEstado(c.estado), Negro, 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("${c.patente} · ${c.recorrido}", style = MaterialTheme.typography.titleMedium)
                Text(textoAsientos(c), color = colorTexto, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${c.minutosLlegada} min", style = MaterialTheme.typography.headlineSmall)
                Text("llegada", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DetalleColectivoScreen(vm: PasajeroViewModel, patente: String, onAtras: () -> Unit, onVerReserva: () -> Unit) {
    val c = vm.colectivo(patente)
    val reserva by vm.reserva.collectAsState()
    LaunchedEffect(c) { if (c == null) onAtras() }
    if (c == null) return

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        bottomBar = {
            Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                val actual = reserva
                when {
                    // Solo una reserva activa a la vez.
                    actual == null -> BotonPrincipal(
                        "Reservar asiento", { vm.reservar(c.patente, onVerReserva) },
                        enabled = c.estado == EstadoOcupacion.CON_ASIENTOS,
                    )
                    actual.colectivo.patente == c.patente -> BotonPrincipal("Ver mi reserva", onVerReserva)
                    else -> BotonPrincipal("Ya tienes una reserva activa", {}, enabled = false)
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BarraAtras("Detalle del colectivo", onAtras)
            Column(Modifier.padding(horizontal = MargenPantalla).padding(bottom = 16.dp)) {
                Text("Patente", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(c.patente, style = MaterialTheme.typography.displaySmall)
                Text(c.recorrido, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Tarjeta(relleno = PaddingValues(20.dp)) {
                    Text("Tiempo estimado de llegada a tu paradero", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${c.minutosLlegada} min", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold))
                }
                Tarjeta(relleno = PaddingValues(20.dp)) {
                    Text("Asientos disponibles (cámaras a bordo)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colorEstado(c.estado))
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val texto = when (c.estado) {
                            EstadoOcupacion.CON_ASIENTOS -> buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.displaySmall.fontSize)) {
                                    append("${c.asientosDisponibles}  ")
                                }
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(if (c.asientosDisponibles == 1) "asiento disponible" else "asientos disponibles")
                                }
                            }
                            else -> buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(textoAsientos(c)) }
                            }
                        }
                        val colorTexto = if (c.estado == EstadoOcupacion.LLENO) Color.White else Negro
                        Text(texto, color = colorTexto, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf(
                            EstadoOcupacion.CON_ASIENTOS to "Con asientos",
                            EstadoOcupacion.LLENO to "Lleno",
                            EstadoOcupacion.SIN_DATO to "Sin dato",
                        ).forEach { (e, t) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(colorEstado(e)))
                                Spacer(Modifier.width(6.dp))
                                Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReservaActivaScreen(vm: PasajeroViewModel, onVerMapa: () -> Unit, onCancelada: () -> Unit) {
    val reserva by vm.reserva.collectAsState()
    val r = reserva ?: return
    val cs = MaterialTheme.colorScheme

    Scaffold(
        bottomBar = {
            Column(Modifier.padding(MargenPantalla), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BotonPrincipal("Ver colectivo en el mapa", onVerMapa)
                BotonSecundario("Cancelar reserva", { vm.cancelarReserva(onCancelada) }, color = cs.error, borde = cs.error)
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(MargenPantalla),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CirculoIcono(Icons.Filled.Check, cs.primaryContainer, Extra.verdeTexto, 60.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Reserva activa", style = MaterialTheme.typography.headlineMedium)
                    Etiqueta("CONFIRMADA", cs.primaryContainer, cs.onPrimaryContainer)
                }
            }
            Surface(color = Extra.cabecera, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.colectivo.patente} · ${r.colectivo.recorrido}", color = Color.White.copy(alpha = 0.85f))
                        Text("Viene en camino", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${r.colectivo.minutosLlegada} min", color = VerdeMarca,
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text("llegada estimada", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Surface(color = Extra.avisoFondo, contentColor = Extra.avisoTexto, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        buildAnnotatedString {
                            append("Tu reserva ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("vence ${Reserva.MINUTOS_PARA_ABORDAR} minutos después")
                            }
                            append(" de que el colectivo llegue a tu paradero. Si el conductor no confirma tu abordaje en ese tiempo, pasa a Expirada.")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(16.dp)) {
                Text(
                    "También se cancela si la anulas tú o si el colectivo sale de servicio.",
                    Modifier.fillMaxWidth().padding(16.dp),
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
