package com.pedidos360.monitoreo.store;

import com.pedidos360.monitoreo.model.EventoMonitor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana en memoria de los ultimos eventos vistos por el monitoreo.
 *
 * Kafka conserva el log completo (no se consume como una cola); esto solo
 * retiene los ultimos {@value #LIMITE_EN_MEMORIA} para poder mostrarlos en
 * {@code GET /monitoreo/eventos} sin guardar nada en base de datos. Los
 * contadores ({@code totalRecibidos} y por tipo) siguen sumando aunque el
 * evento salga de la ventana.
 */
@Component
public class EventoStore {

    /** Cuantos eventos se mantienen disponibles para la consulta. */
    public static final int LIMITE_EN_MEMORIA = 500;

    private final Deque<EventoMonitor> eventos = new ArrayDeque<>();
    private final Map<String, Long> porTipo = new ConcurrentHashMap<>();

    private long totalRecibidos;
    private EventoMonitor ultimoEvento;

    /** Registra un evento: el mas reciente queda al frente de la ventana. */
    public synchronized void registrar(EventoMonitor evento) {
        totalRecibidos++;
        ultimoEvento = evento;
        porTipo.merge(evento.tipo() == null ? "DESCONOCIDO" : evento.tipo(), 1L, Long::sum);

        eventos.addFirst(evento);
        while (eventos.size() > LIMITE_EN_MEMORIA) {
            eventos.removeLast();
        }
    }

    /** Devuelve los eventos retenidos (mas recientes primero), opcionalmente de un tipo. */
    public synchronized List<EventoMonitor> listar(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return List.copyOf(eventos);
        }
        String filtro = tipo.trim().toUpperCase(Locale.ROOT);
        return eventos.stream()
                .filter(e -> filtro.equals(e.tipo()))
                .toList();
    }

    /** Totales del monitoreo: cuantos eventos se vieron y de que tipo. */
    public synchronized Estadisticas estadisticas() {
        return new Estadisticas(totalRecibidos, eventos.size(), Map.copyOf(porTipo), ultimoEvento);
    }

    /**
     * @param totalRecibidos    eventos procesados desde que arranco el servicio
     * @param retenidosEnMemoria eventos aun disponibles en la ventana
     * @param porTipo           contador acumulado por tipo de evento
     * @param ultimoEvento      evento mas reciente visto
     */
    public record Estadisticas(
            long totalRecibidos,
            int retenidosEnMemoria,
            Map<String, Long> porTipo,
            EventoMonitor ultimoEvento) {
    }
}
