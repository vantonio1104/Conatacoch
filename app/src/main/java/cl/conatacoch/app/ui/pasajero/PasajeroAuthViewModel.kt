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

    /**
     * true = usa la app sin cuenta (como la app de Red). Puede ver el mapa, los recorridos
     * y los colectivos; para reservar, pagar y ver su historial necesita una cuenta.
     */
    var invitado by mutableStateOf(false)
        private set

    /** Mensaje del servidor o de la conexión (correo ya registrado, sin internet, etc.). */
    var errorServidor by mutableStateOf<String?>(null)
        private set

    /** true mientras se espera al servidor (para no enviar dos veces). */
    var cargando by mutableStateOf(false)
        private set

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

    fun entrarComoInvitado() {
        invitado = true
        nombre = ""
    }

    fun cerrarSesion() {
        auth.cerrarSesion()
        invitado = false
        nombre = ""
    }

    var nombreError by mutableStateOf(false)
        private set
    var correoError by mutableStateOf(false)
        private set
    var passwordError by mutableStateOf(false)
        private set
    var loginError by mutableStateOf(false)
        private set

    fun registrar(nombre: String, correo: String, password: String, onListo: () -> Unit) {
        nombreError = nombre.isBlank()
        correoError = !Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()
        passwordError = !PasswordValidator.isValid(password)
        if (nombreError || correoError || passwordError) return
        conServidor {
            auth.registrarPasajero(nombre.trim(), correo.trim(), password)
            this@PasajeroAuthViewModel.nombre = nombre.trim()
            invitado = false
            onListo()
        }
    }

    fun ingresar(correo: String, password: String, onListo: () -> Unit) {
        conServidor {
            // Mensaje genérico: no se revela si falló el correo o la contraseña.
            val encontrado = auth.loginPasajero(correo, password)
            loginError = encontrado == null
            if (encontrado != null) {
                nombre = encontrado
                invitado = false
                onListo()
            }
        }
    }
}
