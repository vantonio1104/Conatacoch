package cl.conatacoch.app.ui.conductor

import android.graphics.Bitmap
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
import kotlinx.coroutines.launch

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
    var biometria by mutableStateOf(Biometria.HUELLA)

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
        viewModelScope.launch {
            auth.registrarConductor(
                RegistroConductor(
                    rut = f.rut.trim(), nombre = f.nombre.trim(), direccion = f.direccion.trim(),
                    comuna = f.comuna.trim(), celular = f.celular.trim(), telefonoFijo = f.telefonoFijo.trim(),
                    telefonoEmergencia = f.telefonoEmergencia.trim(), correo = f.correo.trim(),
                    password = f.password, licencia = f.licencia, emisionLicencia = f.emision.trim(),
                    vencimientoLicencia = f.vencimiento.trim(), comunaEmision = f.comunaEmision.trim(),
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

    /** Paso 1 del login: correo y contraseña. La biometría se pide después. */
    fun verificarCredenciales(correo: String, password: String, onListo: (EstadoCuenta) -> Unit) {
        viewModelScope.launch {
            val estado = auth.loginConductor(correo, password)
            loginError = estado == null
            if (estado != null) onListo(estado)
        }
    }
}
