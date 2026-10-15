package com.pedidos360.identidad.controller;

import com.pedidos360.identidad.dto.UsuarioDTO;
import com.pedidos360.identidad.model.RolUsuario;
import com.pedidos360.identidad.model.Usuario;
import com.pedidos360.identidad.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Devuelve (y crea si no existe) el perfil del usuario autenticado.
     * Los claims se leen con respaldo porque el mismo endpoint atiende tokens
     * de los DOS emisores admitidos:
     *   Microsoft Entra ID -> oid / name / preferred_username
     *   Amazon Cognito     -> sub / cognito:username / email
     */
    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> obtenerPerfilActual(@AuthenticationPrincipal Jwt jwt) {
        String email = primerValor(jwt.getClaimAsString("preferred_username"),
                                   jwt.getClaimAsString("email"));
        String oid = primerValor(jwt.getClaimAsString("oid"), jwt.getSubject());
        String nombre = primerValor(jwt.getClaimAsString("name"),
                                    jwt.getClaimAsString("cognito:username"),
                                    email);

        Usuario usuario = usuarioService.obtenerOCrearPorOid(oid, nombre, email);
        return ResponseEntity.ok(new UsuarioDTO(usuario));
    }

    /** Primer valor no vacio: los claims cambian segun quien emita el token. */
    private static String primerValor(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioDTO>> listarUsuarios() {
        List<UsuarioDTO> usuarios = usuarioService.listarTodos().stream()
                .map(UsuarioDTO::new)
                .toList();
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioDTO> obtenerUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(new UsuarioDTO(usuarioService.obtenerPorId(id)));
    }

    @PutMapping("/{id}/rol")
    public ResponseEntity<UsuarioDTO> actualizarRol(@PathVariable Long id, @RequestBody ActualizarRolRequest request) {
        Usuario actualizado = usuarioService.actualizarRol(id, request.rol());
        return ResponseEntity.ok(new UsuarioDTO(actualizado));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<String> manejarNoEncontrado(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    public record ActualizarRolRequest(RolUsuario rol) {
    }
}
