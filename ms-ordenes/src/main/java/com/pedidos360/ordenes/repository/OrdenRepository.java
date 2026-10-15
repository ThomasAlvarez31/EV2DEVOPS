package com.pedidos360.ordenes.repository;

import com.pedidos360.ordenes.model.Orden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a las ordenes de compra.
 */
@Repository
public interface OrdenRepository extends JpaRepository<Orden, Long> {

    /**
     * Busca la orden de un pedido (clave de negocio unica).
     *
     * @param pedidoId pedido de ms-carrito
     * @return la orden correspondiente si ya fue registrada
     */
    Optional<Orden> findByPedidoId(Long pedidoId);

    /**
     * Ordenes de un usuario, de mas reciente a mas antigua.
     *
     * @param usuarioId oid (Microsoft) o sub (Cognito)
     * @return ordenes del usuario
     */
    List<Orden> findByUsuarioIdOrderByRegistradaEnDesc(String usuarioId);

    /**
     * Indica si ya existe una orden para ese pedido.
     *
     * @param pedidoId pedido de ms-carrito
     * @return true si la orden ya fue registrada
     */
    boolean existsByPedidoId(Long pedidoId);
}
