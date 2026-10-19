package cl.conatacoch.app.ui.pasajero

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cl.conatacoch.app.domain.validation.PasswordValidator
import cl.conatacoch.app.ui.components.BarraAtras
import cl.conatacoch.app.ui.components.BotonPrincipal
import cl.conatacoch.app.ui.components.BotonesSociales
import cl.conatacoch.app.ui.components.CampoTexto
import cl.conatacoch.app.ui.components.CirculoIcono
import cl.conatacoch.app.ui.components.EnlaceTexto
import cl.conatacoch.app.ui.components.Logo
import cl.conatacoch.app.ui.components.MargenPantalla
import cl.conatacoch.app.ui.theme.Extra

/** Columna de formulario con scroll y espacio para el teclado; `abajo` queda fijo al pie. */
@Composable
fun Formulario(abajo: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(bottomBar = abajo) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MargenPantalla),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun RegistroPasajeroScreen(vm: PasajeroAuthViewModel, onRegistrado: () -> Unit, onIrALogin: () -> Unit) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Formulario(abajo = { EnlaceTexto("¿Ya tienes cuenta?", "Inicia sesión", onIrALogin, Modifier.padding(bottom = 16.dp)) }) {
        Logo(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
        Text("Crear cuenta", style = MaterialTheme.typography.headlineLarge)
        CampoTexto("Nombre", nombre, { nombre = it }, error = vm.nombreError, ayuda = "Ingresa tu nombre".takeIf { vm.nombreError })
        CampoTexto(
            "Correo electrónico", correo, { correo = it }, teclado = KeyboardType.Email,
            error = vm.correoError, ayuda = "Ingresa un correo válido".takeIf { vm.correoError },
        )
        CampoTexto(
            "Contraseña", password, { password = it }, password = true,
            error = vm.passwordError, ayuda = "Mínimo ${PasswordValidator.MIN_LENGTH} caracteres",
        )
        BotonPrincipal("Crear cuenta", { vm.registrar(nombre, correo, password, onRegistrado) })
        BotonesSociales("o regístrate con")
    }
}

@Composable
fun CodigoVerificacionScreen(vm: PasajeroAuthViewModel, onAtras: () -> Unit, onVerificado: () -> Unit) {
    var codigo by rememberSaveable { mutableStateOf("") }
    val contexto = LocalContext.current

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().padding(horizontal = MargenPantalla),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BarraAtras(null, onAtras)
            CirculoIcono(Icons.Outlined.MailOutline, MaterialTheme.colorScheme.primaryContainer, Extra.verdeTexto, 72.dp)
            Text("Revisa tu correo", style = MaterialTheme.typography.headlineLarge)
            Text(
                buildAnnotatedString {
                    append("Enviamos un código de 6 dígitos a ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                        append(vm.correoPendiente)
                    }
                    append(". Ingrésalo para activar tu cuenta.")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CampoCodigo(codigo, { codigo = it }, error = vm.codigoError)
            if (vm.codigoError) {
                Text("Código incorrecto", color = MaterialTheme.colorScheme.error)
            }
            BotonPrincipal("Verificar", { vm.verificar(codigo, onVerificado) }, enabled = codigo.length == 6)
            EnlaceTexto("¿No te llegó?", "Reenviar código", {
                vm.reenviarCodigo()
                Toast.makeText(contexto, "Código reenviado", Toast.LENGTH_SHORT).show()
            })
        }
    }
}

/** Seis casillas sobre un solo campo de texto numérico. */
@Composable
private fun CampoCodigo(valor: String, onCambio: (String) -> Unit, error: Boolean) {
    BasicTextField(
        value = valor,
        onValueChange = { nuevo -> if (nuevo.length <= 6 && nuevo.all { it.isDigit() }) onCambio(nuevo) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(6) { i ->
                    val activa = i == valor.length
                    val color = when {
                        error -> MaterialTheme.colorScheme.error
                        activa -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.83f)
                            .border(BorderStroke(if (activa) 2.dp else 1.dp, color), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            valor.getOrNull(i)?.toString() ?: "",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun LoginPasajeroScreen(vm: PasajeroAuthViewModel, onIngresado: () -> Unit, onIrARegistro: () -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Formulario(abajo = { EnlaceTexto("¿No tienes cuenta?", "Regístrate", onIrARegistro, Modifier.padding(bottom = 16.dp)) }) {
        Logo(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
        Text("Iniciar sesión", style = MaterialTheme.typography.headlineLarge)
        if (vm.loginError) ErrorGenerico("Correo o contraseña incorrectos")
        CampoTexto("Correo electrónico", correo, { correo = it }, error = vm.loginError, teclado = KeyboardType.Email)
        CampoTexto("Contraseña", password, { password = it }, error = vm.loginError, password = true)
        // Recuperar contraseña: no está en el alcance de este paso.
        TextButton(onClick = {}, Modifier.align(Alignment.End)) {
            Text("¿Olvidaste tu contraseña?", color = Extra.verdeTexto, textDecoration = TextDecoration.Underline)
        }
        BotonPrincipal("Ingresar", { vm.ingresar(correo, password, onIngresado) })
        BotonesSociales("o continúa con")
    }
}

@Composable
fun ErrorGenerico(texto: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(texto, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleMedium)
        }
    }
}
