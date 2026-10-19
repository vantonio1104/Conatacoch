package cl.conatacoch.app.domain.validation

/** La contraseña debe tener al menos 15 caracteres (un emoji cuenta como uno). */
object PasswordValidator {
    const val MIN_LENGTH = 15

    fun isValid(password: String): Boolean =
        password.codePointCount(0, password.length) >= MIN_LENGTH
}
