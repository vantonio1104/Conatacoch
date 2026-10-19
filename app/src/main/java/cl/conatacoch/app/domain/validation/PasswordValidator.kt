package cl.conatacoch.app.domain.validation

/** La contraseña debe tener al menos 15 caracteres. */
object PasswordValidator {
    const val MIN_LENGTH = 15

    fun isValid(password: String): Boolean = password.length >= MIN_LENGTH
}
