package com.pedidos360.pagos.repository;

import com.pedidos360.pagos.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    /** El pago de un pedido (a lo sumo uno, por la restricción única). */
    Optional<Pago> findByPedidoId(Long pedidoId);

    boolean existsByPedidoId(Long pedidoId);
}
