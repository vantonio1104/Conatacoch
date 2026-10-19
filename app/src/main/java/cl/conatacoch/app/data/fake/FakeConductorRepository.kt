package cl.conatacoch.app.data.fake

import cl.conatacoch.app.domain.model.Aviso
import cl.conatacoch.app.domain.model.EstadoServicio
import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.Pago
import cl.conatacoch.app.domain.model.PasajeroEnEspera
import cl.conatacoch.app.domain.model.TipoNotificacion
import cl.conatacoch.app.domain.model.Turno
import cl.conatacoch.app.domain.repository.ConductorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime

class FakeConductorRepository : ConductorRepository {

    override val nombre = "Pedro Soto Muñoz"

    override suspend fun turnos(): List<Turno> {
        val r1 = "06:00–14:00"
        val r2 = "14:00–22:00"
        return listOf(
            Turno(LocalDate.of(2026, 10, 15), "Recorrido 1", "ABCD-12", r1),
            Turno(LocalDate.of(2026, 10, 16), "Recorrido 1", "ABCD-12", r1),
            Turno(LocalDate.of(2026, 10, 17), null, null, null),
            Turno(LocalDate.of(2026, 10, 18), null, null, null),
            Turno(LocalDate.of(2026, 10, 19), "Recorrido 2", "ABCD-12", r2),
            Turno(LocalDate.of(2026, 10, 20), "Recorrido 2", "ABCD-12", r2),
        )
    }

    private val _servicio = MutableStateFlow(EstadoServicio())
    override val servicio: StateFlow<EstadoServicio> = _servicio

    override suspend fun iniciarServicio() {
        _servicio.value = EstadoServicio(
            enServicio = true,
            detenido = true,
            patente = "ABCD-12",
            recorrido = "Recorrido 1",
            aBordo = 1,
            reservados = 2,
            enEspera = listOf(
                PasajeroEnEspera("Camila R.", 1, enEsteParadero = true, minutosAlParadero = 0, segundosParaAbordar = 161),
                PasajeroEnEspera("José M.", 1, enEsteParadero = false, minutosAlParadero = 6, segundosParaAbordar = 180),
            ),
            ultimoPago = Pago("RP-482913", LocalDateTime.now().minusMinutes(1), "ABCD-12", 800),
        )
    }

    override suspend fun finalizarServicio() {
        _servicio.value = EstadoServicio()
    }

    override fun cambiarDetenido(detenido: Boolean) = _servicio.update { it.copy(detenido = detenido) }

    override suspend fun confirmarAbordaje(nombre: String) = _servicio.update { s ->
        val p = s.enEspera.find { it.nombre == nombre } ?: return@update s
        s.copy(
            enEspera = s.enEspera - p,
            reservados = (s.reservados - p.asientos).coerceAtLeast(0),
            aBordo = (s.aBordo + p.asientos).coerceAtMost(s.capacidad),
        )
    }

    override suspend fun enviarAviso(aviso: Aviso) {
        if (aviso == Aviso.AC04) _servicio.update { it.copy(correccionHabilitada = true) }
    }

    override suspend fun corregirOcupacion(aBordo: Int) = _servicio.update {
        it.copy(aBordo = aBordo.coerceIn(0, it.capacidad), correccionHabilitada = false)
    }

    override val notificaciones: Flow<List<Notificacion>> = flowOf(
        listOf(
            Notificacion(TipoNotificacion.NUEVA_RESERVA, "Nueva reserva", "Camila R. reservó 1 asiento en tu próximo paradero", "08:39"),
            Notificacion(TipoNotificacion.RESERVA_CANCELADA, "Reserva cancelada", "Martín G. canceló su reserva", "08:21"),
            Notificacion(TipoNotificacion.NUEVA_RESERVA, "Nueva reserva", "José M. reservó 1 asiento", "08:05"),
            Notificacion(TipoNotificacion.NUEVA_ASIGNACION, "Nueva asignación", "Recorrido 2 · ABCD-12 · lunes 19-10-2026, 14:00–22:00", "Ayer"),
            Notificacion(TipoNotificacion.RESERVA_CANCELADA, "Reserva cancelada", "Valeria P. canceló su reserva", "Ayer"),
        )
    )
}
