package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Solicitud para crear una cola.
 *
 * name no puede ser vacio ni contener caracteres invalidos: el patron rechaza
 * nombres vacios y espacios en blanco antes de tocar el broker, con un
 * mensaje de error claro (ver ApiExceptionHandler).
 *
 * durable es opcional: si no viene, la cola se crea durable (true), que es
 * el comportamiento recomendado en RabbitMQ.
 */
public record CrearColaRequest(

        @NotBlank(message = "el nombre de la cola es obligatorio")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{1,255}$",
                message = "el nombre de la cola debe tener entre 1 y 255 caracteres y solo puede usar letras, numeros, '.', '_' o '-'")
        String name,

        Boolean durable,

        @Size(max = 20, message = "como maximo se admiten 20 argumentos de cola")
        Map<String, Object> arguments
) {
}
