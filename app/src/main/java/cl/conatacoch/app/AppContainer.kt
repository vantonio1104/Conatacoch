package cl.conatacoch.app

import cl.conatacoch.app.data.fake.FakeAdminRepository
import cl.conatacoch.app.data.fake.FakeAuthRepository
import cl.conatacoch.app.data.fake.FakeConductorRepository
import cl.conatacoch.app.data.fake.FakePasajeroRepository
import cl.conatacoch.app.data.fake.FakePreferenciasRepository
import cl.conatacoch.app.data.remote.ApiAdminRepository
import cl.conatacoch.app.data.remote.ApiAuthRepository
import cl.conatacoch.app.data.remote.ApiCliente
import cl.conatacoch.app.data.remote.ApiConductorRepository
import cl.conatacoch.app.data.remote.ApiPasajeroRepository
import cl.conatacoch.app.data.remote.OsmMapaRepository
import cl.conatacoch.app.data.remote.ServidorMapaRepository
import cl.conatacoch.app.domain.repository.AdminRepository
import cl.conatacoch.app.domain.repository.AuthRepository
import cl.conatacoch.app.domain.repository.ConductorRepository
import cl.conatacoch.app.domain.repository.MapaRepository
import cl.conatacoch.app.domain.repository.PasajeroRepository
import cl.conatacoch.app.domain.repository.PreferenciasRepository
import java.io.File

/**
 * Único lugar donde se elige la implementación.
 * Si local.properties tiene API_URL=https://... la app usa el servidor (PostgreSQL + MongoDB);
 * si no, usa los datos de ejemplo en memoria (sirve para probar sin internet).
 */
object AppContainer {
    /** Dirección del backend; vacía = datos de ejemplo. */
    val urlServidor: String = BuildConfig.API_URL
    val usaServidor: Boolean = urlServidor.isNotBlank()
    private val api: ApiCliente? = if (usaServidor) ApiCliente(urlServidor) else null

    val auth: AuthRepository = api?.let { ApiAuthRepository(it) } ?: FakeAuthRepository()

    /** Carpeta de la app donde se guardan los trazados ya calculados. La asigna MainActivity. */
    @Volatile var directorioDatos: File? = null

    /**
     * Trazados de los recorridos: primero el guardado en la base de datos (lo recalcula la administración);
     * si no hay, se calcula con OpenStreetMap (Overpass + OSRM), gratis y sin clave.
     */
    val mapa: MapaRepository = OsmMapaRepository { directorioDatos }.let { osm ->
        api?.let { ServidorMapaRepository(it, osm) } ?: osm
    }
    val pasajero: PasajeroRepository = FakePasajeroRepository(mapa).let { demo ->
        api?.let { ApiPasajeroRepository(it, mapa, demo) } ?: demo
    }
    val preferencias: PreferenciasRepository = FakePreferenciasRepository()
    val conductor: ConductorRepository = api?.let { ApiConductorRepository(it) } ?: FakeConductorRepository()
    val admin: AdminRepository = api?.let { ApiAdminRepository(it) } ?: FakeAdminRepository()
}
