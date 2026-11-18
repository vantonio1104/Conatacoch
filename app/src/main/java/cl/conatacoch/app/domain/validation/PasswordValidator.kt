package cl.conatacoch.app.domain.validation

/** La contraseña debe tener entre 8 y 64 caracteres (un emoji cuenta como uno). Igual que el backend. */
object PasswordValidator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    fun isValid(password: String): Boolean =
        password.codePointCount(0, password.length) in MIN_LENGTH..MAX_LENGTH
}
