package com.pedidos360.ordenes.controller;

import com.pedidos360.ordenes.dto.ActualizarEstadoOrdenRequest;
import com.pedidos360.ordenes.model.EstadoOrden;
import com.pedidos360.ordenes.model.Orden;
import com.pedidos360.ordenes.service.OrdenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * API de consulta de las ordenes de compra.
 *
 * NO existe POST /ordenes: las ordenes se CREAN solas cuando RabbitMQ entrega
 * el evento PEDIDO_REGISTRADO (esto es el orders-service del enunciado:
 * microservicio con colas de RabbitMQ, con valor de negocio y con API propia).
 *
 * Todos los recursos estan ligados al usuario autenticado en el JWT, igual
 * que en ms-carrito.
 */
@RestController
@RequestMapping("/ordenes")
public class OrdenController {

    private final OrdenService ordenService;

    public OrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    /**
     * Identificador del usuario: el claim "oid" de Microsoft Entra ID. Los
     * tokens de Amazon Cognito no lo traen, asi que se usa el "sub"
     * (presente en los DOS emisores admitidos).
     */
    private static String obtenerUsuarioId(Jwt jwt) {
        String oid = jwt.getClaimAsString("oid");
        if (oid == null || oid.isBlank()) {
            oid = jwt.getSubject();
        }
        if (oid == null || oid.isBlank()) {
            throw new IllegalStateException("El token no incluye un identificador de usuario (oid/sub)");
        }
        return oid;
    }

    /** Ordenes del usuario autenticado, de mas reciente a mas antigua. */
    @GetMapping
    public ResponseEntity<List<Orden>> listar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ordenService.listar(obtenerUsuarioId(jwt)));
    }

    /** Orden por su id (403 si no es del usuario, 404 si no existe). */
    @GetMapping("/{id}")
    public ResponseEntity<Orden> obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(ordenService.obtener(obtenerUsuarioId(jwt), id));
    }

    /** Orden de un pedido concreto (para cruzarla con el checkout). */
    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<Orden> obtenerPorPedido(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable Long pedidoId) {
        return ResponseEntity.ok(ordenService.obtenerPorPedido(obtenerUsuarioId(jwt), pedidoId));
    }

    /**
     * Anulacion manual de una orden. Solo se admite desde REGISTRADA o
     * PAGADA: un valor invalido o una transicion no permitida devuelve 400.
     */
    @PutMapping("/{id}/estado")
    public ResponseEntity<Orden> cambiarEstado(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable Long id,
                                               @RequestBody ActualizarEstadoOrdenRequest request) {
        EstadoOrden nuevoEstado = EstadoOrden.desde(request == null ? null : request.estado());
        return ResponseEntity.ok(ordenService.cambiarEstado(obtenerUsuarioId(jwt), id, nuevoEstado));
    }

    // ------------------------------------------------------------- excepciones

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> manejarNoEncontrado(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> manejarSinPermiso(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> manejarSolicitudInvalida(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
