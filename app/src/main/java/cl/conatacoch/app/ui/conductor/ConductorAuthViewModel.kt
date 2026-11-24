package cl.conatacoch.app.ui.conductor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.conatacoch.app.domain.model.ClaseLicencia
import cl.conatacoch.app.domain.model.EstadoCuenta
import cl.conatacoch.app.domain.model.RegistroConductor
import cl.conatacoch.app.domain.repository.AuthRepository
import cl.conatacoch.app.domain.validation.PasswordValidator
import cl.conatacoch.app.domain.validation.RutValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class FormConductor(
    val rut: String = "",
    val nombre: String = "",
    val direccion: String = "",
    val comuna: String = "",
    val celular: String = "",
    val telefonoFijo: String = "",
    val telefonoEmergencia: String = "",
    val correo: String = "",
    val password: String = "",
    val licencia: ClaseLicencia = ClaseLicencia.A2,
    val emision: String = "",
    val vencimiento: String = "",
    val comunaEmision: String = "",
)

enum class Biometria { HUELLA, ROSTRO }

class ConductorAuthViewModel(private val auth: AuthRepository) : ViewModel() {

    var form by mutableStateOf(FormConductor())
    var fotoFrente by mutableStateOf<Bitmap?>(null)
    var fotoReverso by mutableStateOf<Bitmap?>(null)

    /** Lee la foto que tomó la cámara, la reduce y borra el archivo (datos personales: no quedan en el teléfono). */
    fun usarFoto(archivo: File, frente: Boolean) {
        viewModelScope.launch {
            val foto = withContext(Dispatchers.IO) { fotoReducida(archivo).also { archivo.delete() } }
            if (frente) fotoFrente = foto else fotoReverso = foto
        }
    }
    var biometria by mutableStateOf(Biometria.HUELLA)

    /** Mensaje del servidor o de la conexión (RUT ya registrado, sin internet, etc.). */
    var errorServidor by mutableStateOf<String?>(null)
        private set
    private var cargando = false

    /** Ejecuta una llamada al servidor mostrando el error en pantalla en vez de cerrar la app. */
    private fun conServidor(accion: suspend () -> Unit) {
        if (cargando) return
        errorServidor = null
        cargando = true
        viewModelScope.launch {
            try {
                accion()
            } catch (e: Exception) {
                errorServidor = e.message ?: "Ocurrió un error. Inténtalo de nuevo."
            } finally {
                cargando = false
            }
        }
    }

    private var revisarPaso1 by mutableStateOf(false)
    private var revisarPaso2 by mutableStateOf(false)

    // Errores visibles solo después de intentar continuar.
    fun vacio(valor: String, paso: Int = 1) = (if (paso == 1) revisarPaso1 else revisarPaso2) && valor.isBlank()
    val rutError get() = revisarPaso1 && !RutValidator.isValid(form.rut)
    val correoError get() = revisarPaso1 && !Patterns.EMAIL_ADDRESS.matcher(form.correo.trim()).matches()
    val passwordError get() = revisarPaso1 && !PasswordValidator.isValid(form.password)
    val fotosError get() = revisarPaso2 && (fotoFrente == null || fotoReverso == null)

    fun continuarPaso1(onListo: () -> Unit) {
        revisarPaso1 = true
        val f = form
        // El teléfono fijo es opcional.
        val obligatorios = listOf(f.nombre, f.direccion, f.comuna, f.celular, f.telefonoEmergencia)
        if (obligatorios.any { it.isBlank() } || rutError || correoError || passwordError) return
        onListo()
    }

    fun enviarRegistro(onListo: () -> Unit) {
        revisarPaso2 = true
        val f = form
        if (listOf(f.emision, f.vencimiento, f.comunaEmision).any { it.isBlank() } || fotosError) return
        conServidor {
            auth.registrarConductor(
                RegistroConductor(
                    rut = f.rut.trim(), nombre = f.nombre.trim(), direccion = f.direccion.trim(),
                    comuna = f.comuna.trim(), celular = f.celular.trim(), telefonoFijo = f.telefonoFijo.trim(),
                    telefonoEmergencia = f.telefonoEmergencia.trim(), correo = f.correo.trim(),
                    password = f.password, licencia = f.licencia, emisionLicencia = f.emision.trim(),
                    vencimientoLicencia = f.vencimiento.trim(), comunaEmision = f.comunaEmision.trim(),
                    fotoFrente = fotoFrente?.comoJpeg(), fotoReverso = fotoReverso?.comoJpeg(),
                )
            )
            form = FormConductor()
            fotoFrente = null
            fotoReverso = null
            revisarPaso1 = false
            revisarPaso2 = false
            onListo()
        }
    }

    var loginError by mutableStateOf(false)
        private set

    fun cerrarSesion() = auth.cerrarSesion()

    /** Paso 1 del login: correo y contraseña. La biometría se pide después. */
    fun verificarCredenciales(correo: String, password: String, onListo: (EstadoCuenta) -> Unit) {
        conServidor {
            val estado = auth.loginConductor(correo, password)
            loginError = estado == null
            if (estado != null) onListo(estado)
        }
    }
}

/** Lado mayor de la foto que se envía: suficiente para leer la licencia. */
private const val LADO_MAXIMO = 1600

/** El servidor acepta hasta 1 MB por foto; se deja margen. */
private const val BYTES_MAXIMOS = 900_000

/** Decodifica la foto achicándola a [LADO_MAXIMO] px en su lado mayor (sin cargarla entera en memoria). */
private fun fotoReducida(archivo: File): Bitmap? {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(archivo.path, medidas)
    var muestra = 1
    while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= LADO_MAXIMO) muestra *= 2
    val foto = BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return null
    val mayor = maxOf(foto.width, foto.height)
    if (mayor <= LADO_MAXIMO) return foto
    return Bitmap.createScaledBitmap(foto, foto.width * LADO_MAXIMO / mayor, foto.height * LADO_MAXIMO / mayor, true)
}

/** Foto para enviar al servidor: JPEG que baja la calidad hasta quedar bajo [BYTES_MAXIMOS]. */
private fun Bitmap.comoJpeg(): ByteArray {
    var calidad = 85
    while (true) {
        val bytes = ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, calidad, it) }.toByteArray()
        if (bytes.size <= BYTES_MAXIMOS || calidad <= 40) return bytes
        calidad -= 15
    }
}
