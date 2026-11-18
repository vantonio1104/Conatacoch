package cl.conatacoch.app.data.remote

import cl.conatacoch.app.domain.model.Calle
import cl.conatacoch.app.domain.model.Punto
import cl.conatacoch.app.domain.model.Recorrido
import cl.conatacoch.app.domain.model.Trazado
import cl.conatacoch.app.domain.repository.MapaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Calcula el trazado real de un recorrido con servicios gratuitos de OpenStreetMap (sin clave):
 *  1. Overpass API: busca las calles del recorrido y el punto exacto donde se cruzan dos calles seguidas.
 *  2. OSRM: une esos cruces siguiendo las calles (ruta en auto).
 * El resultado se guarda en [directorio] para no volver a pedirlo (y para que la demo funcione aunque
 * los servidores públicos estén lentos). Más adelante conviene que lo calcule y guarde el backend.
 */
class OsmMapaRepository(private val directorio: () -> File?) : MapaRepository {

    private val memoria = mutableMapOf<String, Trazado>()
    private val mutex = Mutex()

    override suspend fun trazado(recorrido: Recorrido): Trazado {
        mutex.withLock {
            memoria[recorrido.folio]?.let { return it }
            val t = withContext(Dispatchers.IO) { leerCache(recorrido.folio) ?: calcular(recorrido).also { guardarCache(recorrido.folio, it) } }
            memoria[recorrido.folio] = t
            return t
        }
    }

    override suspend fun recalcular(recorrido: Recorrido): Trazado = mutex.withLock {
        val t = withContext(Dispatchers.IO) { calcular(recorrido).also { guardarCache(recorrido.folio, it) } }
        memoria[recorrido.folio] = t
        t
    }

    private fun calcular(recorrido: Recorrido): Trazado {
        val calles = (recorrido.ida + recorrido.regreso).distinctBy { it.nombre }
        val nodosPorCalle = buscarCalles(calles)
        val ida = cruces(recorrido.ida, nodosPorCalle)
        val regreso = cruces(recorrido.regreso, nodosPorCalle)
        check(ida.size >= 2 || regreso.size >= 2) { "No se encontraron las calles del recorrido en OpenStreetMap" }
        return Trazado(ida = ruta(ida), regreso = ruta(regreso))
    }

    // --- 1. Overpass: calles y cruces ---

    /** Para cada nombre de calle, los nodos (id → punto) de las vías de OSM que tienen ese nombre. */
    private fun buscarCalles(calles: List<Calle>): Map<String, Map<Long, Punto>> {
        val patrones = calles.associate { it.nombre to Regex("^${patronCalle(it.nombre)}$", RegexOption.IGNORE_CASE) }
        val todos = calles.joinToString("|") { patronCalle(it.nombre) }
        val consulta = """
            [out:json][timeout:60];
            way["highway"]["name"~"^($todos)$",i]($ZONA);
            out body;
            >;
            out skel qt;
        """.trimIndent()
        val json = JSONObject(post(URL_OVERPASS, "data=" + URLEncoder.encode(consulta, "UTF-8"), "application/x-www-form-urlencoded"))
        val elementos = json.getJSONArray("elements")

        val nodos = HashMap<Long, Punto>()
        val vias = mutableListOf<Pair<String, List<Long>>>()
        for (i in 0 until elementos.length()) {
            val e = elementos.getJSONObject(i)
            when (e.getString("type")) {
                "node" -> nodos[e.getLong("id")] = Punto(e.getDouble("lat"), e.getDouble("lon"))
                "way" -> {
                    val nombre = e.optJSONObject("tags")?.optString("name").orEmpty()
                    val ids = e.getJSONArray("nodes")
                    vias += nombre to List(ids.length()) { ids.getLong(it) }
                }
            }
        }
        return patrones.mapValues { (_, patron) ->
            vias.filter { (nombre, _) -> patron.matches(nombre) }
                .flatMap { (_, ids) -> ids.mapNotNull { id -> nodos[id]?.let { id to it } } }
                .toMap()
        }
    }

    /** Punto de cruce de cada par de calles seguidas. Si un cruce no se encuentra, se omite. */
    private fun cruces(calles: List<Calle>, nodosPorCalle: Map<String, Map<Long, Punto>>): List<Punto> {
        val pares = calles.zipWithNext().filter { (a, b) -> a.nombre != b.nombre }
        val candidatos = pares.map { (a, b) ->
            candidatosCruce(nodosPorCalle[a.nombre].orEmpty(), nodosPorCalle[b.nombre].orEmpty())
        }
        val resultado = mutableListOf<Punto>()
        candidatos.forEachIndexed { i, opciones ->
            if (opciones.isEmpty()) return@forEachIndexed
            // Calles con el mismo nombre en otra comuna: se elige el cruce más cercano al anterior
            // (o, para el primero, al siguiente cruce del recorrido).
            val referencia = resultado.lastOrNull()
                ?: candidatos.drop(i + 1).firstOrNull { it.isNotEmpty() }?.first()
            resultado += if (referencia == null) opciones.first() else opciones.minBy { distancia(it, referencia) }
        }
        return resultado
    }

    // --- 2. OSRM: ruta por las calles ---

    private fun ruta(puntos: List<Punto>): List<Punto> {
        if (puntos.size < 2) return emptyList()
        val resultado = mutableListOf<Punto>()
        tramos(puntos, MAX_PUNTOS_POR_PEDIDO).forEach { tramo ->
            val coords = tramo.joinToString(";") { "${it.lng},${it.lat}" }
            val json = JSONObject(get("$URL_OSRM/$coords?overview=full&geometries=polyline&continue_straight=true"))
            check(json.optString("code") == "Ok") { "OSRM no encontró ruta: ${json.optString("message")}" }
            val parte = decodificarPolilinea(json.getJSONArray("routes").getJSONObject(0).getString("geometry"))
            resultado += if (resultado.isEmpty()) parte else parte.drop(1)
        }
        return resultado
    }

    // --- HTTP y caché ---

    private fun get(url: String): String = pedir(url) { requestMethod = "GET" }

    private fun post(url: String, cuerpo: String, tipo: String): String = pedir(url) {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", tipo)
        outputStream.use { it.write(cuerpo.toByteArray()) }
    }

    private fun pedir(url: String, configurar: HttpURLConnection.() -> Unit): String {
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            // Los servidores públicos de OSM piden identificar la app.
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            conexion.configurar()
            val codigo = conexion.responseCode
            val texto = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(codigo in 200..299) { "El servidor de mapas respondió $codigo" }
            return texto
        } finally {
            conexion.disconnect()
        }
    }

    private fun archivo(folio: String) = directorio()?.let { File(it, "trazados/v1-$folio.json") }

    private fun leerCache(folio: String): Trazado? = runCatching {
        val f = archivo(folio)?.takeIf { it.exists() } ?: return null
        val json = JSONObject(f.readText())
        Trazado(leerPuntos(json.getJSONArray("ida")), leerPuntos(json.getJSONArray("regreso")))
    }.getOrNull()

    private fun guardarCache(folio: String, t: Trazado) {
        runCatching {
            val f = archivo(folio) ?: return
            f.parentFile?.mkdirs()
            f.writeText(JSONObject().put("ida", escribirPuntos(t.ida)).put("regreso", escribirPuntos(t.regreso)).toString())
        }
    }

    private fun leerPuntos(a: JSONArray) = List(a.length()) { a.getJSONArray(it).let { p -> Punto(p.getDouble(0), p.getDouble(1)) } }
    private fun escribirPuntos(l: List<Punto>) = JSONArray().apply { l.forEach { put(JSONArray().put(it.lat).put(it.lng)) } }

    private companion object {
        const val URL_OVERPASS = "https://overpass-api.de/api/interpreter"
        const val URL_OSRM = "https://router.project-osrm.org/route/v1/driving"
        const val USER_AGENT = "CONATACOCH-app/0.1 (proyecto universitario INACAP)"
        const val MAX_PUNTOS_POR_PEDIDO = 25
        /** Zona sur de Santiago (sur, oeste, norte, este): La Cisterna, El Bosque, San Ramón, La Granja, La Florida, La Pintana, San Bernardo. */
        const val ZONA = "-33.68,-70.76,-33.47,-70.52"
    }
}

/**
 * Expresión regular para encontrar una calle del CSV en OpenStreetMap, tolerando
 * "Av."/"Avenida", tildes, mayúsculas y sufijos Norte/Sur/Oriente/Poniente.
 */
internal fun patronCalle(nombre: String): String {
    val sinPrefijo = nombre.trim().replace(Regex("^(Av\\.?|Avda\\.?|Avenida)\\s+", RegexOption.IGNORE_CASE), "")
    val palabras = sinPrefijo.split(' ', '.').filter { it.isNotBlank() }
    val nucleo = palabras.joinToString("[ .]+") { palabra -> palabra.map { letraFlexible(it) }.joinToString("") }
    return "(Avenida )?$nucleo( (Norte|Sur|Oriente|Poniente))?"
}

private fun letraFlexible(c: Char): String = when (c.lowercaseChar()) {
    'a', 'á' -> "[aá]"
    'e', 'é' -> "[eé]"
    'i', 'í' -> "[ií]"
    'o', 'ó' -> "[oó]"
    'u', 'ú', 'ü' -> "[uúü]"
    'n', 'ñ' -> "[nñ]"
    in 'a'..'z', in '0'..'9' -> c.toString()
    else -> "."
}

/** Nodos compartidos por ambas calles; si no comparten ninguno, el par más cercano (a menos de ~150 m). */
internal fun candidatosCruce(a: Map<Long, Punto>, b: Map<Long, Punto>): List<Punto> {
    if (a.isEmpty() || b.isEmpty()) return emptyList()
    val comunes = a.keys.intersect(b.keys).map { a.getValue(it) }
    if (comunes.isNotEmpty()) return comunes
    var mejor: Pair<Punto, Punto>? = null
    var mejorDist = Double.MAX_VALUE
    for (p in a.values) for (q in b.values) {
        val d = distancia(p, q)
        if (d < mejorDist) {
            mejorDist = d
            mejor = p to q
        }
    }
    val (p, q) = mejor ?: return emptyList()
    return if (mejorDist < 0.0015) listOf(Punto((p.lat + q.lat) / 2, (p.lng + q.lng) / 2)) else emptyList()
}

/** Distancia aproximada en grados (suficiente para comparar puntos cercanos). */
internal fun distancia(a: Punto, b: Punto): Double {
    val dx = (b.lng - a.lng) * kotlin.math.cos(Math.toRadians(a.lat))
    val dy = b.lat - a.lat
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/** Parte la lista en tramos de [max] elementos; cada tramo empieza donde terminó el anterior. */
internal fun <T> tramos(lista: List<T>, max: Int): List<List<T>> {
    require(max >= 2)
    if (lista.size <= max) return listOf(lista)
    val resultado = mutableListOf<List<T>>()
    var inicio = 0
    while (inicio < lista.size - 1) {
        val fin = minOf(inicio + max, lista.size)
        resultado += lista.subList(inicio, fin)
        inicio = fin - 1
    }
    return resultado
}

/** Formato "encoded polyline" (precisión 5), el mismo que usan OSRM y Google. */
internal fun decodificarPolilinea(codigo: String): List<Punto> {
    val puntos = mutableListOf<Punto>()
    var i = 0
    var lat = 0
    var lng = 0
    fun siguiente(): Int {
        var resultado = 0
        var desplazamiento = 0
        var b: Int
        do {
            b = codigo[i++].code - 63
            resultado = resultado or ((b and 0x1f) shl desplazamiento)
            desplazamiento += 5
        } while (b >= 0x20)
        return if (resultado and 1 != 0) (resultado shr 1).inv() else resultado shr 1
    }
    while (i < codigo.length) {
        lat += siguiente()
        lng += siguiente()
        puntos += Punto(lat / 1e5, lng / 1e5)
    }
    return puntos
}
