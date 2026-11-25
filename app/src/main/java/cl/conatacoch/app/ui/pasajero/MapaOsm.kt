package cl.conatacoch.app.ui.pasajero

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.ViewOutlineProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.conatacoch.app.R
import cl.conatacoch.app.domain.model.Colectivo
import cl.conatacoch.app.domain.model.EstadoOcupacion
import cl.conatacoch.app.domain.model.Punto
import cl.conatacoch.app.domain.model.Trazado
import cl.conatacoch.app.ui.theme.Extra
import cl.conatacoch.app.ui.theme.Negro
import cl.conatacoch.app.ui.theme.VerdeMarca
import cl.conatacoch.app.ui.theme.VerdeOscuro
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

/** La Cisterna / La Granja: centro de los recorridos mientras carga el trazado. */
private val CentroInicial = GeoPoint(-33.535, -70.645)

private fun Punto.geo() = GeoPoint(lat, lng)

/** Modo oscuro del mapa: invierte los colores de los mosaicos (blanco → negro). */
private val FiltroOscuro = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/**
 * Mapa de OpenStreetMap (osmdroid, gratis y sin clave) con el recorrido elegido y los colectivos.
 * [margenSuperior] es lo que tapan el buscador y la leyenda que van encima.
 */
@Composable
fun MapaRecorrido(
    estado: EstadoTrazado,
    colectivos: List<Colectivo>,
    onColectivo: (String) -> Unit,
    onReintentar: () -> Unit,
    margenSuperior: Dp,
    modifier: Modifier = Modifier,
    /** Conductor: el mapa sigue su ubicación (con botón para volver a centrar si mueve el mapa). */
    seguirUbicacion: Boolean = false,
) {
    val context = LocalContext.current
    val oscuro = Extra.oscuro
    val colorRegreso = if (oscuro) VerdeMarca.copy(alpha = 0.7f) else VerdeOscuro
    val margenPx = with(LocalDensity.current) { margenSuperior.roundToPx() }
    val alTocar by rememberUpdatedState(onColectivo)

    var ubicacionPermitida by remember { mutableStateOf(tienePermisoUbicacion(context)) }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ubicacionPermitida = tienePermisoUbicacion(context)
    }
    LaunchedEffect(Unit) {
        if (!ubicacionPermitida) {
            pedirPermiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val mapa = remember { crearMapa(context) }
    val capas = remember { CapasMapa() }

    // osmdroid necesita enterarse de cuándo la pantalla se pausa o se cierra.
    val ciclo = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> mapa.onResume()
                Lifecycle.Event.ON_PAUSE -> mapa.onPause()
                else -> Unit
            }
        }
        ciclo.addObserver(observador)
        onDispose {
            ciclo.removeObserver(observador)
            capas.miUbicacion?.disableMyLocation()
            mapa.onDetach()
        }
    }

    LaunchedEffect(oscuro) {
        mapa.overlayManager.tilesOverlay.setColorFilter(if (oscuro) FiltroOscuro else null)
        mapa.invalidate()
    }

    LaunchedEffect(ubicacionPermitida) {
        if (ubicacionPermitida && capas.miUbicacion == null) {
            capas.miUbicacion = MyLocationNewOverlay(GpsMyLocationProvider(context), mapa).also {
                it.enableMyLocation()
                if (seguirUbicacion) {
                    it.enableFollowLocation()
                    it.runOnFirstFix { mapa.post { mapa.controller.setZoom(16.0) } }
                }
                mapa.overlays.add(it)
            }
        }
    }

    // Recorrido: se dibuja y la cámara se ajusta para mostrarlo completo.
    LaunchedEffect(estado, oscuro) {
        capas.ida?.let { mapa.overlays.remove(it) }
        capas.regreso?.let { mapa.overlays.remove(it) }
        capas.ida = null
        capas.regreso = null
        val t = (estado as? EstadoTrazado.Listo)?.trazado
        if (t != null) {
            val densidad = context.resources.displayMetrics.density
            capas.regreso = linea(t.regreso, colorRegreso, 4f * densidad, discontinua = true)
            capas.ida = linea(t.ida, VerdeMarca, 6f * densidad, discontinua = false)
            // Las líneas van debajo de los colectivos y de la ubicación.
            listOfNotNull(capas.ida, capas.regreso).forEach { mapa.overlays.add(0, it) }
            // Si sigue al conductor y ya hay ubicación, no se aleja para mostrar todo el recorrido.
            val siguiendo = seguirUbicacion && capas.miUbicacion?.myLocation != null
            if (capas.encuadrado != t && !siguiendo) {
                capas.encuadrado = t
                encuadrar(mapa, t, margenPx)
            }
        }
        mapa.invalidate()
    }

    // Colectivos: se mueven los marcadores existentes y se agregan o quitan los que cambian.
    LaunchedEffect(colectivos) {
        val visibles = colectivos.filter { it.posicion != null }
        val patentes = visibles.map { it.patente }.toSet()
        capas.colectivos.keys.filter { it !in patentes }.forEach { p -> capas.colectivos.remove(p)?.let { mapa.overlays.remove(it) } }
        visibles.forEach { c ->
            val marcador = capas.colectivos.getOrPut(c.patente) {
                Marker(mapa).also { m ->
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.setInfoWindow(null)
                    m.title = c.patente
                    m.setOnMarkerClickListener { _, _ ->
                        alTocar(c.patente)
                        true
                    }
                    mapa.overlays.add(m)
                }
            }
            marcador.position = c.posicion!!.geo()
            marcador.icon = capas.icono(context, c.estado)
        }
        mapa.invalidate()
    }

    Box(modifier.clipToBounds()) {
        // clipToBounds: sin esto, al mover el mapa (y sus líneas) se dibuja encima del resto de la pantalla.
        AndroidView(factory = { mapa }, modifier = Modifier.fillMaxSize().clipToBounds())

        Column(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (estado) {
                EstadoTrazado.Cargando -> Aviso {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Cargando recorrido…", style = MaterialTheme.typography.bodyMedium)
                }
                is EstadoTrazado.Error -> Aviso {
                    Text("No se pudo cargar el recorrido", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onReintentar) { Text("Reintentar", color = Extra.verdeTexto) }
                }
                is EstadoTrazado.Listo -> Aviso {
                    LineaLeyenda(VerdeMarca, discontinua = false)
                    Text(" Ida   ", style = MaterialTheme.typography.bodyMedium)
                    LineaLeyenda(colorRegreso, discontinua = true)
                    Text(" Regreso", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (seguirUbicacion) {
            SmallFloatingActionButton(
                onClick = {
                    capas.miUbicacion?.let {
                        it.enableFollowLocation()
                        mapa.controller.setZoom(16.0)
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) { Icon(Icons.Outlined.MyLocation, contentDescription = "Centrar en mi ubicación") }
        }
        // Atribución exigida por la licencia de OpenStreetMap.
        Text(
            "© OpenStreetMap",
            Modifier.align(Alignment.BottomEnd).padding(4.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Objetos del mapa que se reutilizan entre recomposiciones. */
private class CapasMapa {
    var ida: Polyline? = null
    var regreso: Polyline? = null
    var encuadrado: Trazado? = null
    var miUbicacion: MyLocationNewOverlay? = null
    val colectivos = mutableMapOf<String, Marker>()
    private val iconos = mutableMapOf<EstadoOcupacion, BitmapDrawable>()

    fun icono(context: Context, estado: EstadoOcupacion) = iconos.getOrPut(estado) { iconoColectivo(context, estado) }
}

private fun crearMapa(context: Context): MapView {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        // Caché de mosaicos dentro de la app (no pide permiso de almacenamiento).
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
    return MapView(context).apply {
        // El mapa no debe dibujar fuera de su rectángulo (dentro de Compose no se recorta solo).
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BOUNDS
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 10.0
        maxZoomLevel = 19.0
        controller.setZoom(13.0)
        controller.setCenter(CentroInicial)
    }
}

private fun linea(puntos: List<Punto>, color: Color, ancho: Float, discontinua: Boolean): Polyline? {
    if (puntos.size < 2) return null
    return Polyline().apply {
        setPoints(puntos.map { it.geo() })
        outlinePaint.color = color.toArgb()
        outlinePaint.strokeWidth = ancho
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        if (discontinua) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(ancho * 2.5f, ancho * 1.5f), 0f)
        infoWindow = null
    }
}

/** Ajusta la vista al recorrido, dejando libre la parte de arriba (buscador y leyenda). */
private fun encuadrar(mapa: MapView, t: Trazado, margenSuperiorPx: Int) {
    val puntos = (t.ida + t.regreso).map { it.geo() }
    if (puntos.isEmpty()) return
    val caja = BoundingBox.fromGeoPointsSafe(puntos)
    val hacer = {
        val alto = mapa.height.coerceAtLeast(1)
        // Se estira la caja hacia el norte en la proporción que tapa el buscador.
        val extra = caja.latitudeSpan * margenSuperiorPx / (alto - margenSuperiorPx).coerceAtLeast(1)
        val ajustada = BoundingBox(caja.latNorth + extra, caja.lonEast, caja.latSouth, caja.lonWest)
        mapa.zoomToBoundingBox(ajustada, true, 64)
    }
    if (mapa.width > 0 && mapa.height > 0) hacer() else mapa.addOnFirstLayoutListener { _, _, _, _, _ -> hacer() }
}

/** Círculo de color según ocupación, con borde blanco y el auto encima (como en Figma). */
private fun iconoColectivo(context: Context, estado: EstadoOcupacion): BitmapDrawable {
    val densidad = context.resources.displayMetrics.density
    val tam = (40 * densidad).toInt()
    val bitmap = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = tam / 2f
    pincel.color = android.graphics.Color.WHITE
    canvas.drawCircle(r, r, r, pincel)
    pincel.color = colorEstado(estado).toArgb()
    canvas.drawCircle(r, r, r - 3 * densidad, pincel)
    ContextCompat.getDrawable(context, R.drawable.ic_colectivo)?.mutate()?.let { auto ->
        auto.setTint(if (estado == EstadoOcupacion.LLENO) android.graphics.Color.WHITE else Negro.toArgb())
        val lado = (20 * densidad).toInt()
        val inicio = (tam - lado) / 2
        auto.setBounds(inicio, inicio, inicio + lado, inicio + lado)
        auto.draw(canvas)
    }
    return BitmapDrawable(context.resources, bitmap)
}

private fun tienePermisoUbicacion(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Aviso(contenido: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            contenido()
        }
    }
}

@Composable
private fun LineaLeyenda(color: Color, discontinua: Boolean) {
    if (discontinua) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.size(width = 5.dp, height = 4.dp).background(color)) }
        }
    } else {
        Box(Modifier.size(width = 21.dp, height = 4.dp).clip(CircleShape).background(color))
    }
}
