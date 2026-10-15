package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.BindingInfo;
import com.pedidos360.rabbitadmin.dto.CrearBindingRequest;
import com.pedidos360.rabbitadmin.dto.CrearColaRequest;
import com.pedidos360.rabbitadmin.dto.CrearExchangeRequest;
import com.pedidos360.rabbitadmin.dto.ColaInfo;
import com.pedidos360.rabbitadmin.dto.ExchangeInfo;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API REST de administracion de RabbitMQ.
 *
 * Recursos y verbos:
 *   GET    /queues                     lista de colas
 *   GET    /queues/{name}              detalle de una cola
 *   POST   /queues                     crea una cola
 *   DELETE /queues/{name}              elimina una cola
 *   GET    /exchanges                  lista de exchanges
 *   POST   /exchanges                  crea un exchange
 *   DELETE /exchanges/{name}           elimina un exchange
 *   GET    /bindings                   lista de bindings
 *   POST   /bindings                   crea un binding
 *   DELETE /bindings                   elimina un binding (query params)
 *
 * El controlador solo resuelve HTTP: la validacion y la logica de
 * administracion viven en {@link RabbitAdminService}.
 */
@RestController
public class RabbitAdminController {

    private final RabbitAdminService servicio;

    public RabbitAdminController(RabbitAdminService servicio) {
        this.servicio = servicio;
    }

    // ------------------------------------------------------------------
    //  COLAS
    // ------------------------------------------------------------------

    @GetMapping("/queues")
    public List<ColaInfo> listarColas() {
        return servicio.listarColas();
    }

    @GetMapping("/queues/{name}")
    public ColaInfo obtenerCola(@PathVariable("name") String name) {
        return servicio.obtenerCola(name);
    }

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public ColaInfo crearCola(@Valid @RequestBody CrearColaRequest solicitud) {
        return servicio.crearCola(solicitud);
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Map<String, Object>> eliminarCola(
            @PathVariable("name") String name,
            @RequestParam(name = "ifUnused", defaultValue = "false") boolean ifUnused,
            @RequestParam(name = "ifEmpty", defaultValue = "false") boolean ifEmpty) {
        servicio.eliminarCola(name, ifUnused, ifEmpty);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    //  EXCHANGES
    // ------------------------------------------------------------------

    @GetMapping("/exchanges")
    public List<ExchangeInfo> listarExchanges() {
        return servicio.listarExchanges();
    }

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeInfo crearExchange(@Valid @RequestBody CrearExchangeRequest solicitud) {
        return servicio.crearExchange(solicitud);
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Map<String, Object>> eliminarExchange(@PathVariable("name") String name) {
        servicio.eliminarExchange(name);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    //  BINDINGS
    // ------------------------------------------------------------------

    @GetMapping("/bindings")
    public List<BindingInfo> listarBindings() {
        return servicio.listarBindings();
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public BindingInfo crearBinding(@Valid @RequestBody CrearBindingRequest solicitud) {
        return servicio.crearBinding(solicitud);
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Map<String, Object>> eliminarBinding(
            @RequestParam(name = "queue") String queue,
            @RequestParam(name = "exchange") String exchange,
            @RequestParam(name = "routingKey") String routingKey) {
        servicio.eliminarBinding(queue, exchange, routingKey);
        return ResponseEntity.noContent().build();
    }
}
