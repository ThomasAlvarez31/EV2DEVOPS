package com.pedidos360.despachos.repository;

import com.pedidos360.despachos.model.Despacho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a los despachos (envios) de los pedidos pagados.
 */
@Repository
public interface DespachoRepository extends JpaRepository<Despacho, Long> {

    /**
     * Busca el despacho de un pedido (clave de negocio unica).
     *
     * @param pedidoId pedido de ms-carrito
     * @return el despacho si el pago ya lo genero
     */
    Optional<Despacho> findByPedidoId(Long pedidoId);

    /**
     * Despachos de un usuario, de mas reciente a mas antigua.
     *
     * @param usuarioId oid (Microsoft) o sub (Cognito)
     * @return despachos del usuario
     */
    List<Despacho> findByUsuarioIdOrderByCreadoEnDesc(String usuarioId);

    /**
     * Indica si ya existe un despacho para ese pedido.
     *
     * @param pedidoId pedido de ms-carrito
     * @return true si el despacho ya fue generado
     */
    boolean existsByPedidoId(Long pedidoId);
}
