package com.pedidos360.monitoreo.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.monitoreo.model.EventoMonitor;
import com.pedidos360.monitoreo.store.EventoStore;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor del log de eventos de negocio (topico pedidos360.monitoreo).
 *
 * El grupo de consumidor "ms-monitoreo" mantiene su propio offset: si el
 * servicio se reinicia, vuelve a leer desde donde quedo (auto-offset-reset
 * earliest solo aplica la primera vez). A diferencia de una cola, el mensaje
 * NO se borra al consumirlo: sigue en el topico y otro consumidor podria
 * leerlo tambien (por ejemplo para nuevas metricas).
 */
@Component
public class EventoConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventoConsumer.class);

    /** Topico que publica ms-carrito (mismo valor que MonitorPublisher.TOPICO). */
    public static final String TOPICO = "pedidos360.monitoreo";

    private final EventoStore store;
    private final ObjectMapper objectMapper;

    public EventoConsumer(EventoStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPICO)
    public void escuchar(ConsumerRecord<String, String> record) {
        try {
            EventoMonitor evento = objectMapper
                    .readValue(record.value(), EventoMonitor.class)
                    .enPosicion(record.topic(), record.partition(), record.offset());

            store.registrar(evento);
            log.info("Evento {} (pedido {}) recibido de Kafka en {}[{}] offset={}",
                    evento.tipo(), evento.pedidoId(),
                    record.topic(), record.partition(), record.offset());
        } catch (JsonProcessingException e) {
            // Un mensaje ilegible no debe tumbar el consumidor: se registra y
            // se sigue con el siguiente (el offset se avanza igual).
            log.error("Mensaje ilegible en Kafka {}[{}] offset={}: {}",
                    record.topic(), record.partition(), record.offset(), record.value());
        }
    }
}
