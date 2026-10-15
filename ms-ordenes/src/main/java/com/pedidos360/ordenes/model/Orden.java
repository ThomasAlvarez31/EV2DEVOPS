package com.pedidos360.ordenes.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden de compra: el comprobante formal de un pedido.
 *
 * A diferencia del pedido (que vive en ms-carrito), la orden se registra de
 * forma ASINCRONA cuando RabbitMQ entrega el evento PEDIDO_REGISTRADO: este
 * microservicio no llama a ningun otro servicio ni crea nada por su cuenta.
 *
 * La clave de negocio es pedidoId (unica): un evento repetido no puede
 * generar dos ordenes para el mismo pedido.
 */
@Entity
@Table(name = "ordenes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orden_pedido",
                columnNames = "pedido_id"))
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Pedido de ms-carrito al que corresponde esta orden (clave unica). */
    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    /** Identificador del usuario (oid de Microsoft o sub de Cognito). */
    @Column(name = "usuario_id", nullable = false, length = 100)
    private String usuarioId;

    /** Ciclo de vida de la propia orden. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOrden estado = EstadoOrden.REGISTRADA;

    /** Ultimo estado logistico del pedido visto en los eventos. */
    @Column(name = "estado_pedido", length = 30)
    private String estadoPedido;

    @Column(nullable = false)
    private Double total;

    /** Nombre legible del metodo de pago (nunca datos de tarjeta). */
    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    /** Fecha en que ocurrio el evento (fecha del pedido). */
    @Column(name = "fecha_pedido")
    private LocalDateTime fechaPedido;

    /** Fecha en que esta orden fue registrada en el sistema. */
    @Column(name = "registrada_en", nullable = false)
    private LocalDateTime registradaEn = LocalDateTime.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "orden_id", nullable = false)
    private List<ItemOrden> items = new ArrayList<>();

    public Orden() {
    }

    public Orden(Long pedidoId, String usuarioId, Double total, String metodoPago, LocalDateTime fechaPedido) {
        this.pedidoId = pedidoId;
        this.usuarioId = usuarioId;
        this.total = total;
        this.metodoPago = metodoPago;
        this.fechaPedido = fechaPedido;
    }

    /** Agrega una linea a la orden. */
    public void agregarItem(Long productoId, String nombre, Integer cantidad, Double precioUnitario) {
        items.add(new ItemOrden(productoId, nombre, cantidad, precioUnitario));
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

    public EstadoOrden getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrden estado) {
        this.estado = estado;
    }

    public String getEstadoPedido() {
        return estadoPedido;
    }

    public void setEstadoPedido(String estadoPedido) {
        this.estadoPedido = estadoPedido;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public LocalDateTime getFechaPedido() {
        return fechaPedido;
    }

    public void setFechaPedido(LocalDateTime fechaPedido) {
        this.fechaPedido = fechaPedido;
    }

    public LocalDateTime getRegistradaEn() {
        return registradaEn;
    }

    public void setRegistradaEn(LocalDateTime registradaEn) {
        this.registradaEn = registradaEn;
    }

    public List<ItemOrden> getItems() {
        return items;
    }

    public void setItems(List<ItemOrden> items) {
        this.items = items;
    }
}
