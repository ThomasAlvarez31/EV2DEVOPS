package com.pedidos360.rabbitadmin.dto;

import java.util.Map;

/** Informacion de un exchange expuesta por la API. */
public record ExchangeInfo(
        String name,
        String type,
        Boolean durable,
        Boolean autoDelete,
        Map<String, Object> arguments
) {
}
