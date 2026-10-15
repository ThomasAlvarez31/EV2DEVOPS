package com.pedidos360.rabbitadmin.error;

import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduce las excepciones de la API a respuestas HTTP con mensajes claros.
 *
 * Todas las respuestas de error tienen la misma forma:
 *   { "status": 400, "error": "...", "message": "...", "details": [...] }
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Validacion de los DTO (@NotBlank / @Pattern / @Size). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidar(MethodArgumentNotValidException ex) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(fielError -> fielError.getField() + ": " + fielError.getDefaultMessage())
                .sorted()
                .toList();

        return responder(HttpStatus.BAD_REQUEST, "Solicitud invalida",
                "Los parametros de entrada no son validos. Corrija los siguientes campos:", detalles);
    }

    /** JSON mal formado o tipos incorrectos en el cuerpo. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Solicitud invalida",
                "El cuerpo de la peticion no es JSON valido o tiene tipos de datos incorrectos.", List.of());
    }

    /** Validaciones hechas dentro del servicio (argumentos, prefijos reservados). */
    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<Map<String, Object>> solicitudInvalida(SolicitudInvalidaException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Solicitud invalida", ex.getMessage(), List.of());
    }

    /** Cola, exchange o binding inexistente. */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), List.of());
    }

    /** Broker caido o API de gestion inaccesible. */
    @ExceptionHandler(BrokerNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> brokerNoDisponible(BrokerNoDisponibleException ex) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, "Broker no disponible", ex.getMessage(), List.of());
    }

    /** El broker no acepta la conexion (RabbitMQ apagado o credenciales malas). */
    @ExceptionHandler(AmqpConnectException.class)
    public ResponseEntity<Map<String, Object>> sinConexion(AmqpConnectException ex) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, "Broker no disponible",
                "No se pudo conectar con RabbitMQ. Verifique que este corriendo y las credenciales RABBITMQ_*.", List.of());
    }

    /**
     * Errores de protocolo devueltos por el broker: configuracion incompatible
     * (PRECONDITION_FAILED) u otros fallos de la declaracion.
     */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<Map<String, Object>> errorAmqp(AmqpException ex) {
        String causa = mensajeDe(ex);

        if (causa.contains("NOT_FOUND")) {
            return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado",
                    "El recurso indicado no existe en el broker.", List.of());
        }
        if (causa.contains("PRECONDITION_FAILED") || causa.contains("INEQUIVALENT")) {
            return responder(HttpStatus.CONFLICT, "Configuracion incompatible",
                    "El recurso ya existe con una configuracion distinta a la solicitada. "
                            + "Eliminelo primero o use la misma configuracion.", List.of());
        }
        if (causa.contains("ACCESS_REFUSED")) {
            return responder(HttpStatus.FORBIDDEN, "Acceso rechazado",
                    "El usuario de RabbitMQ no tiene permisos para esta operacion.", List.of());
        }
        if (ex instanceof AmqpIOException && causa.contains("Connection")) {
            return responder(HttpStatus.SERVICE_UNAVAILABLE, "Broker no disponible",
                    "No se pudo conectar con RabbitMQ.", List.of());
        }
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error del broker",
                "RabbitMQ rechazo la operacion: " + causa, List.of());
    }

    /** Faltan query params obligatorios (p. ej. DELETE /bindings). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> parametroFaltante(MissingServletRequestParameterException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Solicitud invalida",
                "Falta el parametro obligatorio '" + ex.getParameterName() + "'.", List.of());
    }

    /** Content-Type distinto de application/json. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> tipoNoSoportado(HttpMediaTypeNotSupportedException ex) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado",
                "Use Content-Type: application/json.", List.of());
    }

    /** Verbo HTTP no permitido para la ruta. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> verboNoSoportado(HttpRequestMethodNotSupportedException ex) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Metodo no permitido",
                "El verbo " + ex.getMethod() + " no esta permitido en este recurso.", List.of());
    }

    /** Ruta inexistente: se responde 404 y no 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> rutaInexistente(NoResourceFoundException ex) {
        return responder(HttpStatus.NOT_FOUND, "Ruta no encontrada",
                "No existe el recurso " + ex.getResourcePath() + ".", List.of());
    }

    /** Cualquier otro fallo inesperado: 500 con mensaje generico (sin volcar stack). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception ex) {
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrio un error inesperado al administrar RabbitMQ.", List.of());
    }

    private static String mensajeDe(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable actual = ex;
        while (actual != null && sb.length() < 1000) {
            sb.append(actual.getMessage() == null ? "" : actual.getMessage()).append(' ');
            actual = actual.getCause();
        }
        return sb.toString().toUpperCase();
    }

    private static ResponseEntity<Map<String, Object>> responder(
            HttpStatus status, String error, String mensaje, List<String> detalles) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", status.value());
        cuerpo.put("error", error);
        cuerpo.put("message", mensaje);
        if (!detalles.isEmpty()) {
            cuerpo.put("details", detalles);
        }
        return ResponseEntity.status(status).body(cuerpo);
    }
}
