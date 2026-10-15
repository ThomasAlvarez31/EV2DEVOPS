package com.pedidos360.identidad.service;

import com.pedidos360.identidad.model.RolUsuario;
import com.pedidos360.identidad.model.Usuario;
import com.pedidos360.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void obtenerOCrearPorOid_deberiaRetornarUsuarioExistente_siYaExiste() {
        Usuario existente = new Usuario("oid-123", "Juan Perez", "juan@duocuc.cl");
        when(usuarioRepository.findByAzureOid("oid-123")).thenReturn(Optional.of(existente));

        Usuario resultado = usuarioService.obtenerOCrearPorOid("oid-123", "Juan Perez", "juan@duocuc.cl");

        assertEquals("juan@duocuc.cl", resultado.getEmail());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void obtenerOCrearPorOid_deberiaCrearUsuario_siNoExiste() {
        when(usuarioRepository.findByAzureOid("oid-999")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario resultado = usuarioService.obtenerOCrearPorOid("oid-999", "Ana Soto", "ana@duocuc.cl");

        assertEquals("oid-999", resultado.getAzureOid());
        assertEquals(RolUsuario.CLIENTE, resultado.getRol());
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }
}
