package cl.conatacoch.app.ui.pasajero

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PersonAdd
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cl.conatacoch.app.domain.validation.PasswordValidator
import cl.conatacoch.app.ui.components.BotonPrincipal
import cl.conatacoch.app.ui.components.BotonSecundario
import cl.conatacoch.app.ui.components.BotonesSociales
import cl.conatacoch.app.ui.components.CampoTexto
import cl.conatacoch.app.ui.components.CirculoIcono
import cl.conatacoch.app.ui.components.CuerpoLista
import cl.conatacoch.app.ui.components.Encabezado
import cl.conatacoch.app.ui.components.EnlaceTexto
import cl.conatacoch.app.ui.components.Logo
import cl.conatacoch.app.ui.components.MargenPantalla
import cl.conatacoch.app.ui.components.Tarjeta
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
fun RegistroPasajeroScreen(vm: PasajeroAuthViewModel, onRegistrado: () -> Unit, onIrALogin: () -> Unit, onInvitado: () -> Unit) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Formulario(abajo = { EnlaceTexto("¿Ya tienes cuenta?", "Inicia sesión", onIrALogin, Modifier.padding(bottom = 16.dp)) }) {
        Logo(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
        Text("Crear cuenta", style = MaterialTheme.typography.headlineLarge)
        vm.errorServidor?.let { ErrorGenerico(it) }
        CampoTexto("Nombre", nombre, { nombre = it }, error = vm.nombreError, ayuda = "Ingresa tu nombre".takeIf { vm.nombreError })
        CampoTexto(
            "Correo electrónico", correo, { correo = it }, teclado = KeyboardType.Email,
            error = vm.correoError, ayuda = "Ingresa un correo válido".takeIf { vm.correoError },
        )
        CampoTexto(
            "Contraseña", password, { password = it }, password = true,
            error = vm.passwordError,
            ayuda = if (vm.passwordError && password.length > PasswordValidator.MAX_LENGTH) {
                "Máximo ${PasswordValidator.MAX_LENGTH} caracteres"
            } else {
                "Mínimo ${PasswordValidator.MIN_LENGTH} caracteres"
            },
        )
        BotonPrincipal("Crear cuenta", { vm.registrar(nombre, correo, password, onRegistrado) })
        BotonesSociales("o regístrate con")
        ContinuarSinCuenta(onInvitado)
    }
}

@Composable
fun LoginPasajeroScreen(vm: PasajeroAuthViewModel, onIngresado: () -> Unit, onIrARegistro: () -> Unit, onInvitado: () -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Formulario(abajo = { EnlaceTexto("¿No tienes cuenta?", "Regístrate", onIrARegistro, Modifier.padding(bottom = 16.dp)) }) {
        Logo(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
        Text("Iniciar sesión", style = MaterialTheme.typography.headlineLarge)
        if (vm.loginError) ErrorGenerico("Correo o contraseña incorrectos")
        vm.errorServidor?.let { ErrorGenerico(it) }
        CampoTexto("Correo electrónico", correo, { correo = it }, error = vm.loginError, teclado = KeyboardType.Email)
        CampoTexto("Contraseña", password, { password = it }, error = vm.loginError, password = true)
        // Recuperar contraseña: no está en el alcance de este paso.
        TextButton(onClick = {}, Modifier.align(Alignment.End)) {
            Text("¿Olvidaste tu contraseña?", color = Extra.verdeTexto, textDecoration = TextDecoration.Underline)
        }
        BotonPrincipal("Ingresar", { vm.ingresar(correo, password, onIngresado) })
        BotonesSociales("o continúa con")
        ContinuarSinCuenta(onInvitado)
    }
}

/** Entrar sin registrarse: el registro es opcional para ver el mapa y los colectivos. */
@Composable
private fun ContinuarSinCuenta(onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BotonSecundario("Continuar sin registrarme", onClick)
        Text(
            "Podrás ver recorridos y colectivos. Para reservar y pagar necesitas una cuenta.",
            Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Lo que ve un invitado en las secciones que necesitan cuenta (Pagar, Historial, Avisos).
 */
@Composable
fun SinCuentaScreen(
    titulo: String,
    subtitulo: String,
    motivo: String,
    onIniciarSesion: () -> Unit,
    onCrearCuenta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Encabezado(titulo, subtitulo)
        CuerpoLista(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            InvitaACrearCuenta(motivo, onIniciarSesion, onCrearCuenta)
        }
    }
}

@Composable
fun InvitaACrearCuenta(motivo: String, onIniciarSesion: () -> Unit, onCrearCuenta: () -> Unit) {
    Tarjeta(relleno = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CirculoIcono(Icons.Outlined.PersonAdd, MaterialTheme.colorScheme.primaryContainer, Extra.verdeTexto)
            Text("Necesitas una cuenta", style = MaterialTheme.typography.titleLarge)
            Text(motivo, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            BotonPrincipal("Crear cuenta", onCrearCuenta)
            BotonSecundario("Iniciar sesión", onIniciarSesion)
        }
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
