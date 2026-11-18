package cl.conatacoch.app.data.fake

import cl.conatacoch.app.domain.model.Aviso
import cl.conatacoch.app.domain.model.EstadoServicio
import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.Pago
import cl.conatacoch.app.domain.model.PasajeroEnEspera
import cl.conatacoch.app.domain.model.PerfilConductor
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

    override suspend fun perfil() = PerfilConductor("Pedro Soto Muñoz", "Línea 5003 · ABCD-12")

    override suspend fun turnos(): List<Turno> {
        val r1 = "06:00–14:00"
        val r2 = "14:00–22:00"
        return listOf(
            Turno(LocalDate.of(2026, 10, 15), "Línea 5003", "ABCD-12", r1),
            Turno(LocalDate.of(2026, 10, 16), "Línea 5003", "ABCD-12", r1),
            Turno(LocalDate.of(2026, 10, 17), null, null, null),
            Turno(LocalDate.of(2026, 10, 18), null, null, null),
            Turno(LocalDate.of(2026, 10, 19), "Línea 5011", "ABCD-12", r2),
            Turno(LocalDate.of(2026, 10, 20), "Línea 5011", "ABCD-12", r2),
        )
    }

    private val _servicio = MutableStateFlow(EstadoServicio())
    override val servicio: StateFlow<EstadoServicio> = _servicio

    override suspend fun iniciarServicio() {
        _servicio.value = EstadoServicio(
            enServicio = true,
            detenido = true,
            patente = "ABCD-12",
            recorrido = "Línea 5003",
            folio = "235003",
            aBordo = 1,
            reservados = 2,
            enEspera = listOf(
                PasajeroEnEspera("Camila R.", 1, enEsteParadero = true, minutosAlParadero = 0, segundosParaAbordar = 161, reservaId = 1),
                PasajeroEnEspera("José M.", 1, enEsteParadero = true, minutosAlParadero = 0, segundosParaAbordar = 180, reservaId = 2),
            ),
            ultimoPago = Pago("RP-482913", LocalDateTime.now().minusMinutes(1), "ABCD-12", 800),
        )
    }

    override suspend fun finalizarServicio() {
        _servicio.value = EstadoServicio()
    }

    override fun cambiarDetenido(detenido: Boolean) = _servicio.update { it.copy(detenido = detenido) }

    override suspend fun confirmarAbordaje(reservaId: Int) = _servicio.update { s ->
        val p = s.enEspera.find { it.reservaId == reservaId } ?: return@update s
        s.copy(
            enEspera = s.enEspera - p,
            reservados = (s.reservados - p.asientos).coerceAtLeast(0),
            aBordo = (s.aBordo + p.asientos).coerceAtMost(s.capacidad),
        )
    }

    override suspend fun cancelarReserva(reservaId: Int) = _servicio.update { s ->
        val p = s.enEspera.find { it.reservaId == reservaId } ?: return@update s
        s.copy(enEspera = s.enEspera - p, reservados = (s.reservados - p.asientos).coerceAtLeast(0))
    }

    override suspend fun enviarAviso(aviso: Aviso) = Unit

    override suspend fun cambiarOcupados(ocupados: Int) = _servicio.update {
        it.copy(aBordo = ocupados.coerceIn(0, it.capacidad - it.reservados))
    }

    override val notificaciones: Flow<List<Notificacion>> = flowOf(
        listOf(
            Notificacion(TipoNotificacion.NUEVA_RESERVA, "Nueva reserva", "Camila R. reservó 1 asiento en tu próximo paradero", "08:39"),
            Notificacion(TipoNotificacion.RESERVA_CANCELADA, "Reserva cancelada", "Martín G. canceló su reserva", "08:21"),
            Notificacion(TipoNotificacion.NUEVA_RESERVA, "Nueva reserva", "José M. reservó 1 asiento", "08:05"),
            Notificacion(TipoNotificacion.NUEVA_ASIGNACION, "Nueva asignación", "Línea 5011 · ABCD-12 · lunes 19-10-2026, 14:00–22:00", "Ayer"),
            Notificacion(TipoNotificacion.RESERVA_CANCELADA, "Reserva cancelada", "Valeria P. canceló su reserva", "Ayer"),
        )
    )
}
