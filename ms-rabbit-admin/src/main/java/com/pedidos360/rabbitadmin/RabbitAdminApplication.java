package com.pedidos360.rabbitadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio administrador de RabbitMQ.
 *
 * Expone una API REST para crear y eliminar colas, exchanges y bindings
 * (ademas de listarlos y consultarlos). Toda la logica de administracion
 * vive en {@link com.pedidos360.rabbitadmin.service.RabbitAdminService},
 * de modo que los controladores solo traducen HTTP <-> servicio.
 */
@SpringBootApplication
public class RabbitAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitAdminApplication.class, args);
    }
}
