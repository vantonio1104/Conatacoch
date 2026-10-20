package cl.conatacoch.app.ui.pasajero

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.conatacoch.app.domain.repository.AuthRepository
import cl.conatacoch.app.domain.validation.PasswordValidator
import kotlinx.coroutines.launch

class PasajeroAuthViewModel(private val auth: AuthRepository) : ViewModel() {

    /** Nombre de quien inició sesión (se muestra en Preferencias). */
    var nombre by mutableStateOf("")
        private set

    /** Correo al que se envió el código de verificación. */
    var correoPendiente by mutableStateOf("")
        private set

    var nombreError by mutableStateOf(false)
        private set
    var correoError by mutableStateOf(false)
        private set
    var passwordError by mutableStateOf(false)
        private set
    var codigoError by mutableStateOf(false)
        private set
    var loginError by mutableStateOf(false)
        private set

    fun registrar(nombre: String, correo: String, password: String, onListo: () -> Unit) {
        nombreError = nombre.isBlank()
        correoError = !Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()
        passwordError = !PasswordValidator.isValid(password)
        if (nombreError || correoError || passwordError) return
        viewModelScope.launch {
            auth.registrarPasajero(nombre.trim(), correo.trim(), password)
            correoPendiente = correo.trim()
            this@PasajeroAuthViewModel.nombre = nombre.trim()
            onListo()
        }
    }

    fun reenviarCodigo() {
        viewModelScope.launch { auth.reenviarCodigo(correoPendiente) }
    }

    fun verificar(codigo: String, onListo: () -> Unit) {
        viewModelScope.launch {
            codigoError = !auth.verificarCodigo(correoPendiente, codigo)
            if (!codigoError) onListo()
        }
    }

    fun ingresar(correo: String, password: String, onListo: () -> Unit) {
        viewModelScope.launch {
            // Mensaje genérico: no se revela si falló el correo o la contraseña.
            val encontrado = auth.loginPasajero(correo, password)
            loginError = encontrado == null
            if (encontrado != null) {
                nombre = encontrado
                onListo()
            }
        }
    }
}
