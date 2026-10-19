package cl.conatacoch.app.ui.conductor

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Pide confirmación con el bloqueo propio del teléfono. Nada sale del dispositivo.
 * Android decide si usa huella o rostro; la app no puede forzar uno de los dos.
 * Con `conPin` se usa el PIN, patrón o contraseña del teléfono.
 */
fun pedirBiometria(
    activity: FragmentActivity,
    conPin: Boolean,
    onExito: () -> Unit,
    onError: (String) -> Unit,
) {
    val autenticadores = when {
        !conPin -> BIOMETRIC_WEAK
        // Solo PIN/patrón está soportado desde Android 11.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> DEVICE_CREDENTIAL
        else -> BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
    if (BiometricManager.from(activity).canAuthenticate(autenticadores) != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Configura huella, rostro o un bloqueo de pantalla en el teléfono.")
        return
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Confirma que eres tú")
        .setSubtitle("App del conductor")
        .setAllowedAuthenticators(autenticadores)
        .apply { if (autenticadores == BIOMETRIC_WEAK) setNegativeButtonText("Cancelar") }
        .build()
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onExito()

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelado = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            if (!cancelado) onError(errString.toString())
        }
    }
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
}
