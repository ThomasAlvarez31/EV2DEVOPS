package com.pedidos360.ordenes.service;

import com.pedidos360.ordenes.messaging.EventoOrden;
import com.pedidos360.ordenes.model.EstadoOrden;
import com.pedidos360.ordenes.model.Orden;
import com.pedidos360.ordenes.repository.OrdenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Registra y actualiza las ordenes de compra a partir de los eventos de
 * RabbitMQ, y expone las operaciones de lectura/consulta de la API.
 *
 * FLUJO (todo por eventos, sin llamar a otros servicios):
 *
 *   PEDIDO_REGISTRADO -> crea la orden (REGISTRADA) con sus items
 *   PAGO_APROBADO     -> la orden pasa a PAGADA
 *   PAGO_RECHAZADO    -> la orden pasa a CANCELADA
 *   CAMBIO_ESTADO     -> guarda el ultimo estado logistico del pedido
 *
 * IDEMPOTENCIA: la clave de negocio es pedidoId (unica en la tabla). Un
 * evento repetido se reconoce y se ignora: nunca genera dos ordenes ni
 * repite una transicion. Si llega un evento de un pedido que todavia no
 * registro la orden (por orden de llegada), se avisa y se descarta, porque
 * el evento PEDIDO_REGISTRADO siempre abre el ciclo.
 *
 * Esta clase NO decide como se confirman los mensajes: eso lo hace
 * OrdenListener (ACK/NACK explicito sobre el delivery tag).
 */
@Service
public class OrdenService {

    private static final Logger log = LoggerFactory.getLogger(OrdenService.class);

    /** Que le ocurrio al evento al pasar por el dominio de ordenes. */
    public enum Resultado {
        /** Se creo la orden. */
        CREADA,
        /** Se cambio el estado de una orden existente. */
        ACTUALIZADA,
        /** El evento ya estaba reflejado en la orden: no hay nada que hacer. */
        REPETIDA,
        /** Evento ajeno o sin orden correspondiente: se descarta con aviso. */
        IGNORADA
    }

    private final OrdenRepository ordenRepository;

    public OrdenService(OrdenRepository ordenRepository) {
        this.ordenRepository = ordenRepository;
    }

    // ------------------------------------------------------------- eventos

    /**
     * Aplica un evento de negocio al dominio de ordenes.
     *
     * Toda la aplicacion es TRANSACCIONAL: si algo falla a mitad de camino no
     * queda una orden a medias y el evento puede reprocesarse.
     *
     * @param evento evento ya parseado desde RabbitMQ
     * @return que le ocurrio al evento
     * @throws IllegalArgumentException si el evento viene incompleto
     */
    @Transactional
    public Resultado aplicar(EventoOrden evento) {
        validar(evento);

        switch (evento.tipo()) {
            case EventoOrden.TIPO_PEDIDO_REGISTRADO -> {
                return registrar(evento);
            }
            case EventoOrden.TIPO_PAGO_APROBADO -> {
                return marcarPagada(evento);
            }
            case EventoOrden.TIPO_PAGO_RECHAZADO -> {
                return marcarCancelada(evento);
            }
            case EventoOrden.TIPO_CAMBIO_ESTADO -> {
                return registrarEstadoPedido(evento);
            }
            default -> {
                log.warn("Evento {} del pedido {} no corresponde al dominio de ordenes: se descarta",
                        evento.tipo(), evento.pedidoId());
                return Resultado.IGNORADA;
            }
        }
    }

    /** Crea la orden con sus lineas. */
    private Resultado registrar(EventoOrden evento) {
        if (ordenRepository.existsByPedidoId(evento.pedidoId())) {
            log.info("Evento {} del pedido {} ya registrado como orden -> se omite (idempotencia)",
                    evento.tipo(), evento.pedidoId());
            return Resultado.REPETIDA;
        }

        Orden orden = new Orden(
                evento.pedidoId(),
                evento.usuarioId(),
                evento.total(),
                evento.metodoPago(),
                parsearFecha(evento.fecha()));
        orden.setEstadoPedido(evento.estado());

        for (EventoOrden.ItemOrdenEvent item : evento.items()) {
            orden.agregarItem(item.productoId(), item.nombre(), item.cantidad(), item.precioUnitario());
        }

        ordenRepository.save(orden);
        log.info("Orden creada para el pedido {} del usuario {} (total {}, {} lineas)",
                orden.getPedidoId(), orden.getUsuarioId(), orden.getTotal(), orden.getItems().size());
        return Resultado.CREADA;
    }

    /** Pago aprobado -> orden PAGADA. */
    private Resultado marcarPagada(EventoOrden evento) {
        Orden orden = ordenDe(evento);
        if (orden == null) {
            return Resultado.IGNORADA;
        }
        if (orden.getEstado() == EstadoOrden.PAGADA) {
            return Resultado.REPETIDA;
        }
        if (orden.getEstado() != EstadoOrden.REGISTRADA) {
            log.warn("Pago aprobado del pedido {} con la orden en estado {}: se mantiene {}",
                    evento.pedidoId(), orden.getEstado(), orden.getEstado());
            return Resultado.REPETIDA;
        }

        orden.setEstado(EstadoOrden.PAGADA);
        if (evento.estado() != null && !evento.estado().isBlank()) {
            orden.setEstadoPedido(evento.estado());
        }
        ordenRepository.save(orden);
        log.info("Orden {} del pedido {} pasó a PAGADA", orden.getId(), orden.getPedidoId());
        return Resultado.ACTUALIZADA;
    }

    /** Pago rechazado -> orden CANCELADA. */
    private Resultado marcarCancelada(EventoOrden evento) {
        Orden orden = ordenDe(evento);
        if (orden == null) {
            return Resultado.IGNORADA;
        }
        if (orden.getEstado() == EstadoOrden.CANCELADA || orden.getEstado() == EstadoOrden.ANULADA) {
            return Resultado.REPETIDA;
        }

        orden.setEstado(EstadoOrden.CANCELADA);
        if (evento.estado() != null && !evento.estado().isBlank()) {
            orden.setEstadoPedido(evento.estado());
        }
        ordenRepository.save(orden);
        log.info("Orden {} del pedido {} pasó a CANCELADA", orden.getId(), orden.getPedidoId());
        return Resultado.ACTUALIZADA;
    }

    /** Cambio de estado del pedido -> guarda el estado logistico visto. */
    private Resultado registrarEstadoPedido(EventoOrden evento) {
        Orden orden = ordenDe(evento);
        if (orden == null) {
            return Resultado.IGNORADA;
        }
        String estadoPedido = evento.estado();
        if (estadoPedido == null || estadoPedido.isBlank()
                || estadoPedido.equals(orden.getEstadoPedido())) {
            return Resultado.REPETIDA;
        }

        orden.setEstadoPedido(estadoPedido);
        ordenRepository.save(orden);
        log.info("Orden {} del pedido {} registró el estado logístico {}",
                orden.getId(), orden.getPedidoId(), estadoPedido);
        return Resultado.ACTUALIZADA;
    }

    /** Orden del evento, con aviso si el pedido todavia no tiene orden. */
    private Orden ordenDe(EventoOrden evento) {
        return ordenRepository.findByPedidoId(evento.pedidoId())
                .orElseGet(() -> {
                    log.warn("Evento {} del pedido {} sin orden previa: se descarta "
                                    + "(el evento PEDIDO_REGISTRADO abre el ciclo)",
                            evento.tipo(), evento.pedidoId());
                    return null;
                });
    }

    // ------------------------------------------------------------- API

    /** Ordenes del usuario autenticado, de mas reciente a mas antigua. */
    public java.util.List<Orden> listar(String usuarioId) {
        return ordenRepository.findByUsuarioIdOrderByRegistradaEnDesc(usuarioId);
    }

    /** Orden por su id (solo para su dueno). */
    public Orden obtener(String usuarioId, Long id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada: " + id));
        if (!orden.getUsuarioId().equals(usuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No tienes permiso para ver esta orden");
        }
        return orden;
    }

    /** Orden de un pedido (solo para su dueno). */
    public Orden obtenerPorPedido(String usuarioId, Long pedidoId) {
        Orden orden = ordenRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada para el pedido: " + pedidoId));
        if (!orden.getUsuarioId().equals(usuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No tienes permiso para ver esta orden");
        }
        return orden;
    }

    /**
     * Anulacion manual de una orden. Solo se admiten las transiciones
     * permitidas por {@link EstadoOrden#transicionesDesde}.
     */
    public Orden cambiarEstado(String usuarioId, Long id, EstadoOrden nuevoEstado) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("Debe indicar el estado de la orden");
        }
        Orden orden = obtener(usuarioId, id);
        Set<EstadoOrden> permitidos = EstadoOrden.transicionesDesde(orden.getEstado());

        if (!permitidos.contains(nuevoEstado)) {
            throw new IllegalStateException("Transicion de estado no permitida: "
                    + orden.getEstado() + " -> " + nuevoEstado);
        }

        orden.setEstado(nuevoEstado);
        return ordenRepository.save(orden);
    }

    // ------------------------------------------------------------- util

    /** La fecha viaja como texto ISO en el evento. */
    private static LocalDateTime parsearFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(fecha);
        } catch (DateTimeParseException e) {
            log.warn("Fecha de evento no interpretable ({}): se registra sin fecha", fecha);
            return null;
        }
    }

    /** Un evento incompleto no debe crear una orden a medias. */
    private static void validar(EventoOrden evento) {
        if (evento == null) {
            throw new IllegalArgumentException("Evento vacio");
        }
        if (evento.tipo() == null || evento.tipo().isBlank()) {
            throw new IllegalArgumentException("El evento no incluye tipo");
        }
        if (evento.pedidoId() == null) {
            throw new IllegalArgumentException("El evento no incluye pedidoId");
        }
        if (EventoOrden.TIPO_PEDIDO_REGISTRADO.equals(evento.tipo())) {
            if (evento.usuarioId() == null || evento.usuarioId().isBlank()) {
                throw new IllegalArgumentException("El evento no incluye usuarioId");
            }
            if (evento.total() == null) {
                throw new IllegalArgumentException("El evento no incluye total");
            }
            if (evento.items() == null || evento.items().isEmpty()) {
                throw new IllegalArgumentException(
                        "El evento del pedido " + evento.pedidoId() + " no incluye items");
            }
        }
    }
}
