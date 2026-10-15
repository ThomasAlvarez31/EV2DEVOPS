package com.pedidos360.pagos.service;

import com.pedidos360.pagos.dto.SolicitudPago;
import com.pedidos360.pagos.model.EstadoPago;
import com.pedidos360.pagos.model.MetodoPago;
import com.pedidos360.pagos.model.Pago;
import com.pedidos360.pagos.repository.PagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Lógica de negocio de los pagos SIMULADOS.
 *
 * Reglas:
 *  - Un pedido solo puede pagarse una vez (si ya existe un pago se rechaza).
 *  - El monto lo envía ms-carrito (ya calculado en el servidor), no el navegador.
 *  - `simularRechazo` permite mostrar el camino de error en la demo;
 *    aquí no hay pasarela bancaria ni datos de tarjeta reales.
 */
@Service
public class PagoService {

    private final PagoRepository pagoRepository;

    public PagoService(PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    @Transactional
    public Pago registrar(SolicitudPago solicitud) {
        if (solicitud == null || solicitud.pedidoId() == null) {
            throw new IllegalArgumentException("Debe indicar el pedido a pagar");
        }
        if (solicitud.monto() == null || solicitud.monto() <= 0) {
            throw new IllegalArgumentException("El monto a pagar debe ser mayor a 0");
        }

        if (pagoRepository.existsByPedidoId(solicitud.pedidoId())) {
            Pago existente = pagoRepository.findByPedidoId(solicitud.pedidoId()).orElseThrow();
            throw new IllegalStateException(
                    "El pedido " + solicitud.pedidoId() + " ya tiene un pago procesado (estado: "
                            + existente.getEstado() + ")");
        }

        MetodoPago metodoPago = MetodoPago.desde(solicitud.metodoPago());
        boolean rechazado = solicitud.esRechazoSimulado();

        EstadoPago estado = rechazado ? EstadoPago.RECHAZADO : EstadoPago.APROBADO;
        String motivo = rechazado ? "Pago rechazado por el banco (simulación)" : null;

        return pagoRepository.save(new Pago(
                solicitud.pedidoId(),
                solicitud.usuarioId() == null ? "desconocido" : solicitud.usuarioId(),
                solicitud.monto(),
                metodoPago,
                estado,
                motivo));
    }

    public Pago obtenerPorPedido(Long pedidoId) {
        if (pedidoId == null) {
            throw new IllegalArgumentException("Debe indicar un pedido");
        }
        return pagoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("Pago no encontrado para el pedido: " + pedidoId));
    }
}
