package com.pedidos360.pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Registro de un pago simulado.
 *
 * Cada pedido recibe a lo sumo UN pago (pedidoId con restricción única), lo que
 * además evita pagos duplicados por doble clic o reenvío del formulario.
 */
@Entity
@Table(name = "pagos", uniqueConstraints = @UniqueConstraint(columnNames = "pedidoId"))
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pedidoId;

    @Column(nullable = false)
    private String usuarioId;

    /** Monto total del pedido (tomado de ms-carrito, no del navegador). */
    @Column(nullable = false)
    private Double monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPago metodoPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    /** Motivo del rechazo cuando estado = RECHAZADO (vacío si fue aprobado). */
    private String motivo;

    @Column(nullable = false)
    private Instant fecha;

    public Pago() {
    }

    public Pago(Long pedidoId, String usuarioId, Double monto,
                MetodoPago metodoPago, EstadoPago estado, String motivo) {
        this.pedidoId = pedidoId;
        this.usuarioId = usuarioId;
        this.monto = monto;
        this.metodoPago = metodoPago;
        this.estado = estado;
        this.motivo = motivo;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Double getMonto() {
        return monto;
    }

    public MetodoPago getMetodoPago() {
        return metodoPago;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getFecha() {
        return fecha;
    }
}
