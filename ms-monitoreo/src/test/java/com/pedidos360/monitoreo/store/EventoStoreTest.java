package com.pedidos360.monitoreo.store;

import com.pedidos360.monitoreo.model.EventoMonitor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventoStoreTest {

    private EventoMonitor evento(String tipo, long pedidoId) {
        return new EventoMonitor(tipo, pedidoId, "usuario-1", 1000.0,
                "CONFIRMADO", "APROBADO", "TARJETA_CREDITO",
                "detalle", "2026-10-05T10:00:00Z", "ms-carrito",
                null, null, null);
    }

    @Test
    void registrar_deberiaGuardarYDevolverElMasRecientePrimero() {
        EventoStore store = new EventoStore();

        store.registrar(evento("PEDIDO_REGISTRADO", 1L));
        store.registrar(evento("PAGO_APROBADO", 1L));

        List<EventoMonitor> eventos = store.listar(null);
        assertEquals(2, eventos.size());
        assertEquals("PAGO_APROBADO", eventos.get(0).tipo());
        assertEquals("PEDIDO_REGISTRADO", eventos.get(1).tipo());
    }

    @Test
    void listar_deberiaFiltrarPorTipo() {
        EventoStore store = new EventoStore();
        store.registrar(evento("PEDIDO_REGISTRADO", 1L));
        store.registrar(evento("PAGO_APROBADO", 1L));
        store.registrar(evento("PAGO_APROBADO", 2L));

        List<EventoMonitor> aprobados = store.listar("PAGO_APROBADO");

        assertEquals(2, aprobados.size());
        assertTrue(aprobados.stream().allMatch(e -> "PAGO_APROBADO".equals(e.tipo())));
        assertTrue(store.listar("CAMBIO_ESTADO").isEmpty());
    }

    @Test
    void registrar_deberiaRetenerSoloLaUltimaVentana_peroLosTotalesSiguenSumando() {
        EventoStore store = new EventoStore();

        for (int i = 1; i <= EventoStore.LIMITE_EN_MEMORIA + 5; i++) {
            store.registrar(evento("PEDIDO_REGISTRADO", (long) i));
        }

        EventoStore.Estadisticas stats = store.estadisticas();
        assertEquals(EventoStore.LIMITE_EN_MEMORIA + 5, stats.totalRecibidos());
        assertEquals(EventoStore.LIMITE_EN_MEMORIA, stats.retenidosEnMemoria());
        // Se conservan los mas recientes: el primero de la lista es el ultimo evento.
        assertEquals(EventoStore.LIMITE_EN_MEMORIA + 5, store.listar(null).get(0).pedidoId());
    }

    @Test
    void estadisticas_deberiaContarPorTipoYElUltimoEvento() {
        EventoStore store = new EventoStore();
        store.registrar(evento("PEDIDO_REGISTRADO", 1L));
        store.registrar(evento("PAGO_RECHAZADO", 2L));
        store.registrar(evento("PAGO_APROBADO", 3L));

        EventoStore.Estadisticas stats = store.estadisticas();

        assertEquals(3, stats.totalRecibidos());
        assertEquals(1L, stats.porTipo().get("PEDIDO_REGISTRADO"));
        assertEquals(1L, stats.porTipo().get("PAGO_RECHAZADO"));
        assertEquals(1L, stats.porTipo().get("PAGO_APROBADO"));
        assertEquals("PAGO_APROBADO", stats.ultimoEvento().tipo());
        assertEquals(3L, stats.ultimoEvento().pedidoId());
    }
}
