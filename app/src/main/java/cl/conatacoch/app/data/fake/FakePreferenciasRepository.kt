package cl.conatacoch.app.data.fake

import cl.conatacoch.app.domain.model.Preferencias
import cl.conatacoch.app.domain.repository.PreferenciasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakePreferenciasRepository : PreferenciasRepository {
    private val _preferencias = MutableStateFlow(Preferencias())
    override val preferencias: StateFlow<Preferencias> = _preferencias
    override fun actualizar(cambio: (Preferencias) -> Preferencias) = _preferencias.update(cambio)
}
