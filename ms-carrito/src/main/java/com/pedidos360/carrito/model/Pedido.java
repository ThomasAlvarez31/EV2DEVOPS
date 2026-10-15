package com.pedidos360.carrito.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot de una compra, generado a partir de un Carrito en el checkout.
 * Se guarda por separado del carrito para no perder el historial de compras
 * aunque el carrito de origen siga cambiando.
 *
 * Guarda ademas el metodo de pago, el estado del pedido y el estado del pago
 * (simulado), de modo que el historial conserve como se pago cada compra.
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;

    @Column(nullable = false)
    private Double total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado = EstadoPedido.PENDIENTE_PAGO;

    /**
     * Estado del pago simulado. Nullable por compatibilidad con pedidos
     * antiguos creados antes de incorporar la etapa de pago.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago")
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    /** Metodo de pago elegido en el checkout (nullable por pedidos antiguos). */
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago")
    private MetodoPago metodoPago;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ItemPedido> items = new ArrayList<>();

    public Pedido() {
    }

    public Pedido(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    /** Devuelve PENDIENTE para los pedidos creados antes de esta funcionalidad. */
    public EstadoPago getEstadoPago() {
        return estadoPago == null ? EstadoPago.PENDIENTE : estadoPago;
    }

    public void setEstadoPago(EstadoPago estadoPago) {
        this.estadoPago = estadoPago;
    }

    public MetodoPago getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(MetodoPago metodoPago) {
        this.metodoPago = metodoPago;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public List<ItemPedido> getItems() {
        return items;
    }

    public void setItems(List<ItemPedido> items) {
        this.items = items;
    }

    public void agregarItem(ItemPedido item) {
        item.setPedido(this);
        this.items.add(item);
    }

    /** Solo un pedido pendiente de pago puede recibir un intento de pago. */
    public boolean admitePago() {
        return getEstadoPago() == EstadoPago.PENDIENTE;
    }
}
