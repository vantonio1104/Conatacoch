package cl.conatacoch.app.domain.validation

/** Valida un RUT chileno por dígito verificador (módulo 11). Acepta puntos y guion opcionales. */
object RutValidator {
    fun isValid(rut: String): Boolean {
        val clean = rut.replace(".", "").replace("-", "").uppercase()
        if (clean.length !in 2..9) return false
        val body = clean.dropLast(1)
        if (!body.all { it.isDigit() }) return false
        val sum = body.reversed().withIndex().sumOf { (i, c) -> c.digitToInt() * (2 + i % 6) }
        val dv = when (val r = 11 - sum % 11) {
            11 -> '0'
            10 -> 'K'
            else -> '0' + r
        }
        return clean.last() == dv
    }
}
