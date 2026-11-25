package cl.conatacoch.app.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Error que se puede mostrar tal cual al usuario (viene del servidor o de la conexión). */
class ErrorApi(mensaje: String, val codigo: Int = 0) : Exception(mensaje)

/**
 * Cliente mínimo del backend (backend/server.js). Guarda el token de la sesión iniciada.
 * Usa HttpURLConnection y org.json, que vienen con Android (sin librerías extra).
 */
class ApiCliente(baseUrl: String) {

    private val base = baseUrl.trimEnd('/')

    private val _token = MutableStateFlow<String?>(null)
    /** Token de la sesión; null = sin sesión (invitado o sesión cerrada). */
    val token: StateFlow<String?> = _token

    fun iniciarSesion(token: String) {
        _token.value = token
    }

    /** Para avisos que no deben esperar a la pantalla, como cerrar la sesión en el servidor. */
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Olvida el token y avisa al servidor para que deje de servir (sin esperar la respuesta). */
    fun cerrarSesion() {
        val token = _token.value ?: return
        _token.value = null
        alcance.launch { runCatching { pedir("POST", "/api/auth/logout", JSONObject(), token) } }
    }

    suspend fun get(ruta: String): String = pedir("GET", ruta, null)
    suspend fun post(ruta: String, cuerpo: JSONObject): String = pedir("POST", ruta, cuerpo)
    suspend fun put(ruta: String, cuerpo: JSONObject): String = pedir("PUT", ruta, cuerpo)
    suspend fun delete(ruta: String): String = pedir("DELETE", ruta, null)

    suspend fun getArray(ruta: String) = JSONArray(get(ruta))

    suspend fun postObjeto(ruta: String, cuerpo: JSONObject) = JSONObject(post(ruta, cuerpo))

    private suspend fun pedir(metodo: String, ruta: String, cuerpo: JSONObject?, token: String? = _token.value): String = withContext(Dispatchers.IO) {
        val conexion = try {
            (URL("$base$ruta").openConnection() as HttpURLConnection).apply {
                requestMethod = metodo
                // El servidor gratis puede tardar hasta un minuto en "despertar".
                connectTimeout = 30_000
                readTimeout = 70_000
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (cuerpo != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
        } catch (e: IOException) {
            throw ErrorApi(SIN_CONEXION)
        }
        try {
            if (cuerpo != null) conexion.outputStream.use { it.write(cuerpo.toString().toByteArray()) }
            val codigo = conexion.responseCode
            val texto = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (codigo !in 200..299) {
                val mensaje = runCatching { JSONObject(texto).getString("error") }.getOrNull()
                throw ErrorApi(mensaje ?: "El servidor respondió con un error ($codigo)", codigo)
            }
            texto
        } catch (e: IOException) {
            throw ErrorApi(SIN_CONEXION)
        } finally {
            conexion.disconnect()
        }
    }

    companion object {
        const val SIN_CONEXION = "No se pudo conectar con el servidor. Revisa tu conexión a internet."
    }
}

/** Texto del campo o null si no viene o es null. */
internal fun JSONObject.textoONulo(campo: String): String? = if (!has(campo) || isNull(campo)) null else getString(campo)

/** "2026-11-11T05:31:19.624Z" → fecha y hora del teléfono. */
internal fun fechaLocal(iso: String): java.time.LocalDateTime =
    java.time.LocalDateTime.ofInstant(java.time.Instant.parse(iso), java.time.ZoneId.systemDefault())

/** Para las listas que vienen del servidor. */
internal fun <T> JSONArray.mapear(f: (JSONObject) -> T): List<T> = List(length()) { f(getJSONObject(it)) }
