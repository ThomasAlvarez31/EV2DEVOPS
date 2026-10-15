package com.pedidos360.rabbitadmin.dto;

import java.util.Map;

/**
 * Informacion de un binding (enlace cola <-> exchange) expuesta por la API.
 * El identificador del binding es la tripleta exchange + queue + routingKey.
 */
public record BindingInfo(
        String exchange,
        String queue,
        String routingKey,
        Map<String, Object> arguments
) {
}
