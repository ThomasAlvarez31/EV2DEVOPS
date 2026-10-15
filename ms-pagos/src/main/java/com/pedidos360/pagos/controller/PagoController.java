package com.pedidos360.pagos.controller;

import com.pedidos360.pagos.dto.SolicitudPago;
import com.pedidos360.pagos.model.Pago;
import com.pedidos360.pagos.service.PagoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * API del microservicio de pagos.
 *
 * Lo llama ms-carrito al confirmar la compra (POST /pedidos/{id}/pago en la API
 * pública). Ningún navegador habla directamente con este servicio: la tienda
 * expone el resultado dentro del pedido.
 */
@RestController
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    /** Registra (simula) el pago de un pedido y devuelve el resultado. */
    @PostMapping("/pagos")
    public ResponseEntity<Pago> pagar(@RequestBody SolicitudPago solicitud) {
        Pago pago = pagoService.registrar(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(pago);
    }

    /** Consulta el pago registrado para un pedido (para auditoría/soporte). */
    @GetMapping("/pagos/pedido/{pedidoId}")
    public ResponseEntity<Pago> obtenerPorPedido(@PathVariable Long pedidoId) {
        return ResponseEntity.ok(pagoService.obtenerPorPedido(pedidoId));
    }

    // ------------------------------------------------------------- excepciones

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> manejarNoEncontrado(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> manejarSolicitudInvalida(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
