package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.client.RabbitMgmtClient;
import com.pedidos360.rabbitadmin.dto.BindingInfo;
import com.pedidos360.rabbitadmin.dto.CrearBindingRequest;
import com.pedidos360.rabbitadmin.dto.CrearColaRequest;
import com.pedidos360.rabbitadmin.dto.CrearExchangeRequest;
import com.pedidos360.rabbitadmin.dto.ColaInfo;
import com.pedidos360.rabbitadmin.dto.ExchangeInfo;
import com.pedidos360.rabbitadmin.error.RecursoNoEncontradoException;
import com.pedidos360.rabbitadmin.error.SolicitudInvalidaException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Servicio administrador de RabbitMQ: UNICO lugar del sistema donde se crean
 * y se eliminan colas, exchanges y bindings.
 *
 * Los controladores solo resuelven HTTP; toda la logica de validacion y de
 * declaracion vive aqui, de forma que la regla de negocio queda encapsulada y
 * es la misma aunque se invoque desde otro punto (no hay llamadas directas a
 * la libreria de RabbitMQ desde controladores o clases de dominio).
 *
 * Transporte de operaciones:
 *  - crear / borrar / consultar -> AMQP (puerto 5672, via AmqpAdmin)
 *  - listar                     -> API de gestion (puerto 15672), porque la
 *                                  API AMQP no expuesta de listado
 */
@Service
public class RabbitAdminService {

    /** Nombres admitidos para colas, exchanges y routing keys de binding. */
    private static final Pattern NOMBRE = Pattern.compile("^[a-zA-Z0-9._-]{1,255}$");
    private static final Pattern ROUTING_KEY = Pattern.compile("^[a-zA-Z0-9._:*/-]{1,255}$");

    /** Prefijo reservado por RabbitMQ: los clientes no pueden declarar amq.*. */
    private static final String PREFIJO_RESERVADO = "amq.";

    /** Los argumentos de broker siempre son de la forma x-<nombre>. */
    private static final Pattern CLAVE_ARGUMENTO = Pattern.compile("^x-[a-z0-9-]{1,64}$");

    /** Argumentos que deben ser numeros positivos (milisegundos o conteos). */
    private static final Set<String> ARGUMENTOS_NUMERICOS = Set.of(
            "x-message-ttl", "x-expires", "x-max-length", "x-max-length-bytes",
            "x-max-priority", "x-delivery-limit", "x-queue-lazy-threshold");

    private static final int MAX_ARGUMENTOS = 20;

    private final AmqpAdmin amqpAdmin;
    private final RabbitMgmtClient gestion;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitMgmtClient gestion) {
        this.amqpAdmin = amqpAdmin;
        this.gestion = gestion;
    }

    // ------------------------------------------------------------------
    //  COLAS
    // ------------------------------------------------------------------

    /** Lista todas las colas del broker (API de gestion). */
    public List<ColaInfo> listarColas() {
        return gestion.listar("queues").stream()
                .map(e -> new ColaInfo(
                        texto(e.get("name")),
                        numerico(e.get("messages")),
                        numerico(e.get("consumers")),
                        logico(e.get("durable")),
                        logico(e.get("autoDelete")),
                        mapa(e.get("arguments"))))
                .toList();
    }

    /** Consulta una cola por nombre (AMQP: mensajes pendientes y consumidores). */
    public ColaInfo obtenerCola(String nombre) {
        String nombreLimpio = validarNombre("la cola", nombre);
        QueueInformation info = amqpAdmin.getQueueInfo(nombreLimpio);
        if (info == null) {
            throw new RecursoNoEncontradoException("No existe la cola '" + nombreLimpio + "'.");
        }
        return new ColaInfo(info.getName(), info.getMessageCount(), info.getConsumerCount(),
                null, null, null);
    }

    /** Crea (o redeclara) una cola. Idempotente si la configuracion coincide. */
    public ColaInfo crearCola(CrearColaRequest solicitud) {
        String nombre = validarNombre("la cola", solicitud.name());
        boolean durable = solicitud.durable() == null || solicitud.durable();
        Map<String, Object> argumentos = validarArgumentos(solicitud.arguments(), "de la cola");

        Queue cola = QueueBuilder.durable(nombre).withArguments(argumentos).build();
        amqpAdmin.declareQueue(cola);

        return obtenerCola(nombre);
    }

    /**
     * Elimina una cola.
     *
     * @param ifUnused solo se elimina si no tiene consumidores conectados
     * @param ifEmpty  solo se elimina si no tiene mensajes pendientes
     */
    public void eliminarCola(String nombre, boolean ifUnused, boolean ifEmpty) {
        String nombreLimpio = validarNombre("la cola", nombre);
        if (amqpAdmin.getQueueInfo(nombreLimpio) == null) {
            throw new RecursoNoEncontradoException("No existe la cola '" + nombreLimpio + "'.");
        }
        amqpAdmin.deleteQueue(nombreLimpio, ifUnused, ifEmpty);
    }

    // ------------------------------------------------------------------
    //  EXCHANGES
    // ------------------------------------------------------------------

    /** Lista todos los exchanges del broker (API de gestion). */
    public List<ExchangeInfo> listarExchanges() {
        return gestion.listar("exchanges").stream()
                .map(e -> new ExchangeInfo(
                        texto(e.get("name")),
                        texto(e.get("type")),
                        logico(e.get("durable")),
                        logico(e.get("autoDelete")),
                        mapa(e.get("arguments"))))
                .toList();
    }

    /** Crea (o redeclara) un exchange directo, topic, fanout o headers. */
    public ExchangeInfo crearExchange(CrearExchangeRequest solicitud) {
        String nombre = validarNombre("el exchange", solicitud.name());
        String tipo = solicitud.type().toLowerCase(Locale.ROOT);
        boolean durable = solicitud.durable() == null || solicitud.durable();
        boolean autoDelete = Boolean.TRUE.equals(solicitud.autoDelete());
        Map<String, Object> argumentos = validarArgumentos(solicitud.arguments(), "del exchange");

        Exchange exchange = switch (tipo) {
            case "direct" -> new DirectExchange(nombre, durable, autoDelete, argumentos);
            case "topic" -> new TopicExchange(nombre, durable, autoDelete, argumentos);
            case "fanout" -> new FanoutExchange(nombre, durable, autoDelete, argumentos);
            case "headers" -> new HeadersExchange(nombre, durable, autoDelete, argumentos);
            default -> throw new SolicitudInvalidaException(
                    "Tipo de exchange no soportado: '" + solicitud.type()
                            + "'. Use direct, topic, fanout o headers.");
        };

        amqpAdmin.declareExchange(exchange);
        return new ExchangeInfo(nombre, tipo, durable, autoDelete, argumentos);
    }

    /** Elimina un exchange. */
    public void eliminarExchange(String nombre) {
        String nombreLimpio = validarNombre("el exchange", nombre);
        boolean eliminado = amqpAdmin.deleteExchange(nombreLimpio);
        if (!eliminado) {
            throw new RecursoNoEncontradoException("No existe el exchange '" + nombreLimpio + "'.");
        }
    }

    // ------------------------------------------------------------------
    //  BINDINGS
    // ------------------------------------------------------------------

    /**
     * Lista los bindings del broker (API de gestion). Se omiten los que
     * gener automaticamente RabbitMQ desde el exchange por defecto (""),
     * porque no son administrables.
     */
    public List<BindingInfo> listarBindings() {
        return gestion.listar("bindings").stream()
                .filter(b -> !texto(b.get("source")).isEmpty())
                .filter(b -> "queue".equals(texto(b.get("destination_type"))))
                .map(b -> new BindingInfo(
                        texto(b.get("source")),
                        texto(b.get("destination")),
                        texto(b.get("routing_key")),
                        mapa(b.get("arguments"))))
                .toList();
    }

    /** Enlaza una cola a un exchange con una routing key. */
    public BindingInfo crearBinding(CrearBindingRequest solicitud) {
        String cola = validarNombre("la cola", solicitud.queue());
        String exchange = validarNombre("el exchange", solicitud.exchange());
        String routingKey = validarRoutingKey(solicitud.routingKey());
        Map<String, Object> argumentos = validarArgumentos(solicitud.arguments(), "del binding");

        Binding binding = new Binding(cola, Binding.DestinationType.QUEUE,
                exchange, routingKey, argumentos);
        amqpAdmin.declareBinding(binding);

        return new BindingInfo(exchange, cola, routingKey, argumentos);
    }

    /**
     * Desenlaza una cola de un exchange.
     * La identidad del binding es la tripleta cola + exchange + routingKey.
     */
    public void eliminarBinding(String cola, String exchange, String routingKey) {
        String colaLimpia = validarNombre("la cola", cola);
        String exchangeLimpio = validarNombre("el exchange", exchange);
        String routingKeyLimpia = validarRoutingKey(routingKey);

        Binding binding = new Binding(colaLimpia, Binding.DestinationType.QUEUE,
                exchangeLimpio, routingKeyLimpia, null);
        try {
            amqpAdmin.removeBinding(binding);
        } catch (RuntimeException e) {
            if (mensajeDe(e).contains("NOT_FOUND")) {
                throw new RecursoNoEncontradoException(
                        "No existe el binding cola='" + colaLimpia + "', exchange='"
                                + exchangeLimpio + "', routingKey='" + routingKeyLimpia + "'.");
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------
    //  VALIDACIONES
    // ------------------------------------------------------------------

    /** Valida el nombre de un recurso; lanza 400 si es vacio, invalido o reservado. */
    private String validarNombre(String articulo, String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalidaException("El nombre " + articulo + " es obligatorio y no puede ser vacio.");
        }
        String limpio = nombre.trim();
        if (!NOMBRE.matcher(limpio).matches()) {
            throw new SolicitudInvalidaException(
                    "Nombre invalido para " + articulo + ": '" + limpio
                            + "'. Solo se permiten letras, numeros, '.', '_' o '-' (max. 255 caracteres).");
        }
        if (limpio.toLowerCase(Locale.ROOT).startsWith(PREFIJO_RESERVADO)) {
            throw new SolicitudInvalidaException(
                    "No se puede usar el prefijo reservado '" + PREFIJO_RESERVADO + "': '"
                            + limpio + "' esta reservado para RabbitMQ.");
        }
        return limpio;
    }

    /** Valida la routing key de un binding. */
    private String validarRoutingKey(String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            throw new SolicitudInvalidaException("La routing key del binding es obligatoria.");
        }
        String limpio = routingKey.trim();
        if (!ROUTING_KEY.matcher(limpio).matches()) {
            throw new SolicitudInvalidaException(
                    "Routing key invalida: '" + limpio
                            + "'. Solo se permiten letras, numeros, '.', '_', ':', '*', '/' o '-' (max. 255 caracteres).");
        }
        return limpio;
    }

    /**
     * Valida los argumentos opcionales de broker:
     *  - clave con forma x-<nombre>
     *  - valor de tipo texto, numero o booleano
     *  - los argumentos de tiempo/conteo deben ser numeros positivos
     *  - como maximo 20 argumentos
     */
    private Map<String, Object> validarArgumentos(Map<String, Object> argumentos, String contexto) {
        if (argumentos == null || argumentos.isEmpty()) {
            return Map.of();
        }
        if (argumentos.size() > MAX_ARGUMENTOS) {
            throw new SolicitudInvalidaException(
                    "Se admiten como maximo " + MAX_ARGUMENTOS + " argumentos " + contexto + ".");
        }

        Map<String, Object> limpios = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entrada : argumentos.entrySet()) {
            String clave = entrada.getKey();
            Object valor = entrada.getValue();

            if (clave == null || !CLAVE_ARGUMENTO.matcher(clave).matches()) {
                throw new SolicitudInvalidaException(
                        "Argumento invalido " + contexto + ": '" + clave
                                + "'. Los argumentos deben llamarse x-<nombre> (letras minusculas, numeros o '-').");
            }
            if (valor != null && !(valor instanceof String) && !(valor instanceof Number) && !(valor instanceof Boolean)) {
                throw new SolicitudInvalidaException(
                        "Valor invalido para el argumento '" + clave + "' " + contexto
                                + ": solo se admiten texto, numeros o booleanos.");
            }
            if (ARGUMENTOS_NUMERICOS.contains(clave)) {
                if (!(valor instanceof Number numero) || numero.doubleValue() <= 0) {
                    throw new SolicitudInvalidaException(
                            "El argumento '" + clave + "' " + contexto + " debe ser un numero positivo.");
                }
            }
            limpios.put(clave, valor);
        }
        return limpios;
    }

    // ------------------------------------------------------------------
    //  UTILIDADES DE LECTURA DE LA API DE GESTION
    // ------------------------------------------------------------------

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private static Integer numerico(Object valor) {
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        return null;
    }

    private static Boolean logico(Object valor) {
        if (valor instanceof Boolean logico) {
            return logico;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            return new LinkedHashMap<>((Map<String, Object>) mapa);
        }
        return new LinkedHashMap<>();
    }

    private static String mensajeDe(Throwable excepcion) {
        StringBuilder sb = new StringBuilder();
        Throwable actual = excepcion;
        while (actual != null && sb.length() < 1000) {
            sb.append(actual.getMessage() == null ? "" : actual.getMessage()).append(' ');
            actual = actual.getCause();
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
