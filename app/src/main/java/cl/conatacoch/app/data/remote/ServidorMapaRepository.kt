package cl.conatacoch.app.data.remote

import cl.conatacoch.app.domain.model.Punto
import cl.conatacoch.app.domain.model.Recorrido
import cl.conatacoch.app.domain.model.Trazado
import cl.conatacoch.app.domain.repository.MapaRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Primero usa el trazado guardado en la base de datos (lo recalcula la administración);
 * si el recorrido aún no tiene uno, o no hay conexión, lo calcula con OpenStreetMap ([osm]).
 */
class ServidorMapaRepository(
    private val api: ApiCliente,
    private val osm: MapaRepository,
) : MapaRepository {

    private val memoria = ConcurrentHashMap<String, Trazado>()
    private val mutex = Mutex()

    override suspend fun trazado(recorrido: Recorrido): Trazado {
        memoria[recorrido.folio]?.let { return it }
        val delServidor = mutex.withLock {
            memoria[recorrido.folio] ?: runCatching { pedir(recorrido.folio) }.getOrNull()
        }
        if (delServidor != null) {
            memoria[recorrido.folio] = delServidor
            return delServidor
        }
        return osm.trazado(recorrido)
    }

    override suspend fun recalcular(recorrido: Recorrido): Trazado =
        osm.recalcular(recorrido).also { memoria[recorrido.folio] = it }

    private suspend fun pedir(folio: String): Trazado? {
        val json = try {
            JSONObject(api.get("/api/rutas/$folio/trazado"))
        } catch (e: ErrorApi) {
            if (e.codigo == 404) return null else throw e
        }
        val t = Trazado(puntos(json.getJSONArray("ida")), puntos(json.getJSONArray("regreso")))
        return t.takeIf { it.ida.size >= 2 || it.regreso.size >= 2 }
    }

    private fun puntos(a: JSONArray) = List(a.length()) { a.getJSONArray(it).let { p -> Punto(p.getDouble(0), p.getDouble(1)) } }
}
