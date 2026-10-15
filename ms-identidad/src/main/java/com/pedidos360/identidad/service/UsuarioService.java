package com.pedidos360.identidad.service;

import com.pedidos360.identidad.model.RolUsuario;
import com.pedidos360.identidad.model.Usuario;
import com.pedidos360.identidad.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Busca el perfil asociado al oid del token de Azure AD. Si es la primera vez
     * que este usuario llama al backend, se crea automáticamente su perfil (auto-provisioning).
     */
    public Usuario obtenerOCrearPorOid(String azureOid, String nombre, String email) {
        return usuarioRepository.findByAzureOid(azureOid)
                .orElseGet(() -> usuarioRepository.save(new Usuario(azureOid, nombre, email)));
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario obtenerPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + id));
    }

    public Usuario actualizarRol(Long id, RolUsuario nuevoRol) {
        Usuario usuario = obtenerPorId(id);
        usuario.setRol(nuevoRol);
        return usuarioRepository.save(usuario);
    }
}
