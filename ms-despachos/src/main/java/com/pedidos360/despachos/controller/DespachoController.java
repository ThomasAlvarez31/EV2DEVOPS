package com.pedidos360.despachos.controller;

import com.pedidos360.despachos.dto.ActualizarEstadoDespachoRequest;
import com.pedidos360.despachos.model.Despacho;
import com.pedidos360.despachos.model.EstadoDespacho;
import com.pedidos360.despachos.service.DespachoService;
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
 * API de consulta de los despachos (envios) de los pedidos pagados.
 *
 * NO existe POST /despachos: los despachos se CREAN solos cuando RabbitMQ
 * entrega el evento PAGO_APROBADO, y avanzan con los cambios de estado del
 * pedido (o manualmente desde esta API por el area de logistica).
 *
 * Todos los recursos estan ligados al usuario autenticado en el JWT, igual
 * que en ms-carrito y ms-ordenes.
 */
@RestController
@RequestMapping("/despachos")
public class DespachoController {

    private final DespachoService despachoService;

    public DespachoController(DespachoService despachoService) {
        this.despachoService = despachoService;
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

    /** Despachos del usuario autenticado, de mas reciente a mas antiguo. */
    @GetMapping
    public ResponseEntity<List<Despacho>> listar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(despachoService.listar(obtenerUsuarioId(jwt)));
    }

    /** Despacho por su id (403 si no es del usuario, 404 si no existe). */
    @GetMapping("/{id}")
    public ResponseEntity<Despacho> obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(despachoService.obtener(obtenerUsuarioId(jwt), id));
    }

    /** Despacho de un pedido concreto (para cruzarlo con el checkout). */
    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<Despacho> obtenerPorPedido(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable Long pedidoId) {
        return ResponseEntity.ok(despachoService.obtenerPorPedido(obtenerUsuarioId(jwt), pedidoId));
    }

    /**
     * Actualizacion manual del envio (area de logistica). Solo se admiten las
     * transiciones permitidas: un valor invalido o una transicion no
     * permitida devuelve 400.
     */
    @PutMapping("/{id}/estado")
    public ResponseEntity<Despacho> cambiarEstado(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable Long id,
                                                  @RequestBody ActualizarEstadoDespachoRequest request) {
        EstadoDespacho nuevoEstado = EstadoDespacho.desde(request == null ? null : request.estado());
        return ResponseEntity.ok(despachoService.cambiarEstado(obtenerUsuarioId(jwt), id, nuevoEstado));
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
