package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Solicitud para crear un exchange.
 *
 * type es obligatorio y solo admite los cuatro tipos de AMQP; el nombre sigue
 * el mismo patron que las colas para descartar entradas vacias o invalidas.
 */
public record CrearExchangeRequest(

        @NotBlank(message = "el nombre del exchange es obligatorio")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{1,255}$",
                message = "el nombre del exchange debe tener entre 1 y 255 caracteres y solo puede usar letras, numeros, '.', '_' o '-'")
        String name,

        @NotNull(message = "el tipo de exchange es obligatorio (direct, topic, fanout o headers)")
        @Pattern(
                regexp = "^(direct|topic|fanout|headers)$",
                message = "el tipo de exchange debe ser direct, topic, fanout o headers")
        String type,

        Boolean durable,

        Boolean autoDelete,

        @Size(max = 20, message = "como maximo se admiten 20 argumentos de exchange")
        Map<String, Object> arguments
) {
}
