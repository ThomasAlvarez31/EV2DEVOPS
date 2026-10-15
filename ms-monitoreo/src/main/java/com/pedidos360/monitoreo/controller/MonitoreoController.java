package com.pedidos360.monitoreo.controller;

import com.pedidos360.monitoreo.model.EventoMonitor;
import com.pedidos360.monitoreo.store.EventoStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de monitoreo: consulta de los eventos de negocio vistos por Kafka.
 *
 *  GET /monitoreo/eventos?tipo=PAGO_APROBADO  -> ultimos eventos (mas reciente primero)
 *  GET /monitoreo/estadisticas                -> totales y contadores por tipo
 */
@RestController
@RequestMapping("/monitoreo")
public class MonitoreoController {

    private final EventoStore store;

    public MonitoreoController(EventoStore store) {
        this.store = store;
    }

    @GetMapping("/eventos")
    public List<EventoMonitor> eventos(@RequestParam(name = "tipo", required = false) String tipo) {
        return store.listar(tipo);
    }

    @GetMapping("/estadisticas")
    public EventoStore.Estadisticas estadisticas() {
        return store.estadisticas();
    }
}
