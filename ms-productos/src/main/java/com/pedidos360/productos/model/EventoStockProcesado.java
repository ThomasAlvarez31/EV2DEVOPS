package com.pedidos360.productos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Registro de un evento de negocio YA aplicado al inventario.
 *
 * RabbitMQ entrega "al menos una vez": si la aplicacion se cae entre el
 * descuento del stock y el ACK, el broker vuelve a entregar el evento. Esta
 * tabla hace el procesamiento IDEMPOTENTE: la clave (tipo, pedido_id) es
 * unica, de modo que un evento repetido se reconoce y se ignora en lugar de
 * descontar dos veces el mismo pedido.
 */
@Entity
@Table(name = "stock_eventos_procesados",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_evento_tipo_pedido",
                columnNames = {"tipo", "pedido_id"}))
public class EventoStockProcesado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PEDIDO_REGISTRADO | PAGO_RECHAZADO. */
    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "procesado_en", nullable = false)
    private LocalDateTime procesadoEn;

    public EventoStockProcesado() {
    }

    public EventoStockProcesado(String tipo, Long pedidoId) {
        this.tipo = tipo;
        this.pedidoId = pedidoId;
        this.procesadoEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public LocalDateTime getProcesadoEn() {
        return procesadoEn;
    }
}
