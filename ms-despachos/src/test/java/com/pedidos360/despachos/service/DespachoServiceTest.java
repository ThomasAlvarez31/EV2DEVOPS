package com.pedidos360.despachos.service;

import com.pedidos360.despachos.messaging.EventoDespacho;
import com.pedidos360.despachos.model.Despacho;
import com.pedidos360.despachos.model.EstadoDespacho;
import com.pedidos360.despachos.repository.DespachoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la generacion y el avance de los despachos a partir de los
 * eventos de RabbitMQ, y de las reglas de consulta/avance de la API.
 *
 * Cobertura clave: el pago aprobado genera UN despacho por pedido
 * (idempotencia), el envio avanza con el estado logistico y un despacho
 * entregado ya no se puede mover.
 */
@ExtendWith(MockitoExtension.class)
class DespachoServiceTest {

    @Mock
    private DespachoRepository despachoRepository;

    @InjectMocks
    private DespachoService despachoService;

    private Despacho despacho;

    @BeforeEach
    void setUp() {
        despacho = new Despacho(7L, "usuario-42",
                java.time.LocalDateTime.of(2026, 10, 8, 12, 30));
        despacho.setId(1L);
    }

    private static EventoDespacho evento(String tipo, Long pedidoId, String estado) {
        return new EventoDespacho(tipo, pedidoId, "usuario-42", estado,
                "TARJETA_CREDITO", 749.97, "2026-10-08T12:30:00", List.of());
    }

    // --------------------------------------------------- creacion por evento

    @Test
    void aplicar_deberiaGenerarElDespacho_alRecibirPagoAprobado() {
        when(despachoRepository.existsByPedidoId(7L)).thenReturn(false);

        DespachoService.Resultado resultado =
                despachoService.aplicar(evento(EventoDespacho.TIPO_PAGO_APROBADO, 7L, "CONFIRMADO"));

        assertEquals(DespachoService.Resultado.CREADO, resultado);

        ArgumentCaptor<Despacho> guardado = ArgumentCaptor.forClass(Despacho.class);
        verify(despachoRepository).save(guardado.capture());
        Despacho creado = guardado.getValue();
        assertEquals(7L, creado.getPedidoId());
        assertEquals("usuario-42", creado.getUsuarioId());
        assertEquals("P360-00007", creado.getGuia());
        assertEquals(EstadoDespacho.PENDIENTE, creado.getEstado());
        assertEquals("CONFIRMADO", creado.getEstadoPedido());
        assertEquals(java.time.LocalDateTime.of(2026, 10, 8, 12, 30), creado.getFechaPedido());
    }

    @Test
    void aplicar_noDeberiaGenerarDosDespachos_siElEventoSeRepite() {
        when(despachoRepository.existsByPedidoId(7L)).thenReturn(true);

        DespachoService.Resultado resultado =
                despachoService.aplicar(evento(EventoDespacho.TIPO_PAGO_APROBADO, 7L, "CONFIRMADO"));

        assertEquals(DespachoService.Resultado.REPETIDO, resultado);
        verify(despachoRepository, never()).save(any());
    }

    // -------------------------------------------------------- avance

    @Test
    void aplicar_deberiaPonerEnRutaElDespacho_cuandoElPedidoSeEnvia() {
        when(despachoRepository.findByPedidoId(7L)).thenReturn(Optional.of(despacho));

        DespachoService.Resultado resultado =
                despachoService.aplicar(evento(EventoDespacho.TIPO_CAMBIO_ESTADO, 7L, "ENVIADO"));

        assertEquals(DespachoService.Resultado.ACTUALIZADO, resultado);
        assertEquals(EstadoDespacho.EN_RUTA, despacho.getEstado());
        assertEquals("ENVIADO", despacho.getEstadoPedido());
        verify(despachoRepository).save(despacho);
    }

    @Test
    void aplicar_deberiaMarcarEntregadoElDespacho_cuandoElPedidoSeEntrega() {
        despacho.setEstado(EstadoDespacho.EN_RUTA);
        when(despachoRepository.findByPedidoId(7L)).thenReturn(Optional.of(despacho));

        DespachoService.Resultado resultado =
                despachoService.aplicar(evento(EventoDespacho.TIPO_CAMBIO_ESTADO, 7L, "ENTREGADO"));

        assertEquals(DespachoService.Resultado.ACTUALIZADO, resultado);
        assertEquals(EstadoDespacho.ENTREGADO, despacho.getEstado());
        verify(despachoRepository).save(despacho);
    }

    @Test
    void aplicar_deberiaGuardarElEstadoPedido_aunSiNoCambiaElDespacho() {
        when(despachoRepository.findByPedidoId(7L)).thenReturn(Optional.of(despacho));

        DespachoService.Resultado resultado =
                despachoService.aplicar(evento(EventoDespacho.TIPO_CAMBIO_ESTADO, 7L, "EN_PREPARACION"));

        assertEquals(DespachoService.Resultado.ACTUALIZADO, resultado);
        assertEquals(EstadoDespacho.PENDIENTE, despacho.getEstado());
        assertEquals("EN_PREPARACION", despacho.getEstadoPedido());
        verify(despachoRepository).save(despacho);
    }

    @Test
    void aplicar_noDeberiaRetrocederUnDespachoEntregado() {
        despacho.setEstado(EstadoDespacho.ENTREGADO);
        despacho.setEstadoPedido("ENTREGADO");
        when(despachoRepository.findByPedidoId(7L)).thenReturn(Optional.of(despacho));

        DespachoService.Resultado resultado =
                despachoService.aplicar(evento(EventoDespacho.TIPO_CAMBIO_ESTADO, 7L, "ENVIADO"));

        assertEquals(DespachoService.Resultado.REPETIDO, resultado);
        assertEquals(EstadoDespacho.ENTREGADO, despacho.getEstado());
        verify(despachoRepository, never()).save(any());
    }

    @Test
    void aplicar_noDeberiaRepetirLaTransicion_siElEstadoEsElMismo() {
        despacho.setEstado(EstadoDespacho.EN_RUTA);
        despacho.setEstadoPedido("ENVIADO");
        when(despachoRepository.findByPedidoId(7L)).thenReturn(Optional.of(despacho));

        DespachoService.Resultado resultado =
                despachoService.aplicar(evento(EventoDespacho.TIPO_CAMBIO_ESTADO, 7L, "ENVIADO"));

        assertEquals(DespachoService.Resultado.REPETIDO, resultado);
        verify(despachoRepository, never()).save(any());
    }

    @Test
    void aplicar_deberiaDescartarElEvento_siElPedidoNoTieneDespacho() {
        when(despachoRepository.findByPedidoId(7L)).thenReturn(Optional.empty());

        DespachoService.Resultado resultado =
                despachoService.aplicar(evento(EventoDespacho.TIPO_CAMBIO_ESTADO, 7L, "ENVIADO"));

        assertEquals(DespachoService.Resultado.IGNORADO, resultado);
        verify(despachoRepository, never()).save(any());
    }

    // ------------------------------------------------------ eventos invalidos

    @Test
    void aplicar_deberiaDescartarUnEventoAjenoAlDominio() {
        EventoDespacho ajeno = evento("PEDIDO_REGISTRADO", 7L, "PENDIENTE_PAGO");

        DespachoService.Resultado resultado = despachoService.aplicar(ajeno);

        assertEquals(DespachoService.Resultado.IGNORADO, resultado);
        verify(despachoRepository, never()).save(any());
    }

    @Test
    void aplicar_deberiaRechazarElEvento_siNoTienePedidoId() {
        EventoDespacho sinPedido = evento(EventoDespacho.TIPO_PAGO_APROBADO, null, "CONFIRMADO");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> despachoService.aplicar(sinPedido));

        assertTrue(error.getMessage().contains("pedidoId"));
    }

    @Test
    void aplicar_deberiaRechazarElPago_siNoTieneUsuarioId() {
        EventoDespacho sinUsuario = new EventoDespacho(EventoDespacho.TIPO_PAGO_APROBADO, 7L, null,
                "CONFIRMADO", null, null, null, List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> despachoService.aplicar(sinUsuario));

        assertTrue(error.getMessage().contains("usuarioId"));
    }

    @Test
    void aplicar_deberiaGenerarElDespachoAunSiLaFechaEsIlegible() {
        when(despachoRepository.existsByPedidoId(7L)).thenReturn(false);

        despachoService.aplicar(new EventoDespacho(EventoDespacho.TIPO_PAGO_APROBADO, 7L,
                "usuario-42", "CONFIRMADO", null, null, "ayer", List.of()));

        ArgumentCaptor<Despacho> guardado = ArgumentCaptor.forClass(Despacho.class);
        verify(despachoRepository).save(guardado.capture());
        assertNull(guardado.getValue().getFechaPedido());
    }

    // ------------------------------------------------------------ API

    @Test
    void obtener_deberiaDevolverElDespacho_siEsDelUsuario() {
        when(despachoRepository.findById(1L)).thenReturn(Optional.of(despacho));

        Despacho resultado = despachoService.obtener("usuario-42", 1L);

        assertEquals(7L, resultado.getPedidoId());
    }

    @Test
    void obtener_deberiaRechazar_siElDespachoEsDeOtroUsuario() {
        when(despachoRepository.findById(1L)).thenReturn(Optional.of(despacho));

        assertThrows(AccessDeniedException.class, () -> despachoService.obtener("otro-usuario", 1L));
    }

    @Test
    void obtener_deberiaLanzar404_siElDespachoNoExiste() {
        when(despachoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> despachoService.obtener("usuario-42", 99L));
    }

    @Test
    void cambiarEstado_deberiaMarcarEntregado_manualmente() {
        despacho.setEstado(EstadoDespacho.EN_RUTA);
        when(despachoRepository.findById(1L)).thenReturn(Optional.of(despacho));
        when(despachoRepository.save(despacho)).thenReturn(despacho);

        Despacho resultado = despachoService.cambiarEstado("usuario-42", 1L, EstadoDespacho.ENTREGADO);

        assertEquals(EstadoDespacho.ENTREGADO, resultado.getEstado());
    }

    @Test
    void cambiarEstado_deberiaRechazarUnaTransicionNoPermitida() {
        despacho.setEstado(EstadoDespacho.ENTREGADO);
        when(despachoRepository.findById(1L)).thenReturn(Optional.of(despacho));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> despachoService.cambiarEstado("usuario-42", 1L, EstadoDespacho.PENDIENTE));

        assertTrue(error.getMessage().contains("no permitida"));
        verify(despachoRepository, never()).save(any());
    }

    @Test
    void cambiarEstado_deberiaRechazarUnEstadoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> despachoService.cambiarEstado("usuario-42", 1L, null));
    }
}
