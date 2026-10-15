package com.pedidos360.monitoreo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de monitoreo: consume el log de eventos de negocio de Kafka
 * (topico pedidos360.monitoreo) y lo expone para observar la actividad de la
 * plataforma (eventos recientes y estadisticas).
 */
@SpringBootApplication
public class MonitoreoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoreoApplication.class, args);
    }
}
