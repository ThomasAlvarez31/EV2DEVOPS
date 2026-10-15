package com.pedidos360.productos.repository;

import com.pedidos360.productos.model.EventoStockProcesado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acceso a los eventos de stock ya aplicados (idempotencia del consumidor).
 */
@Repository
public interface EventoStockProcesadoRepository extends JpaRepository<EventoStockProcesado, Long> {

    /**
     * Indica si un evento ya fue aplicado al inventario.
     *
     * @param tipo     tipo de evento (PEDIDO_REGISTRADO, PAGO_RECHAZADO)
     * @param pedidoId identificador del pedido
     * @return true si el evento ya se aplico (evento repetido)
     */
    boolean existsByTipoAndPedidoId(String tipo, Long pedidoId);
}
