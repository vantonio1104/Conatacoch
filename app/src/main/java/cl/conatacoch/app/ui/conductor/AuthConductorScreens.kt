package cl.conatacoch.app.ui.conductor

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import cl.conatacoch.app.domain.model.ClaseLicencia
import cl.conatacoch.app.domain.model.EstadoCuenta
import cl.conatacoch.app.domain.validation.PasswordValidator
import cl.conatacoch.app.ui.components.BarraAtras
import cl.conatacoch.app.ui.components.BotonPrincipal
import cl.conatacoch.app.ui.components.BotonSecundario
import cl.conatacoch.app.ui.components.CampoTexto
import cl.conatacoch.app.ui.components.CirculoIcono
import cl.conatacoch.app.ui.components.EnlaceTexto
import cl.conatacoch.app.ui.components.Etiqueta
import cl.conatacoch.app.ui.components.Logo
import cl.conatacoch.app.ui.components.MargenPantalla
import cl.conatacoch.app.ui.pasajero.ErrorGenerico
import cl.conatacoch.app.ui.pasajero.Formulario
import cl.conatacoch.app.ui.theme.Extra

private const val OBLIGATORIO = "Campo obligatorio"

@Composable
private fun PasoRegistro(paso: Int, titulo: String, onAtras: () -> Unit, boton: String, onBoton: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(bottomBar = { Column(Modifier.padding(MargenPantalla)) { BotonPrincipal(boton, onBoton) } }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            BarraAtras("Registro de conductor", onAtras)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = MargenPantalla),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) { i ->
                        val color = if (i < paso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        Spacer(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(color))
                    }
                }
                Text("Paso $paso de 2 · $titulo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }
}

@Composable
fun RegistroConductorPaso1Screen(vm: ConductorAuthViewModel, onAtras: () -> Unit, onContinuar: () -> Unit) {
    val f = vm.form
    fun ayuda(valor: String) = OBLIGATORIO.takeIf { vm.vacio(valor) }

    PasoRegistro(1, "Datos personales", onAtras, "Continuar", { vm.continuarPaso1(onContinuar) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CampoTexto(
                "RUT", f.rut, { vm.form = f.copy(rut = it) }, Modifier.weight(1f),
                error = vm.rutError, ayuda = "RUT no válido".takeIf { vm.rutError },
            )
            CampoTexto("Nombre completo", f.nombre, { vm.form = f.copy(nombre = it) }, Modifier.weight(1f), error = vm.vacio(f.nombre), ayuda = ayuda(f.nombre))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CampoTexto("Dirección", f.direccion, { vm.form = f.copy(direccion = it) }, Modifier.weight(1f), error = vm.vacio(f.direccion), ayuda = ayuda(f.direccion))
            CampoTexto("Comuna", f.comuna, { vm.form = f.copy(comuna = it) }, Modifier.weight(1f), error = vm.vacio(f.comuna), ayuda = ayuda(f.comuna))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CampoTexto("Celular", f.celular, { vm.form = f.copy(celular = it) }, Modifier.weight(1f), error = vm.vacio(f.celular), ayuda = ayuda(f.celular), teclado = KeyboardType.Phone)
            CampoTexto("Teléfono fijo", f.telefonoFijo, { vm.form = f.copy(telefonoFijo = it) }, Modifier.weight(1f), teclado = KeyboardType.Phone)
        }
        CampoTexto(
            "Teléfono de emergencia", f.telefonoEmergencia, { vm.form = f.copy(telefonoEmergencia = it) },
            error = vm.vacio(f.telefonoEmergencia), ayuda = ayuda(f.telefonoEmergencia), teclado = KeyboardType.Phone,
        )
        CampoTexto(
            "Correo", f.correo, { vm.form = f.copy(correo = it) }, teclado = KeyboardType.Email,
            error = vm.correoError, ayuda = "Ingresa un correo válido".takeIf { vm.correoError },
        )
        CampoTexto(
            "Contraseña (mínimo ${PasswordValidator.MIN_LENGTH} caracteres)", f.password, { vm.form = f.copy(password = it) },
            password = true, error = vm.passwordError,
            ayuda = "Debe tener entre ${PasswordValidator.MIN_LENGTH} y ${PasswordValidator.MAX_LENGTH} caracteres".takeIf { vm.passwordError },
        )
    }
}

@Composable
fun RegistroConductorPaso2Screen(vm: ConductorAuthViewModel, onAtras: () -> Unit, onEnviado: () -> Unit) {
    val f = vm.form
    val contexto = LocalContext.current
    fun ayuda(valor: String) = OBLIGATORIO.takeIf { vm.vacio(valor, paso = 2) }

    val camaraFrente = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { if (it != null) vm.fotoFrente = it }
    val camaraReverso = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { if (it != null) vm.fotoReverso = it }
    fun abrir(lanzar: () -> Unit) = try {
        lanzar()
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(contexto, "No hay una app de cámara disponible", Toast.LENGTH_SHORT).show()
    }

    PasoRegistro(2, "Licencia y acceso biométrico", onAtras, "Enviar registro", { vm.enviarRegistro(onEnviado) }) {
        Text("Clase de licencia", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ClaseLicencia.entries.forEach { clase ->
                val elegida = f.licencia == clase
                Opcion(elegida, { vm.form = f.copy(licencia = clase) }, Modifier.weight(1f), Role.RadioButton) {
                    RadioButton(selected = elegida, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(clase.name, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CampoTexto("Fecha de emisión", f.emision, { vm.form = f.copy(emision = it) }, Modifier.weight(1f), error = vm.vacio(f.emision, 2), ayuda = ayuda(f.emision) ?: "dd-mm-aaaa", teclado = KeyboardType.Number)
            CampoTexto("Fecha de vencimiento", f.vencimiento, { vm.form = f.copy(vencimiento = it) }, Modifier.weight(1f), error = vm.vacio(f.vencimiento, 2), ayuda = ayuda(f.vencimiento) ?: "dd-mm-aaaa", teclado = KeyboardType.Number)
        }
        CampoTexto("Comuna de emisión", f.comunaEmision, { vm.form = f.copy(comunaEmision = it) }, error = vm.vacio(f.comunaEmision, 2), ayuda = ayuda(f.comunaEmision))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonFoto("Foto frente", vm.fotoFrente != null, Modifier.weight(1f)) { abrir { camaraFrente.launch(null) } }
            BotonFoto("Foto reverso", vm.fotoReverso != null, Modifier.weight(1f)) { abrir { camaraReverso.launch(null) } }
        }
        if (vm.fotosError) Text("Agrega ambas fotos de la licencia", color = MaterialTheme.colorScheme.error)
        Text("Activa tu acceso biométrico", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Biometria.HUELLA to "Huella", Biometria.ROSTRO to "Rostro").forEach { (b, texto) ->
                val elegida = vm.biometria == b
                Opcion(elegida, { vm.biometria = b }, Modifier.weight(1f), Role.RadioButton) {
                    Icon(if (b == Biometria.HUELLA) Icons.Outlined.Fingerprint else Icons.Outlined.Face, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(texto, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Caja seleccionable con borde verde cuando está elegida. */
@Composable
private fun Opcion(elegida: Boolean, onClick: () -> Unit, modifier: Modifier, role: Role, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.selectable(selected = elegida, onClick = onClick, role = role),
        shape = RoundedCornerShape(16.dp),
        color = if (elegida) cs.primaryContainer else cs.surfaceContainer,
        contentColor = if (elegida) Extra.verdeTexto else cs.onSurface,
        border = BorderStroke(if (elegida) 2.dp else 1.dp, if (elegida) cs.secondary else cs.outline),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun BotonFoto(texto: String, lista: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (lista) cs.primaryContainer else cs.background,
        contentColor = if (lista) Extra.verdeTexto else cs.onSurface,
        border = BorderStroke(if (lista) 2.dp else 1.dp, if (lista) cs.secondary else cs.outline),
    ) {
        Column(Modifier.padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (lista) Icons.Filled.Check else Icons.Outlined.PhotoCamera, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(texto, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun PendienteValidacionScreen(onCerrarSesion: () -> Unit) {
    Scaffold(bottomBar = { Column(Modifier.padding(MargenPantalla)) { BotonSecundario("Cerrar sesión", onCerrarSesion) } }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(MargenPantalla),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            CirculoIcono(Icons.Outlined.HourglassEmpty, Extra.avisoFondo, Extra.avisoTexto, 132.dp)
            Etiqueta("PENDIENTE DE VALIDACIÓN", Extra.avisoFondo, Extra.avisoTexto)
            Text("Tu cuenta está en revisión", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                "La administración de CONATACOCH debe aprobar tus datos y tu licencia. Mientras tanto no puedes usar la app.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginConductorScreen(vm: ConductorAuthViewModel, onIrARegistro: () -> Unit, onIngresado: (EstadoCuenta) -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var pendiente by rememberSaveable { mutableStateOf<EstadoCuenta?>(null) }
    val activity = LocalActivity.current as FragmentActivity

    Formulario(abajo = { EnlaceTexto("¿No tienes cuenta?", "Regístrate", onIrARegistro, Modifier.padding(bottom = 16.dp)) }) {
        Logo(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
        Text("App del conductor", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (vm.loginError) ErrorGenerico("Correo o contraseña incorrectos")
        CampoTexto("Correo", correo, { correo = it }, error = vm.loginError, teclado = KeyboardType.Email)
        CampoTexto("Contraseña", password, { password = it }, error = vm.loginError, password = true)
        BotonPrincipal("Ingresar", { vm.verificarCredenciales(correo, password) { pendiente = it } })
    }

    val estado = pendiente
    if (estado != null) {
        val confirmar = { conPin: Boolean ->
            pedirBiometria(
                activity, conPin,
                onExito = { pendiente = null; onIngresado(estado) },
                onError = { Toast.makeText(activity, it, Toast.LENGTH_LONG).show() },
            )
        }
        ModalBottomSheet(onDismissRequest = { pendiente = null }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = MargenPantalla).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CirculoIcono(Icons.Outlined.Fingerprint, MaterialTheme.colorScheme.primaryContainer, Extra.verdeTexto, 104.dp)
                Text("Confirma que eres tú", style = MaterialTheme.typography.headlineSmall)
                Text("Usa tu huella o tu rostro para terminar de ingresar", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                // Huella y rostro abren el mismo diálogo: Android elige el sensor disponible.
                BotonPrincipal("Usar huella", { confirmar(false) })
                BotonSecundario("Usar rostro", { confirmar(false) })
                TextButton(onClick = { confirmar(true) }) {
                    Text("Usar PIN o patrón del teléfono", color = Extra.verdeTexto, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
