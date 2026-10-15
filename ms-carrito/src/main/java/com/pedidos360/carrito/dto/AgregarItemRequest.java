package com.pedidos360.carrito.dto;

public record AgregarItemRequest(
        Long productoId,
        String nombreProducto,
        Double precioUnitario,
        Integer cantidad
) {
}
