package com.pedidos360.despachos.service;

import com.pedidos360.despachos.messaging.EventoDespacho;
import com.pedidos360.despachos.model.Despacho;
import com.pedidos360.despachos.model.EstadoDespacho;
import com.pedidos360.despachos.repository.DespachoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Genera y actualiza los despachos (envios) a partir de los eventos de
 * RabbitMQ, y expone las operaciones de lectura/consulta de la API.
 *
 * FLUJO (todo por eventos, sin llamar a otros servicios):
 *
 *   PAGO_APROBADO     -> genera el despacho con guia de envio (PENDIENTE)
 *   CAMBIO_ESTADO     -> avanza el despacho (ENVIADO -> EN_RUTA,
 *                        ENTREGADO -> ENTREGADO) y guarda el estado pedido
 *
 * IDEMPOTENCIA: la clave de negocio es pedidoId (unica en la tabla) y la
 * guia se deriva del numero de pedido, asi que un evento repetido se
 * reconoce y se ignora: nunca genera dos envios para el mismo pedido.
 *
 * Esta clase NO decide como se confirman los mensajes: eso lo hace
 * DespachoListener (ACK/NACK explicito sobre el delivery tag).
 */
@Service
public class DespachoService {

    private static final Logger log = LoggerFactory.getLogger(DespachoService.class);

    /** Que le ocurrio al evento al pasar por el dominio de despachos. */
    public enum Resultado {
        /** Se creo el despacho. */
        CREADO,
        /** Se cambio el estado de un despacho existente. */
        ACTUALIZADO,
        /** El evento ya estaba reflejado en el despacho: no hay nada que hacer. */
        REPETIDO,
        /** Evento ajeno o sin despacho correspondiente: se descarta con aviso. */
        IGNORADO
    }

    private final DespachoRepository despachoRepository;

    public DespachoService(DespachoRepository despachoRepository) {
        this.despachoRepository = despachoRepository;
    }

    // ------------------------------------------------------------- eventos

    /**
     * Aplica un evento de negocio al dominio de despachos.
     *
     * Toda la aplicacion es TRANSACCIONAL: si algo falla a mitad de camino no
     * queda un despacho a medias y el evento puede reprocesarse.
     *
     * @param evento evento ya parseado desde RabbitMQ
     * @return que le ocurrio al evento
     * @throws IllegalArgumentException si el evento viene incompleto
     */
    @Transactional
    public Resultado aplicar(EventoDespacho evento) {
        validar(evento);

        switch (evento.tipo()) {
            case EventoDespacho.TIPO_PAGO_APROBADO -> {
                return crear(evento);
            }
            case EventoDespacho.TIPO_CAMBIO_ESTADO -> {
                return avanzar(evento);
            }
            default -> {
                log.warn("Evento {} del pedido {} no corresponde al dominio de despachos: se descarta",
                        evento.tipo(), evento.pedidoId());
                return Resultado.IGNORADO;
            }
        }
    }

    /** Pago aprobado -> genera la guia de despacho. */
    private Resultado crear(EventoDespacho evento) {
        if (despachoRepository.existsByPedidoId(evento.pedidoId())) {
            log.info("Evento {} del pedido {} ya tiene despacho -> se omite (idempotencia)",
                    evento.tipo(), evento.pedidoId());
            return Resultado.REPETIDO;
        }

        Despacho despacho = new Despacho(evento.pedidoId(), evento.usuarioId(), parsearFecha(evento.fecha()));
        despacho.setEstadoPedido(evento.estado());
        despachoRepository.save(despacho);

        log.info("Despacho {} generado para el pedido {} del usuario {}",
                despacho.getGuia(), despacho.getPedidoId(), despacho.getUsuarioId());
        return Resultado.CREADO;
    }

    /** Cambio de estado del pedido -> avanza el envio. */
    private Resultado avanzar(EventoDespacho evento) {
        Despacho despacho = despachoRepository.findByPedidoId(evento.pedidoId())
                .orElse(null);
        if (despacho == null) {
            log.warn("Evento {} del pedido {} sin despacho previo: se descarta "
                    + "(el evento PAGO_APROBADO genera el envio)", evento.tipo(), evento.pedidoId());
            return Resultado.IGNORADO;
        }

        boolean terminal = despacho.getEstado() == EstadoDespacho.ENTREGADO
                || despacho.getEstado() == EstadoDespacho.CANCELADO;
        EstadoDespacho nuevoEstado = EstadoDespacho.desdeEstadoPedido(evento.estado());
        String estadoPedido = evento.estado();

        boolean mismoEstadoPedido = estadoPedido != null && estadoPedido.equals(despacho.getEstadoPedido());
        boolean mismoEstado = nuevoEstado == null || nuevoEstado == despacho.getEstado();

        if (terminal && !mismoEstado) {
            log.warn("Despacho {} del pedido {} ya esta en estado {} (pedido {}): se mantiene",
                    despacho.getGuia(), despacho.getPedidoId(), despacho.getEstado(), evento.estado());
            return Resultado.REPETIDO;
        }
        if (terminal || (mismoEstado && mismoEstadoPedido)) {
            return Resultado.REPETIDO;
        }

        if (nuevoEstado != null) {
            despacho.setEstado(nuevoEstado);
        }
        if (estadoPedido != null && !estadoPedido.isBlank()) {
            despacho.setEstadoPedido(estadoPedido);
        }
        despacho.marcarActualizacion();
        despachoRepository.save(despacho);

        log.info("Despacho {} del pedido {} ahora en {} (pedido {})",
                despacho.getGuia(), despacho.getPedidoId(), despacho.getEstado(), despacho.getEstadoPedido());
        return Resultado.ACTUALIZADO;
    }

    // ------------------------------------------------------------- API

    /** Despachos del usuario autenticado, de mas reciente a mas antiguo. */
    public List<Despacho> listar(String usuarioId) {
        return despachoRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId);
    }

    /** Despacho por su id (solo para su dueno). */
    public Despacho obtener(String usuarioId, Long id) {
        Despacho despacho = despachoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Despacho no encontrado: " + id));
        if (!despacho.getUsuarioId().equals(usuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No tienes permiso para ver este despacho");
        }
        return despacho;
    }

    /** Despacho de un pedido (solo para su dueno). */
    public Despacho obtenerPorPedido(String usuarioId, Long pedidoId) {
        Despacho despacho = despachoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Despacho no encontrado para el pedido: " + pedidoId));
        if (!despacho.getUsuarioId().equals(usuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No tienes permiso para ver este despacho");
        }
        return despacho;
    }

    /**
     * Actualizacion manual del despacho (area de logistica). Solo se admiten
     * las transiciones permitidas por {@link EstadoDespacho#transicionesDesde}.
     */
    public Despacho cambiarEstado(String usuarioId, Long id, EstadoDespacho nuevoEstado) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("Debe indicar el estado del despacho");
        }
        Despacho despacho = obtener(usuarioId, id);
        Set<EstadoDespacho> permitidos = EstadoDespacho.transicionesDesde(despacho.getEstado());

        if (!permitidos.contains(nuevoEstado)) {
            throw new IllegalStateException("Transicion de estado no permitida: "
                    + despacho.getEstado() + " -> " + nuevoEstado);
        }

        despacho.setEstado(nuevoEstado);
        despacho.marcarActualizacion();
        return despachoRepository.save(despacho);
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

    /** Un evento incompleto no debe generar un despacho a medias. */
    private static void validar(EventoDespacho evento) {
        if (evento == null) {
            throw new IllegalArgumentException("Evento vacio");
        }
        if (evento.tipo() == null || evento.tipo().isBlank()) {
            throw new IllegalArgumentException("El evento no incluye tipo");
        }
        if (evento.pedidoId() == null) {
            throw new IllegalArgumentException("El evento no incluye pedidoId");
        }
        if (EventoDespacho.TIPO_PAGO_APROBADO.equals(evento.tipo())
                && (evento.usuarioId() == null || evento.usuarioId().isBlank())) {
            throw new IllegalArgumentException("El evento no incluye usuarioId");
        }
    }
}
