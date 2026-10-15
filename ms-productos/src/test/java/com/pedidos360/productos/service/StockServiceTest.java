package com.pedidos360.productos.service;

import com.pedidos360.productos.messaging.EventoStock;
import com.pedidos360.productos.model.EventoStockProcesado;
import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.repository.EventoStockProcesadoRepository;
import com.pedidos360.productos.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del descuento y reposicion ASINCRONA de stock.
 *
 * Cubre las reglas del enunciado: al registrar el pedido se descuenta, al
 * rechazar el pago se repone, el stock nunca queda negativo y un evento
 * repetido NO se aplica dos veces (idempotencia).
 */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private EventoStockProcesadoRepository eventoProcesadoRepository;

    @InjectMocks
    private StockService stockService;

    private Producto monitor;

    @BeforeEach
    void setUp() {
        monitor = new Producto("Monitor 27\"", "Monitor IPS", 249.990, 10, "Tecnologia");
        monitor.setId(1L);
    }

    private static EventoStock evento(String tipo, Long pedidoId, EventoStock.ItemStock... items) {
        return new EventoStock(tipo, pedidoId, "usuario-42", "PENDIENTE_PAGO", 100.0, List.of(items));
    }

    private static EventoStock.ItemStock item(Long productoId, int cantidad) {
        return new EventoStock.ItemStock(productoId, "Monitor 27\"", cantidad, 249.990);
    }

    // ---------------------------------------------------- pedido registrado

    @Test
    void aplicar_deberiaDescontarElStock_alRegistrarElPedido() {
        when(eventoProcesadoRepository.existsByTipoAndPedidoId("PEDIDO_REGISTRADO", 7L)).thenReturn(false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(monitor));

        StockService.Resultado resultado =
                stockService.aplicar(evento(EventoStock.TIPO_PEDIDO_REGISTRADO, 7L, item(1L, 3)));

        assertEquals(StockService.Resultado.APLICADO, resultado);
        assertEquals(7, monitor.getStock());
        verify(productoRepository).save(monitor);

        ArgumentCaptor<EventoStockProcesado> marca = ArgumentCaptor.forClass(EventoStockProcesado.class);
        verify(eventoProcesadoRepository).save(marca.capture());
        assertEquals("PEDIDO_REGISTRADO", marca.getValue().getTipo());
        assertEquals(7L, marca.getValue().getPedidoId());
    }

    @Test
    void aplicar_deberiaDescontarVariosItems_delMismoEvento() {
        Producto teclado = new Producto("Teclado", "Mecanico", 50.0, 4, "Tecnologia");
        teclado.setId(2L);
        when(eventoProcesadoRepository.existsByTipoAndPedidoId("PEDIDO_REGISTRADO", 8L)).thenReturn(false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(monitor));
        when(productoRepository.findById(2L)).thenReturn(Optional.of(teclado));

        stockService.aplicar(evento(EventoStock.TIPO_PEDIDO_REGISTRADO, 8L, item(1L, 2), item(2L, 4)));

        assertEquals(8, monitor.getStock());
        assertEquals(0, teclado.getStock());
    }

    @Test
    void aplicar_nuncaDeberiaDejarElStockEnNegativo() {
        when(eventoProcesadoRepository.existsByTipoAndPedidoId("PEDIDO_REGISTRADO", 9L)).thenReturn(false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(monitor));

        stockService.aplicar(evento(EventoStock.TIPO_PEDIDO_REGISTRADO, 9L, item(1L, 99)));

        assertEquals(0, monitor.getStock());
    }

    // ------------------------------------------------------- pago rechazado

    @Test
    void aplicar_deberiaReponerElStock_alRechazarElPago() {
        monitor.setStock(4);
        when(eventoProcesadoRepository.existsByTipoAndPedidoId("PAGO_RECHAZADO", 7L)).thenReturn(false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(monitor));

        StockService.Resultado resultado =
                stockService.aplicar(evento(EventoStock.TIPO_PAGO_RECHAZADO, 7L, item(1L, 3)));

        assertEquals(StockService.Resultado.APLICADO, resultado);
        assertEquals(7, monitor.getStock());
        verify(productoRepository).save(monitor);
    }

    // ---------------------------------------------------------- idempotencia

    @Test
    void aplicar_noDeberiaDescontarDosVeces_siElEventoSeRepite() {
        when(eventoProcesadoRepository.existsByTipoAndPedidoId("PEDIDO_REGISTRADO", 7L)).thenReturn(true);

        StockService.Resultado resultado =
                stockService.aplicar(evento(EventoStock.TIPO_PEDIDO_REGISTRADO, 7L, item(1L, 3)));

        assertEquals(StockService.Resultado.REPETIDO, resultado);
        assertEquals(10, monitor.getStock());
        verify(productoRepository, never()).save(any());
        verify(eventoProcesadoRepository, never()).save(any());
    }

    // ---------------------------------------------------- eventos ajenos

    @Test
    void aplicar_deberiaDescartarUnEventoQueNoCorrespondeAlInventario() {
        StockService.Resultado resultado =
                stockService.aplicar(evento("PAGO_APROBADO", 7L, item(1L, 3)));

        assertEquals(StockService.Resultado.IGNORADO, resultado);
        verify(productoRepository, never()).save(any());
        verify(eventoProcesadoRepository, never()).save(any());
    }

    @Test
    void aplicar_deberiaSeguirConElResto_siElProductoYaNoExiste() {
        when(eventoProcesadoRepository.existsByTipoAndPedidoId("PEDIDO_REGISTRADO", 11L)).thenReturn(false);
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());
        when(productoRepository.findById(1L)).thenReturn(Optional.of(monitor));

        StockService.Resultado resultado = stockService.aplicar(
                evento(EventoStock.TIPO_PEDIDO_REGISTRADO, 11L,
                        new EventoStock.ItemStock(99L, "Borrado", 1, 10.0),
                        item(1L, 2)));

        assertEquals(StockService.Resultado.APLICADO, resultado);
        assertEquals(8, monitor.getStock());
        verify(productoRepository).save(monitor);
    }

    @Test
    void aplicar_deberiaIgnorarLasLineasConCantidadInvalida() {
        when(eventoProcesadoRepository.existsByTipoAndPedidoId("PEDIDO_REGISTRADO", 12L)).thenReturn(false);

        StockService.Resultado resultado = stockService.aplicar(evento(EventoStock.TIPO_PEDIDO_REGISTRADO, 12L,
                new EventoStock.ItemStock(1L, "Monitor", 0, 10.0),
                new EventoStock.ItemStock(2L, "Teclado", null, 10.0)));

        assertEquals(StockService.Resultado.APLICADO, resultado);
        assertEquals(10, monitor.getStock());
        verify(productoRepository, never()).save(any());
    }

    // ---------------------------------------------------- eventos invalidos

    @Test
    void aplicar_deberiaRechazarElEvento_siNoTieneItems() {
        EventoStock sinItems = new EventoStock("PEDIDO_REGISTRADO", 7L, "u", "PENDIENTE_PAGO", 10.0, List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> stockService.aplicar(sinItems));

        assertTrue(error.getMessage().contains("items"));
        verify(productoRepository, never()).save(any());
    }

    @Test
    void aplicar_deberiaRechazarElEvento_siNoTienePedidoId() {
        EventoStock sinPedido = new EventoStock("PEDIDO_REGISTRADO", null, "u", null, null, List.of(item(1L, 1)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> stockService.aplicar(sinPedido));

        assertTrue(error.getMessage().contains("pedidoId"));
    }

    @Test
    void aplicar_deberiaRechazarElEvento_siNoTieneTipo() {
        EventoStock sinTipo = new EventoStock(null, 7L, "u", null, null, List.of(item(1L, 1)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> stockService.aplicar(sinTipo));

        assertTrue(error.getMessage().contains("tipo"));
    }
}
