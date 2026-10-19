package cl.conatacoch.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cl.conatacoch.app.AppContainer
import cl.conatacoch.app.domain.model.EstadoCuenta
import cl.conatacoch.app.ui.components.BotonPrincipal
import cl.conatacoch.app.ui.components.BotonSecundario
import cl.conatacoch.app.ui.components.Logo
import cl.conatacoch.app.ui.components.MargenPantalla
import cl.conatacoch.app.ui.conductor.AlertasConductorScreen
import cl.conatacoch.app.ui.conductor.AvisosConductorScreen
import cl.conatacoch.app.ui.conductor.ConductorAuthViewModel
import cl.conatacoch.app.ui.conductor.ConductorViewModel
import cl.conatacoch.app.ui.conductor.CorreccionOcupacionScreen
import cl.conatacoch.app.ui.conductor.LoginConductorScreen
import cl.conatacoch.app.ui.conductor.PendienteValidacionScreen
import cl.conatacoch.app.ui.conductor.RegistroConductorPaso1Screen
import cl.conatacoch.app.ui.conductor.RegistroConductorPaso2Screen
import cl.conatacoch.app.ui.conductor.RutasScreen
import cl.conatacoch.app.ui.conductor.ServicioScreen
import cl.conatacoch.app.ui.pasajero.CodigoVerificacionScreen
import cl.conatacoch.app.ui.pasajero.DetalleColectivoScreen
import cl.conatacoch.app.ui.pasajero.HistorialScreen
import cl.conatacoch.app.ui.pasajero.LoginPasajeroScreen
import cl.conatacoch.app.ui.pasajero.MapaScreen
import cl.conatacoch.app.ui.pasajero.NotificacionesPasajeroScreen
import cl.conatacoch.app.ui.pasajero.PagarScreen
import cl.conatacoch.app.ui.pasajero.PagoAprobadoScreen
import cl.conatacoch.app.ui.pasajero.PagoRechazadoScreen
import cl.conatacoch.app.ui.pasajero.PasajeroAuthViewModel
import cl.conatacoch.app.ui.pasajero.PasajeroViewModel
import cl.conatacoch.app.ui.pasajero.PreferenciasScreen
import cl.conatacoch.app.ui.pasajero.RegistroPasajeroScreen
import cl.conatacoch.app.ui.pasajero.ReservaActivaScreen
import cl.conatacoch.app.ui.theme.Extra

object Rutas {
    const val PERFIL = "perfil"

    const val P_LOGIN = "pasajero/login"
    const val P_REGISTRO = "pasajero/registro"
    const val P_CODIGO = "pasajero/codigo"
    const val P_MAPA = "pasajero/mapa"
    const val P_PAGAR = "pasajero/pagar"
    const val P_HISTORIAL = "pasajero/historial"
    const val P_AVISOS = "pasajero/avisos"
    const val P_PERFIL = "pasajero/perfil"
    const val P_DETALLE = "pasajero/detalle/{patente}"
    const val P_RESERVA = "pasajero/reserva"
    const val P_PAGO_OK = "pasajero/pago-aprobado"
    const val P_PAGO_ERROR = "pasajero/pago-rechazado"

    const val C_LOGIN = "conductor/login"
    const val C_REGISTRO_1 = "conductor/registro-1"
    const val C_REGISTRO_2 = "conductor/registro-2"
    const val C_PENDIENTE = "conductor/pendiente"
    const val C_SERVICIO = "conductor/servicio"
    const val C_AVISOS = "conductor/avisos"
    const val C_RUTAS = "conductor/rutas"
    const val C_ALERTAS = "conductor/alertas"
    const val C_CORRECCION = "conductor/correccion"

    fun detalle(patente: String) = "pasajero/detalle/$patente"
}

private data class Pestana(val ruta: String, val etiqueta: String, val icono: ImageVector)

private val PestanasPasajero = listOf(
    Pestana(Rutas.P_MAPA, "Mapa", Icons.Outlined.Map),
    Pestana(Rutas.P_PAGAR, "Pagar", Icons.Outlined.QrCode2),
    Pestana(Rutas.P_HISTORIAL, "Historial", Icons.Outlined.Schedule),
    Pestana(Rutas.P_AVISOS, "Avisos", Icons.Outlined.Notifications),
    Pestana(Rutas.P_PERFIL, "Perfil", Icons.Outlined.Person),
)

private val PestanasConductor = listOf(
    Pestana(Rutas.C_SERVICIO, "Servicio", Icons.Outlined.DirectionsCar),
    Pestana(Rutas.C_AVISOS, "Avisos", Icons.Outlined.Notifications),
    Pestana(Rutas.C_RUTAS, "Rutas", Icons.Outlined.CalendarMonth),
    Pestana(Rutas.C_ALERTAS, "Alertas", Icons.Outlined.Inbox),
)

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    // Con alcance de la Activity: se comparten entre pantallas del mismo perfil.
    val pasajeroAuth = viewModel { PasajeroAuthViewModel(AppContainer.auth) }
    val pasajero = viewModel { PasajeroViewModel(AppContainer.pasajero, AppContainer.preferencias) }
    val conductorAuth = viewModel { ConductorAuthViewModel(AppContainer.auth) }
    val conductor = viewModel { ConductorViewModel(AppContainer.conductor, AppContainer.preferencias) }

    fun irAInicio(ruta: String) = nav.navigate(ruta) { popUpTo(nav.graph.id) { inclusive = true } }
    fun volverAlMapa() {
        pasajero.terminarPago()
        nav.navigate(Rutas.P_MAPA) { popUpTo(Rutas.P_MAPA) { inclusive = false }; launchSingleTop = true }
    }
    fun resultadoPago(aprobado: Boolean) =
        nav.navigate(if (aprobado) Rutas.P_PAGO_OK else Rutas.P_PAGO_ERROR) {
            popUpTo(Rutas.P_PAGO_ERROR) { inclusive = true }
        }

    NavHost(nav, startDestination = Rutas.PERFIL) {
        composable(Rutas.PERFIL) {
            SelectorPerfil(onPasajero = { nav.navigate(Rutas.P_LOGIN) }, onConductor = { nav.navigate(Rutas.C_LOGIN) })
        }

        // --- Pasajero ---
        composable(Rutas.P_LOGIN) {
            LoginPasajeroScreen(
                pasajeroAuth,
                onIngresado = { irAInicio(Rutas.P_MAPA) },
                onIrARegistro = { nav.navigate(Rutas.P_REGISTRO) { launchSingleTop = true } },
            )
        }
        composable(Rutas.P_REGISTRO) {
            RegistroPasajeroScreen(
                pasajeroAuth,
                onRegistrado = { nav.navigate(Rutas.P_CODIGO) },
                onIrALogin = { nav.navigate(Rutas.P_LOGIN) { popUpTo(Rutas.P_LOGIN) { inclusive = true } } },
            )
        }
        composable(Rutas.P_CODIGO) {
            CodigoVerificacionScreen(pasajeroAuth, onAtras = { nav.popBackStack() }, onVerificado = { irAInicio(Rutas.P_MAPA) })
        }
        composable(Rutas.P_MAPA) {
            ConBarra(nav, PestanasPasajero, Rutas.P_MAPA) { MapaScreen(pasajero, { nav.navigate(Rutas.detalle(it)) }, it) }
        }
        composable(Rutas.P_PAGAR) {
            ConBarra(nav, PestanasPasajero, Rutas.P_PAGAR) { PagarScreen(pasajero, ::resultadoPago, it) }
        }
        composable(Rutas.P_HISTORIAL) {
            ConBarra(nav, PestanasPasajero, Rutas.P_HISTORIAL) { HistorialScreen(pasajero, it) }
        }
        composable(Rutas.P_AVISOS) {
            ConBarra(nav, PestanasPasajero, Rutas.P_AVISOS) { NotificacionesPasajeroScreen(pasajero, it) }
        }
        composable(Rutas.P_PERFIL) {
            ConBarra(nav, PestanasPasajero, Rutas.P_PERFIL) { PreferenciasScreen(pasajero, pasajeroAuth.nombre, it) }
        }
        composable(Rutas.P_DETALLE) { entrada ->
            DetalleColectivoScreen(
                pasajero,
                patente = entrada.arguments?.getString("patente").orEmpty(),
                onAtras = { nav.popBackStack() },
                onReservado = { nav.navigate(Rutas.P_RESERVA) { popUpTo(Rutas.P_MAPA) } },
            )
        }
        composable(Rutas.P_RESERVA) {
            ReservaActivaScreen(pasajero, onVerMapa = ::volverAlMapa, onCancelada = ::volverAlMapa)
        }
        composable(Rutas.P_PAGO_OK) { PagoAprobadoScreen(pasajero, onVolver = ::volverAlMapa) }
        composable(Rutas.P_PAGO_ERROR) {
            PagoRechazadoScreen(pasajero, onReintentar = { pasajero.pagar(::resultadoPago) }, onVolver = ::volverAlMapa)
        }

        // --- Conductor ---
        composable(Rutas.C_LOGIN) {
            LoginConductorScreen(
                conductorAuth,
                onIrARegistro = { nav.navigate(Rutas.C_REGISTRO_1) { launchSingleTop = true } },
                onIngresado = { estado ->
                    if (estado == EstadoCuenta.APROBADA) irAInicio(Rutas.C_SERVICIO) else nav.navigate(Rutas.C_PENDIENTE)
                },
            )
        }
        composable(Rutas.C_REGISTRO_1) {
            RegistroConductorPaso1Screen(conductorAuth, onAtras = { nav.popBackStack() }, onContinuar = { nav.navigate(Rutas.C_REGISTRO_2) })
        }
        composable(Rutas.C_REGISTRO_2) {
            RegistroConductorPaso2Screen(
                conductorAuth,
                onAtras = { nav.popBackStack() },
                onEnviado = { nav.navigate(Rutas.C_PENDIENTE) { popUpTo(Rutas.C_LOGIN) } },
            )
        }
        composable(Rutas.C_PENDIENTE) {
            PendienteValidacionScreen(onCerrarSesion = { nav.navigate(Rutas.C_LOGIN) { popUpTo(Rutas.C_LOGIN) { inclusive = true } } })
        }
        composable(Rutas.C_SERVICIO) {
            ConBarra(nav, PestanasConductor, Rutas.C_SERVICIO) { ServicioScreen(conductor, it) }
        }
        composable(Rutas.C_AVISOS) {
            ConBarra(nav, PestanasConductor, Rutas.C_AVISOS) {
                AvisosConductorScreen(conductor, onCorreccion = { nav.navigate(Rutas.C_CORRECCION) }, it)
            }
        }
        composable(Rutas.C_RUTAS) {
            ConBarra(nav, PestanasConductor, Rutas.C_RUTAS) { RutasScreen(conductor, it) }
        }
        composable(Rutas.C_ALERTAS) {
            ConBarra(nav, PestanasConductor, Rutas.C_ALERTAS) { AlertasConductorScreen(conductor, it) }
        }
        composable(Rutas.C_CORRECCION) {
            // popBackStack con ruta: no hace nada si la pantalla ya salió de la pila.
            CorreccionOcupacionScreen(conductor, onAtras = { nav.popBackStack(Rutas.C_CORRECCION, inclusive = true) })
        }
    }
}

/** Pantallas con barra inferior. `content` recibe el Modifier con el padding del Scaffold. */
@Composable
private fun ConBarra(
    nav: NavHostController,
    pestanas: List<Pestana>,
    actual: String,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    pestanas.forEach { p ->
                        NavigationBarItem(
                            selected = p.ruta == actual,
                            onClick = {
                                if (p.ruta != actual) {
                                    nav.navigate(p.ruta) {
                                        popUpTo(pestanas.first().ruta) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(p.icono, contentDescription = null) },
                            label = { Text(p.etiqueta) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Extra.verdeTexto,
                                selectedTextColor = Extra.verdeTexto,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

/** Entrada a la app. No está en Figma: solo elige el perfil. */
@Composable
private fun SelectorPerfil(onPasajero: () -> Unit, onConductor: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(MargenPantalla),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Logo()
            Spacer(Modifier.height(40.dp))
            Text("¿Cómo usarás la app?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            BotonPrincipal("Soy pasajero", onPasajero)
            Spacer(Modifier.height(12.dp))
            BotonSecundario("Soy conductor", onConductor)
        }
    }
}
