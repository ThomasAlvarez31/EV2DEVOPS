package com.pedidos360.ordenes.service;

import com.pedidos360.ordenes.messaging.EventoOrden;
import com.pedidos360.ordenes.model.EstadoOrden;
import com.pedidos360.ordenes.model.Orden;
import com.pedidos360.ordenes.repository.OrdenRepository;
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
 * Pruebas del registro formal de ordenes a partir de los eventos de
 * RabbitMQ, y de las reglas de consulta/anulacion de la API.
 *
 * Cobertura clave: el evento crea la orden UNA sola vez (idempotencia por
 * pedidoId), los pagos mueven su estado y nunca se inventa una orden a
 * partir de un evento suelto.
 */
@ExtendWith(MockitoExtension.class)
class OrdenServiceTest {

    @Mock
    private OrdenRepository ordenRepository;

    @InjectMocks
    private OrdenService ordenService;

    private Orden orden;

    @BeforeEach
    void setUp() {
        orden = new Orden(7L, "usuario-42", 749.97, "TARJETA_CREDITO",
                java.time.LocalDateTime.of(2026, 10, 8, 12, 30));
        orden.setId(1L);
        orden.agregarItem(1L, "Monitor 27\"", 3, 249.99);
    }

    private static EventoOrden evento(String tipo, Long pedidoId, EventoOrden.ItemOrdenEvent... items) {
        return new EventoOrden(tipo, pedidoId, "usuario-42", "PENDIENTE_PAGO",
                "TARJETA_CREDITO", 749.97, "2026-10-08T12:30:00", List.of(items));
    }

    private static EventoOrden.ItemOrdenEvent item(Long productoId, int cantidad) {
        return new EventoOrden.ItemOrdenEvent(productoId, "Monitor 27\"", cantidad, 249.99);
    }

    // -------------------------------------------------- creacion por evento

    @Test
    void aplicar_deberiaCrearLaOrden_alRecibirPedidoRegistrado() {
        when(ordenRepository.existsByPedidoId(7L)).thenReturn(false);

        OrdenService.Resultado resultado =
                ordenService.aplicar(evento(EventoOrden.TIPO_PEDIDO_REGISTRADO, 7L, item(1L, 3)));

        assertEquals(OrdenService.Resultado.CREADA, resultado);

        ArgumentCaptor<Orden> guardada = ArgumentCaptor.forClass(Orden.class);
        verify(ordenRepository).save(guardada.capture());
        Orden creada = guardada.getValue();
        assertEquals(7L, creada.getPedidoId());
        assertEquals("usuario-42", creada.getUsuarioId());
        assertEquals(EstadoOrden.REGISTRADA, creada.getEstado());
        assertEquals("PENDIENTE_PAGO", creada.getEstadoPedido());
        assertEquals(749.97, creada.getTotal());
        assertEquals("TARJETA_CREDITO", creada.getMetodoPago());
        assertEquals(java.time.LocalDateTime.of(2026, 10, 8, 12, 30), creada.getFechaPedido());
        assertEquals(1, creada.getItems().size());
        assertEquals(1L, creada.getItems().get(0).getProductoId());
        assertEquals(3, creada.getItems().get(0).getCantidad());
    }

    @Test
    void aplicar_noDeberiaCrearDosOrdenes_siElEventoSeRepite() {
        when(ordenRepository.existsByPedidoId(7L)).thenReturn(true);

        OrdenService.Resultado resultado =
                ordenService.aplicar(evento(EventoOrden.TIPO_PEDIDO_REGISTRADO, 7L, item(1L, 3)));

        assertEquals(OrdenService.Resultado.REPETIDA, resultado);
        verify(ordenRepository, never()).save(any());
    }

    // ------------------------------------------------------- pagos

    @Test
    void aplicar_deberiaMarcarLaOrdenComoPagada_alRecibirPagoAprobado() {
        when(ordenRepository.findByPedidoId(7L)).thenReturn(Optional.of(orden));

        OrdenService.Resultado resultado =
                ordenService.aplicar(evento(EventoOrden.TIPO_PAGO_APROBADO, 7L));

        assertEquals(OrdenService.Resultado.ACTUALIZADA, resultado);
        assertEquals(EstadoOrden.PAGADA, orden.getEstado());
        verify(ordenRepository).save(orden);
    }

    @Test
    void aplicar_noDeberiaVolverAMarcarPagada_unEventoRepetido() {
        orden.setEstado(EstadoOrden.PAGADA);
        when(ordenRepository.findByPedidoId(7L)).thenReturn(Optional.of(orden));

        OrdenService.Resultado resultado =
                ordenService.aplicar(evento(EventoOrden.TIPO_PAGO_APROBADO, 7L));

        assertEquals(OrdenService.Resultado.REPETIDA, resultado);
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void aplicar_deberiaCancelarLaOrden_alRecibirPagoRechazado() {
        when(ordenRepository.findByPedidoId(7L)).thenReturn(Optional.of(orden));

        OrdenService.Resultado resultado =
                ordenService.aplicar(evento(EventoOrden.TIPO_PAGO_RECHAZADO, 7L));

        assertEquals(OrdenService.Resultado.ACTUALIZADA, resultado);
        assertEquals(EstadoOrden.CANCELADA, orden.getEstado());
        verify(ordenRepository).save(orden);
    }

    @Test
    void aplicar_noDeberiaCancelarUnaOrdenYaAnulada() {
        orden.setEstado(EstadoOrden.ANULADA);
        when(ordenRepository.findByPedidoId(7L)).thenReturn(Optional.of(orden));

        OrdenService.Resultado resultado =
                ordenService.aplicar(evento(EventoOrden.TIPO_PAGO_RECHAZADO, 7L));

        assertEquals(OrdenService.Resultado.REPETIDA, resultado);
        assertEquals(EstadoOrden.ANULADA, orden.getEstado());
        verify(ordenRepository, never()).save(any());
    }

    // ------------------------------------------------ estado logistico

    @Test
    void aplicar_deberiaGuardarElUltimoEstadoLogistico() {
        when(ordenRepository.findByPedidoId(7L)).thenReturn(Optional.of(orden));

        OrdenService.Resultado resultado = ordenService.aplicar(
                new EventoOrden(EventoOrden.TIPO_CAMBIO_ESTADO, 7L, "usuario-42",
                        "EN_PREPARACION", null, null, null, null));

        assertEquals(OrdenService.Resultado.ACTUALIZADA, resultado);
        assertEquals("EN_PREPARACION", orden.getEstadoPedido());
        verify(ordenRepository).save(orden);
    }

    @Test
    void aplicar_noDeberiaRepetirLaTransicion_siElEstadoEsElMismo() {
        orden.setEstadoPedido("EN_PREPARACION");
        when(ordenRepository.findByPedidoId(7L)).thenReturn(Optional.of(orden));

        OrdenService.Resultado resultado = ordenService.aplicar(
                new EventoOrden(EventoOrden.TIPO_CAMBIO_ESTADO, 7L, "usuario-42",
                        "EN_PREPARACION", null, null, null, null));

        assertEquals(OrdenService.Resultado.REPETIDA, resultado);
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void aplicar_deberiaDescartarElEvento_siElPedidoNoTieneOrden() {
        when(ordenRepository.findByPedidoId(7L)).thenReturn(Optional.empty());

        OrdenService.Resultado resultado =
                ordenService.aplicar(evento(EventoOrden.TIPO_PAGO_APROBADO, 7L));

        assertEquals(OrdenService.Resultado.IGNORADA, resultado);
        verify(ordenRepository, never()).save(any());
    }

    // ------------------------------------------------------ eventos invalidos

    @Test
    void aplicar_deberiaDescartarUnEventoAjenoAlDominio() {
        EventoOrden ajeno = new EventoOrden("OTRO_TIPO", 7L, "usuario-42",
                null, null, null, null, List.of(item(1L, 1)));

        OrdenService.Resultado resultado = ordenService.aplicar(ajeno);

        assertEquals(OrdenService.Resultado.IGNORADA, resultado);
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void aplicar_deberiaRechazarElEvento_siNoTieneItems() {
        EventoOrden sinItems = new EventoOrden("PEDIDO_REGISTRADO", 7L, "usuario-42",
                null, null, 100.0, null, List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ordenService.aplicar(sinItems));

        assertTrue(error.getMessage().contains("items"));
    }

    @Test
    void aplicar_deberiaRechazarElEvento_siNoTieneUsuarioId() {
        EventoOrden sinUsuario = new EventoOrden("PEDIDO_REGISTRADO", 7L, null,
                null, null, 100.0, null, List.of(item(1L, 1)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ordenService.aplicar(sinUsuario));

        assertTrue(error.getMessage().contains("usuarioId"));
    }

    @Test
    void aplicar_deberiaRechazarElEvento_siNoTieneTotal() {
        EventoOrden sinTotal = new EventoOrden("PEDIDO_REGISTRADO", 7L, "usuario-42",
                null, null, null, null, List.of(item(1L, 1)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ordenService.aplicar(sinTotal));

        assertTrue(error.getMessage().contains("total"));
    }

    @Test
    void aplicar_deberiaRechazarElEvento_siNoTienePedidoId() {
        EventoOrden sinPedido = new EventoOrden("PEDIDO_REGISTRADO", null, "usuario-42",
                null, null, 100.0, null, List.of(item(1L, 1)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ordenService.aplicar(sinPedido));

        assertTrue(error.getMessage().contains("pedidoId"));
    }

    @Test
    void aplicar_deberiaRegistrarLaOrdenAunSiLaFechaEsIlegible() {
        when(ordenRepository.existsByPedidoId(7L)).thenReturn(false);

        ordenService.aplicar(new EventoOrden(EventoOrden.TIPO_PEDIDO_REGISTRADO, 7L, "usuario-42",
                null, null, 100.0, "ayer", List.of(item(1L, 1))));

        ArgumentCaptor<Orden> guardada = ArgumentCaptor.forClass(Orden.class);
        verify(ordenRepository).save(guardada.capture());
        assertNull(guardada.getValue().getFechaPedido());
    }

    // ------------------------------------------------------------ API

    @Test
    void obtener_deberiaDevolverLaOrden_siEsDelUsuario() {
        when(ordenRepository.findById(1L)).thenReturn(Optional.of(orden));

        Orden resultado = ordenService.obtener("usuario-42", 1L);

        assertEquals(7L, resultado.getPedidoId());
    }

    @Test
    void obtener_deberiaRechazar_siLaOrdenEsDeOtroUsuario() {
        when(ordenRepository.findById(1L)).thenReturn(Optional.of(orden));

        assertThrows(AccessDeniedException.class, () -> ordenService.obtener("otro-usuario", 1L));
    }

    @Test
    void obtener_deberiaLanzar404_siLaOrdenNoExiste() {
        when(ordenRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> ordenService.obtener("usuario-42", 99L));
    }

    @Test
    void cambiarEstado_deberiaAnularLaOrden_registrada() {
        when(ordenRepository.findById(1L)).thenReturn(Optional.of(orden));
        when(ordenRepository.save(orden)).thenReturn(orden);

        Orden resultado = ordenService.cambiarEstado("usuario-42", 1L, EstadoOrden.ANULADA);

        assertEquals(EstadoOrden.ANULADA, resultado.getEstado());
    }

    @Test
    void cambiarEstado_deberiaRechazarUnaTransicionNoPermitida() {
        orden.setEstado(EstadoOrden.CANCELADA);
        when(ordenRepository.findById(1L)).thenReturn(Optional.of(orden));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ordenService.cambiarEstado("usuario-42", 1L, EstadoOrden.PAGADA));

        assertTrue(error.getMessage().contains("no permitida"));
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void cambiarEstado_deberiaRechazarUnEstadoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> ordenService.cambiarEstado("usuario-42", 1L, null));
    }
}
