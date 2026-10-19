package cl.conatacoch.app

import cl.conatacoch.app.data.fake.FakeAuthRepository
import cl.conatacoch.app.data.fake.FakeConductorRepository
import cl.conatacoch.app.data.fake.FakePasajeroRepository
import cl.conatacoch.app.data.fake.FakePreferenciasRepository
import cl.conatacoch.app.domain.repository.AuthRepository
import cl.conatacoch.app.domain.repository.ConductorRepository
import cl.conatacoch.app.domain.repository.PasajeroRepository
import cl.conatacoch.app.domain.repository.PreferenciasRepository

/** Único lugar donde se elige la implementación. Para usar la API, cambiar aquí. */
object AppContainer {
    val auth: AuthRepository = FakeAuthRepository()
    val pasajero: PasajeroRepository = FakePasajeroRepository()
    val preferencias: PreferenciasRepository = FakePreferenciasRepository()
    val conductor: ConductorRepository = FakeConductorRepository()
}
