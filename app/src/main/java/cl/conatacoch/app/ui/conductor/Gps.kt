package cl.conatacoch.app.ui.conductor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private fun tienePermiso(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Mientras [activo] (en servicio), entrega la ubicación del teléfono cada ~5 segundos.
 * Pide el permiso de ubicación la primera vez. Funciona con la pantalla abierta.
 */
@SuppressLint("MissingPermission")
@Composable
fun SeguirUbicacion(activo: Boolean, onUbicacion: (Location) -> Unit) {
    val context = LocalContext.current
    var permitido by remember { mutableStateOf(tienePermiso(context)) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitido = tienePermiso(context)
        if (!permitido) {
            Toast.makeText(context, "Sin permiso de ubicación los pasajeros no verán tu colectivo en el mapa", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(activo) {
        if (activo && !permitido) {
            pedir.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val alRecibir by rememberUpdatedState(onUbicacion)

    DisposableEffect(activo, permitido) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Se implementan todos los métodos: en Android 9 o anterior no tienen implementación por defecto.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = alRecibir(location)

            @Deprecated("Solo se usa en Android 9 o anterior")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        if (activo && permitido) {
            val proveedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            proveedores.forEach { lm.requestLocationUpdates(it, 5_000L, 5f, listener, Looper.getMainLooper()) }
            proveedores.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { alRecibir(it) }
        }
        onDispose { lm.removeUpdates(listener) }
    }
}
