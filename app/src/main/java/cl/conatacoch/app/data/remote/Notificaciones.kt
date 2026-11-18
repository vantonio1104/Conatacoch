package cl.conatacoch.app.data.remote

import cl.conatacoch.app.domain.model.Notificacion
import cl.conatacoch.app.domain.model.TipoNotificacion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Hora = DateTimeFormatter.ofPattern("HH:mm")
private val Dia = DateTimeFormatter.ofPattern("dd-MM")

/** "08:06" hoy, "Ayer" o "14-11". */
internal fun cuando(fecha: LocalDateTime): String {
    val dia = fecha.toLocalDate()
    val hoy = LocalDate.now()
    return when (dia) {
        hoy -> fecha.format(Hora)
        hoy.minusDays(1) -> "Ayer"
        else -> fecha.format(Dia)
    }
}

/**
 * Notificaciones de quien inició sesión (guardadas en MongoDB). Se revisan cada 15 segundos;
 * sin sesión la lista queda vacía.
 */
internal fun ApiCliente.flujoNotificaciones(): Flow<List<Notificacion>> = flow {
    var ultima: List<Notificacion>? = null
    while (true) {
        val lista = if (token.value == null) {
            emptyList()
        } else {
            runCatching {
                getArray("/api/notificaciones").mapear { o ->
                    Notificacion(
                        tipo = runCatching { TipoNotificacion.valueOf(o.getString("tipo")) }.getOrDefault(TipoNotificacion.RETRASO),
                        titulo = o.getString("titulo"),
                        detalle = o.getString("detalle"),
                        cuando = cuando(fechaLocal(o.getString("fecha"))),
                    )
                }
            }.getOrNull() ?: ultima ?: emptyList()
        }
        if (lista != ultima) emit(lista)
        ultima = lista
        delay(15_000)
    }
}
