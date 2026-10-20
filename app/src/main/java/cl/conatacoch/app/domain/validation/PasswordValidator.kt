package cl.conatacoch.app.domain.validation

/** La contraseña debe tener entre 15 y 128 caracteres (un emoji cuenta como uno). */
object PasswordValidator {
    const val MIN_LENGTH = 15
    const val MAX_LENGTH = 128

    fun isValid(password: String): Boolean =
        password.codePointCount(0, password.length) in MIN_LENGTH..MAX_LENGTH
}
