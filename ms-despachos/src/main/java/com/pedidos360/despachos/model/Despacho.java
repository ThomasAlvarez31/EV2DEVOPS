package com.pedidos360.despachos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Despacho (envio) de un pedido: la guia que se genera cuando el pago fue
 * aprobado.
 *
 * Se crea de forma ASINCRONA cuando RabbitMQ entrega el evento
 * PAGO_APROBADO: este microservicio no llama a ningun otro servicio ni crea
 * nada por su cuenta.
 *
 * La clave de negocio es pedidoId (unica): un evento repetido no puede
 * generar dos despachos para el mismo pedido.
 */
@Entity
@Table(name = "despachos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_despacho_pedido",
                columnNames = "pedido_id"))
public class Despacho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Pedido de ms-carrito al que corresponde este despacho (clave unica). */
    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    /** Identificador del usuario (oid de Microsoft o sub de Cognito). */
    @Column(name = "usuario_id", nullable = false, length = 100)
    private String usuarioId;

    /** Guia de despacho: se genera con el numero de pedido. */
    @Column(nullable = false, length = 30, unique = true)
    private String guia;

    /** Ciclo de vida del propio despacho. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDespacho estado = EstadoDespacho.PENDIENTE;

    /** Ultimo estado logistico del pedido visto en los eventos. */
    @Column(name = "estado_pedido", length = 30)
    private String estadoPedido;

    /** Cuando se pago el pedido (origen del envio). */
    @Column(name = "fecha_pedido")
    private LocalDateTime fechaPedido;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    public Despacho() {
    }

    public Despacho(Long pedidoId, String usuarioId, LocalDateTime fechaPedido) {
        this.pedidoId = pedidoId;
        this.usuarioId = usuarioId;
        this.fechaPedido = fechaPedido;
        // Guia deterministica: P360-<pedido>. Idempotente por construccion.
        this.guia = "P360-" + String.format("%05d", pedidoId == null ? 0L : pedidoId);
    }

    /** Marca el momento del ultimo cambio de estado. */
    public void marcarActualizacion() {
        this.actualizadoEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(Long pedidoId) {
        this.pedidoId = pedidoId;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getGuia() {
        return guia;
    }

    public void setGuia(String guia) {
        this.guia = guia;
    }

    public EstadoDespacho getEstado() {
        return estado;
    }

    public void setEstado(EstadoDespacho estado) {
        this.estado = estado;
    }

    public String getEstadoPedido() {
        return estadoPedido;
    }

    public void setEstadoPedido(String estadoPedido) {
        this.estadoPedido = estadoPedido;
    }

    public LocalDateTime getFechaPedido() {
        return fechaPedido;
    }

    public void setFechaPedido(LocalDateTime fechaPedido) {
        this.fechaPedido = fechaPedido;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(LocalDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
