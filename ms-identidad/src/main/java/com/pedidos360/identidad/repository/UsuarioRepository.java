package com.pedidos360.identidad.repository;

import com.pedidos360.identidad.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByAzureOid(String azureOid);

    boolean existsByAzureOid(String azureOid);
}
