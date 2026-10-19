package cl.conatacoch.app.ui.pasajero

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.conatacoch.app.ui.components.BotonPrincipal
import cl.conatacoch.app.ui.components.BotonSecundario
import cl.conatacoch.app.ui.components.CirculoIcono
import cl.conatacoch.app.ui.components.CuerpoLista
import cl.conatacoch.app.ui.components.Encabezado
import cl.conatacoch.app.ui.components.FormatoFechaHora
import cl.conatacoch.app.ui.components.MargenPantalla
import cl.conatacoch.app.ui.components.Tarjeta
import cl.conatacoch.app.ui.components.pesos
import cl.conatacoch.app.ui.theme.Extra
import cl.conatacoch.app.ui.theme.VerdeMarca

@Composable
fun PagarScreen(vm: PasajeroViewModel, onResultado: (aprobado: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val datos = vm.datosPago
    Column(modifier.fillMaxSize()) {
        Encabezado("Pagar viaje", "Escanea el QR impreso en el colectivo")
        CuerpoLista(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = VerdeMarca,
                                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
                                cornerRadius = CornerRadius(20.dp.toPx()),
                            )
                        }
                        .clickable { vm.escanearQr() }
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Escanear QR", style = MaterialTheme.typography.titleLarge)
                }
                if (datos != null) {
                    Tarjeta {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CirculoIcono(Icons.Filled.Check, MaterialTheme.colorScheme.primaryContainer, Extra.verdeTexto)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Vehículo confirmado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(datos.patente, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                    Tarjeta {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Monto a pagar", style = MaterialTheme.typography.titleMedium)
                                Text("Tarifa del ${datos.recorrido}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Solo lectura: el pasajero no puede editar el monto.
                            Text(pesos(datos.monto), style = MaterialTheme.typography.headlineLarge)
                        }
                    }
                    Text(
                        "La tarifa la fija la administración para cada recorrido y no se puede modificar. " +
                            "El QR solo contiene el código del vehículo; el pago se completa en Rutpay y llega a CONATACOCH.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BotonPrincipal("Continuar en Rutpay", { vm.pagar(onResultado) })
                }
            }
        }
    }
}

@Composable
fun PagoAprobadoScreen(vm: PasajeroViewModel, onVolver: () -> Unit) {
    val pago = vm.ultimoPago ?: return
    Scaffold(bottomBar = { Column(Modifier.padding(MargenPantalla)) { BotonPrincipal("Volver al mapa", onVolver) } }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(MargenPantalla),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            CirculoIcono(Icons.Filled.Check, MaterialTheme.colorScheme.primaryContainer, Extra.verdeTexto, 108.dp)
            Spacer(Modifier.height(20.dp))
            Text("Pago aprobado", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp),
            ) {
                val filas = listOf(
                    "N° de operación" to pago.operacion,
                    "Fecha" to pago.fecha.format(FormatoFechaHora),
                    "Vehículo" to pago.patente,
                    "Monto" to pesos(pago.monto),
                    "Medio de pago" to pago.medio,
                )
                filas.forEachIndexed { i, (etiqueta, valor) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                        Text(etiqueta, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(valor, style = MaterialTheme.typography.titleMedium)
                    }
                    if (i < filas.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun PagoRechazadoScreen(vm: PasajeroViewModel, onReintentar: () -> Unit, onVolver: () -> Unit) {
    val datos = vm.datosPago ?: return
    val cs = MaterialTheme.colorScheme
    Scaffold(
        bottomBar = {
            Column(Modifier.padding(MargenPantalla), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BotonPrincipal("Intentar de nuevo", onReintentar)
                BotonSecundario("Volver al mapa", onVolver)
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(MargenPantalla),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            CirculoIcono(Icons.Filled.Close, cs.errorContainer, cs.error, 108.dp)
            Spacer(Modifier.height(20.dp))
            Text("Pago rechazado", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            Text(
                "Rutpay no aprobó el pago de ${pesos(datos.monto)} para el vehículo ${datos.patente}. " +
                    "Revisa tu medio de pago en Rutpay e inténtalo nuevamente.",
                textAlign = TextAlign.Center,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
