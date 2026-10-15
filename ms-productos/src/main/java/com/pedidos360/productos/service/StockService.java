package com.pedidos360.productos.service;

import com.pedidos360.productos.messaging.EventoStock;
import com.pedidos360.productos.model.EventoStockProcesado;
import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.repository.EventoStockProcesadoRepository;
import com.pedidos360.productos.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Aplica los eventos de negocio al INVENTARIO de forma ASINCRONA.
 *
 * Este es el unico lugar del sistema donde cambia el stock de un producto
 * a partir de un pedido:
 *
 *   PEDIDO_REGISTRADO -> descuenta la cantidad de cada item del pedido
 *   PAGO_RECHAZADO    -> repone la cantidad de cada item (pedido cancelado)
 *
 * El descuento es ASINCRONO: el checkout de ms-carrito solo VALIDA el stock
 * (llamada sincrona a GET /productos/{id}) y no lo modifica; el descuento
 * ocurre cuando RabbitMQ entrega el evento a esta cola. Asi el catalogo
 * refleja el inventario real sin acoplar los servicios.
 *
 * IDEMPOTENCIA: RabbitMQ entrega "al menos una vez". Antes de aplicar un
 * evento se comprueba en stock_eventos_procesados si ya fue procesado; la
 * clave (tipo, pedido_id) es unica. Un evento repetido se reconoce y se
 * ignora, por lo que NUNCA se descuenta dos veces el mismo pedido.
 *
 * Si el stock no alcanza (carrera entre usuarios) el inventario queda en 0:
 * el pedido ya esta registrado y un producto nunca puede quedar negativo.
 *
 * Esta clase NO decide como se confirman los mensajes: eso lo hace
 * StockListener (ACK/NACK explicito sobre el delivery tag).
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    /** Eventos que este dominio sabe aplicar al inventario. */
    private static final Set<String> TIPOS_CON_STOCK = Set.of(
            EventoStock.TIPO_PEDIDO_REGISTRADO,
            EventoStock.TIPO_PAGO_RECHAZADO
    );

    /** Que le ocurrio al evento al pasar por el inventario. */
    public enum Resultado {
        /** Se aplico al inventario (descuento o reposicion). */
        APLICADO,
        /** Ya habia sido aplicado: se ignora por idempotencia. */
        REPETIDO,
        /** Evento valido pero ajeno a este dominio: se descarta con aviso. */
        IGNORADO
    }

    private final ProductoRepository productoRepository;
    private final EventoStockProcesadoRepository eventoProcesadoRepository;

    public StockService(ProductoRepository productoRepository,
                        EventoStockProcesadoRepository eventoProcesadoRepository) {
        this.productoRepository = productoRepository;
        this.eventoProcesadoRepository = eventoProcesadoRepository;
    }

    /**
     * Aplica un evento de negocio al inventario.
     *
     * Toda la aplicacion es TRANSACCIONAL: si algo falla a mitad de camino no
     * se descuenta nada y el evento puede reprocesarse sin efectos parciales.
     *
     * @param evento evento ya parseado desde RabbitMQ
     * @return que le ocurrio al evento (aplicado, repetido o ignorado)
     * @throws IllegalArgumentException si el evento viene incompleto
     */
    @Transactional
    public Resultado aplicar(EventoStock evento) {
        validar(evento);

        if (!TIPOS_CON_STOCK.contains(evento.tipo())) {
            log.warn("Evento {} del pedido {} no corresponde al inventario: se descarta",
                    evento.tipo(), evento.pedidoId());
            return Resultado.IGNORADO;
        }

        // Idempotencia: evento ya aplicado -> no se descuenta dos veces.
        if (eventoProcesadoRepository.existsByTipoAndPedidoId(evento.tipo(), evento.pedidoId())) {
            log.info("Evento {} del pedido {} ya aplicado al inventario -> se omite (idempotencia)",
                    evento.tipo(), evento.pedidoId());
            return Resultado.REPETIDO;
        }

        boolean esReposicion = EventoStock.TIPO_PAGO_RECHAZADO.equals(evento.tipo());
        for (EventoStock.ItemStock item : evento.items()) {
            aplicarItem(evento, item, esReposicion);
        }

        eventoProcesadoRepository.save(new EventoStockProcesado(evento.tipo(), evento.pedidoId()));
        return Resultado.APLICADO;
    }

    /** Descuenta o repone una linea del pedido en el producto correspondiente. */
    private void aplicarItem(EventoStock evento, EventoStock.ItemStock item, boolean esReposicion) {
        if (item.productoId() == null || item.cantidad() == null || item.cantidad() <= 0) {
            log.warn("Linea invalida en el evento {} del pedido {}: productoId={}, cantidad={}",
                    evento.tipo(), evento.pedidoId(), item.productoId(), item.cantidad());
            return;
        }

        // El catalogo puede haber cambiado: un producto dado de baja no
        // detiene el resto del evento (solo se avisa).
        Producto producto = productoRepository.findById(item.productoId()).orElse(null);
        if (producto == null) {
            log.warn("El producto {} ya no existe en el catalogo: no se modifica su stock (evento {} del pedido {})",
                    item.productoId(), evento.tipo(), evento.pedidoId());
            return;
        }

        int actual = producto.getStock() == null ? 0 : producto.getStock();

        if (esReposicion) {
            producto.setStock(actual + item.cantidad());
            productoRepository.save(producto);
            log.info("Stock repuesto del producto {} ({} -> {}): evento {} del pedido {}",
                    producto.getId(), actual, actual + item.cantidad(), evento.tipo(), evento.pedidoId());
            return;
        }

        int nuevo = Math.max(0, actual - item.cantidad());
        if (nuevo < actual - item.cantidad()) {
            log.warn("Stock insuficiente del producto {} (disponible {}, pedido {}): queda en 0 "
                            + "(evento {} del pedido {})",
                    producto.getId(), actual, item.cantidad(), evento.tipo(), evento.pedidoId());
        }
        producto.setStock(nuevo);
        productoRepository.save(producto);
        log.info("Stock descontado del producto {} ({} -> {}): evento {} del pedido {}",
                producto.getId(), actual, nuevo, evento.tipo(), evento.pedidoId());
    }

    /** Un evento incompleto no debe tocar el inventario: se rechaza al broker. */
    private static void validar(EventoStock evento) {
        if (evento == null) {
            throw new IllegalArgumentException("Evento vacio");
        }
        if (evento.tipo() == null || evento.tipo().isBlank()) {
            throw new IllegalArgumentException("El evento no incluye tipo");
        }
        if (evento.pedidoId() == null) {
            throw new IllegalArgumentException("El evento no incluye pedidoId");
        }
        List<EventoStock.ItemStock> items = evento.items();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "El evento del pedido " + evento.pedidoId() + " no incluye items");
        }
    }
}
