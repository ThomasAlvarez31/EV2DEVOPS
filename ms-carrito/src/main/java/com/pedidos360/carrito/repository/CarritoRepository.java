package com.pedidos360.carrito.repository;

import com.pedidos360.carrito.model.Carrito;
import com.pedidos360.carrito.model.EstadoCarrito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CarritoRepository extends JpaRepository<Carrito, Long> {

    Optional<Carrito> findByUsuarioIdAndEstado(String usuarioId, EstadoCarrito estado);
}
