package cl.conatacoch.app.domain.validation

/**
 * Valida un RUT chileno de 7 u 8 dígitos por su dígito verificador (módulo 11).
 * Acepta "12.345.678-5", "12345678-5" o "123456785"; K en mayúscula o minúscula.
 */
object RutValidator {
    private val FORMATO = Regex("""^\d{1,2}\.?\d{3}\.?\d{3}-?[\dK]$""")

    fun isValid(rut: String): Boolean {
        val texto = rut.trim().uppercase()
        if (!FORMATO.matches(texto)) return false
        val clean = texto.replace(".", "").replace("-", "")
        val body = clean.dropLast(1)
        val sum = body.reversed().withIndex().sumOf { (i, c) -> c.digitToInt() * (2 + i % 6) }
        val dv = when (val r = 11 - sum % 11) {
            11 -> '0'
            10 -> 'K'
            else -> '0' + r
        }
        return clean.last() == dv
    }
}
