package cl.conatacoch.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.conatacoch.app.R
import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.TipoNotificacion
import cl.conatacoch.app.ui.theme.Extra
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

val MargenPantalla = 20.dp
val FormaTarjeta = RoundedCornerShape(16.dp)
private val FormaCampo = RoundedCornerShape(12.dp)

private val LocaleCL: Locale = Locale.forLanguageTag("es-CL")
val FormatoFechaHora: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy · HH:mm")
val FormatoFecha: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
val FormatoDiaSemana: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", LocaleCL)
val FormatoDiaCorto: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", LocaleCL)

/** 800 -> "$800", 1500 -> "$1.500". */
fun pesos(monto: Int): String = "$" + NumberFormat.getIntegerInstance(LocaleCL).format(monto)

@Composable
fun Logo(modifier: Modifier = Modifier) {
    // En modo oscuro el logo va sobre una tarjeta blanca (Figma).
    val fondo = if (Extra.oscuro) Color.White else Color.Transparent
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(fondo)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Image(painterResource(R.drawable.logo), contentDescription = "CONATACOCH", Modifier.width(140.dp))
    }
}

@Composable
fun BotonPrincipal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().height(52.dp), enabled = enabled) {
        Text(texto, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    borde: Color = MaterialTheme.colorScheme.outline,
) {
    OutlinedButton(
        onClick, modifier.fillMaxWidth().height(52.dp),
        border = BorderStroke(1.dp, borde),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) { Text(texto, style = MaterialTheme.typography.titleMedium) }
}

/** Campo con la etiqueta arriba, como en Figma. */
@Composable
fun CampoTexto(
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    ayuda: String? = null,
    password: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = valor,
            onValueChange = onCambio,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error,
            shape = FormaCampo,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else teclado),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
            ),
            supportingText = if (ayuda != null) {
                { Text(ayuda) }
            } else {
                null
            },
        )
    }
}

@Composable
fun BarraAtras(titulo: String?, onAtras: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = MargenPantalla, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
        if (titulo != null) {
            Spacer(Modifier.width(12.dp))
            Text(titulo, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Cabecera blanca con título y subtítulo de las pantallas de lista. */
@Composable
fun Encabezado(titulo: String, subtitulo: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = MargenPantalla, vertical = 20.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium))
        if (subtitulo != null) {
            Text(subtitulo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Cuerpo gris bajo un encabezado. */
@Composable
fun CuerpoLista(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(modifier),
        content = content,
    )
}

@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    borde: Color = MaterialTheme.colorScheme.outlineVariant,
    grosor: Dp = 1.dp,
    relleno: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = MaterialTheme.colorScheme.surfaceContainer
    val contenido: @Composable () -> Unit = { Column(Modifier.padding(relleno), content = content) }
    if (onClick != null) {
        Surface(onClick, modifier.fillMaxWidth(), shape = FormaTarjeta, color = color, border = BorderStroke(grosor, borde), content = contenido)
    } else {
        Surface(modifier.fillMaxWidth(), shape = FormaTarjeta, color = color, border = BorderStroke(grosor, borde), content = contenido)
    }
}

@Composable
fun CirculoIcono(icono: ImageVector, fondo: Color, color: Color, tamano: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(tamano).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(tamano / 2))
    }
}

/** "¿Ya tienes cuenta? Inicia sesión" */
@Composable
fun EnlaceTexto(prefijo: String, enlace: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(prefijo, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick) {
            Text(enlace, color = Extra.verdeTexto, textDecoration = TextDecoration.Underline, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Separador "o regístrate con" + botones Google y Facebook (sin funcionalidad todavía). */
@Composable
fun BotonesSociales(texto: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(texto, Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonSecundario("Google", onClick = {}, Modifier.weight(1f))
        BotonSecundario("Facebook", onClick = {}, Modifier.weight(1f))
    }
}

@Composable
fun Etiqueta(texto: String, fondo: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 12.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun FilaNotificacion(n: Notificacion) {
    val cs = MaterialTheme.colorScheme
    val (icono, fondo, color) = when (n.tipo) {
        TipoNotificacion.RESERVA_CONFIRMADA -> Triple(Icons.Filled.Check, cs.primaryContainer, Extra.verdeTexto)
        TipoNotificacion.RETRASO -> Triple(Icons.Outlined.Schedule, Extra.avisoFondo, Extra.avisoTexto)
        TipoNotificacion.DESVIO -> Triple(Icons.Outlined.TurnRight, Extra.avisoFondo, Extra.avisoTexto)
        TipoNotificacion.SIN_UBICACION -> Triple(Icons.Outlined.LocationOff, Extra.neutroFondo, cs.onSurfaceVariant)
        TipoNotificacion.NUEVA_RESERVA -> Triple(Icons.Outlined.PersonAddAlt, cs.primaryContainer, Extra.verdeTexto)
        TipoNotificacion.RESERVA_CANCELADA -> Triple(Icons.Outlined.PersonRemove, cs.errorContainer, cs.onErrorContainer)
        TipoNotificacion.NUEVA_ASIGNACION -> Triple(Icons.Outlined.CalendarMonth, Extra.infoFondo, Extra.infoTexto)
    }
    Tarjeta {
        Row(verticalAlignment = Alignment.Top) {
            CirculoIcono(icono, fondo, color)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(n.titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(n.cuando, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Text(n.detalle, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
        }
    }
}
