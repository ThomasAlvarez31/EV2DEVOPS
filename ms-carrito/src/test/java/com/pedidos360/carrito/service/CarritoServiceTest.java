package com.pedidos360.carrito.service;

import com.pedidos360.carrito.dto.AgregarItemRequest;
import com.pedidos360.carrito.integration.PagoClient;
import com.pedidos360.carrito.integration.ProductoClient;
import com.pedidos360.carrito.model.*;
import com.pedidos360.carrito.repository.CarritoRepository;
import com.pedidos360.carrito.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    private static final String USUARIO = "user-1";

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProductoClient productoClient;

    @Mock
    private PagoClient pagoClient;

    @InjectMocks
    private CarritoService carritoService;

    private ProductoClient.ProductoInfo producto(double precio, int stock) {
        return new ProductoClient.ProductoInfo(10L, "Pizza", precio, stock, null);
    }

    // ------------------------------------------------------------- agregarItem

    @Test
    void agregarItem_deberiaUsarElPrecioDelCatalogoYNoElDelFrontend() {
        when(carritoRepository.findByUsuarioIdAndEstado(USUARIO, EstadoCarrito.ACTIVO))
                .thenReturn(Optional.empty());
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productoClient.buscarProducto(10L)).thenReturn(producto(9990.0, 10));

        // El navegador manda precio=1.0 a proposito: no debe usarlo.
        AgregarItemRequest request = new AgregarItemRequest(10L, "Nombre falso", 1.0, 2);
        Carrito resultado = carritoService.agregarItem(USUARIO, request);

        assertEquals(1, resultado.getItems().size());
        ItemCarrito item = resultado.getItems().get(0);
        assertEquals("Pizza", item.getNombreProducto());
        assertEquals(9990.0, item.getPrecioUnitario());
        assertEquals(2, item.getCantidad());
    }

    @Test
    void agregarItem_deberiaLanzarExcepcion_siCantidadEsInvalida() {
        AgregarItemRequest request = new AgregarItemRequest(10L, "Pizza", 9990.0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> carritoService.agregarItem(USUARIO, request));
        verifyNoInteractions(productoClient);
    }

    @Test
    void agregarItem_deberiaAcumularCantidad_siElProductoYaEstaEnElCarrito() {
        Carrito carrito = new Carrito(USUARIO);
        carrito.agregarItem(new ItemCarrito(10L, "Pizza", 9990.0, 1));
        when(carritoRepository.findByUsuarioIdAndEstado(USUARIO, EstadoCarrito.ACTIVO))
                .thenReturn(Optional.of(carrito));
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productoClient.buscarProducto(10L)).thenReturn(producto(9990.0, 10));

        Carrito resultado = carritoService.agregarItem(USUARIO, new AgregarItemRequest(10L, "x", 1.0, 2));

        assertEquals(1, resultado.getItems().size());
        assertEquals(3, resultado.getItems().get(0).getCantidad());
    }

    @Test
    void agregarItem_deberiaRechazar_siNoHayStockSuficiente() {
        when(productoClient.buscarProducto(10L)).thenReturn(producto(9990.0, 1));
        when(carritoRepository.findByUsuarioIdAndEstado(USUARIO, EstadoCarrito.ACTIVO))
                .thenReturn(Optional.of(new Carrito(USUARIO)));

        AgregarItemRequest request = new AgregarItemRequest(10L, "Pizza", 9990.0, 5);

        assertThrows(IllegalArgumentException.class, () -> carritoService.agregarItem(USUARIO, request));
        verify(carritoRepository, never()).save(any(Carrito.class));
    }

    @Test
    void agregarItem_deberiaPropagarElError_siMsProductosNoResponde() {
        when(productoClient.buscarProducto(10L))
                .thenThrow(new IllegalStateException("No se pudo verificar el producto en ms-productos."));

        assertThrows(IllegalStateException.class,
                () -> carritoService.agregarItem(USUARIO, new AgregarItemRequest(10L, "x", 1.0, 1)));
    }

    // ------------------------------------------------------- actualizarCantidad

    @Test
    void actualizarCantidad_deberiaActualizarElItem_siStockAlcanza() {
        Carrito carrito = new Carrito(USUARIO);
        ItemCarrito item = new ItemCarrito(10L, "Pizza", 9990.0, 1);
        carrito.agregarItem(item);
        item.setId(77L);

        when(carritoRepository.findByUsuarioIdAndEstado(USUARIO, EstadoCarrito.ACTIVO))
                .thenReturn(Optional.of(carrito));
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productoClient.buscarProducto(10L)).thenReturn(producto(9990.0, 10));

        Carrito resultado = carritoService.actualizarCantidad(USUARIO, 77L, 4);

        assertEquals(4, resultado.getItems().get(0).getCantidad());
    }

    @Test
    void actualizarCantidad_deberiaLanzarExcepcion_siCantidadEsInvalida() {
        assertThrows(IllegalArgumentException.class,
                () -> carritoService.actualizarCantidad(USUARIO, 77L, 0));
    }

    // ---------------------------------------------------------------- checkout

    @Test
    void checkout_deberiaLanzarExcepcion_siCarritoEstaVacio() {
        when(carritoRepository.findByUsuarioIdAndEstado(USUARIO, EstadoCarrito.ACTIVO))
                .thenReturn(Optional.of(new Carrito(USUARIO)));

        assertThrows(IllegalStateException.class,
                () -> carritoService.checkout(USUARIO, MetodoPago.TARJETA_CREDITO));
    }

    @Test
    void checkout_deberiaLanzarExcepcion_siNoHayMetodoDePago() {
        assertThrows(IllegalArgumentException.class, () -> carritoService.checkout(USUARIO, null));
    }

    @Test
    void checkout_deberiaCrearPedidoPendienteDePagoConTotalCorrecto() {
        Carrito carrito = new Carrito(USUARIO);
        carrito.agregarItem(new ItemCarrito(1L, "Hamburguesa", 5000.0, 2));
        when(carritoRepository.findByUsuarioIdAndEstado(USUARIO, EstadoCarrito.ACTIVO))
                .thenReturn(Optional.of(carrito));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido pedido = carritoService.checkout(USUARIO, MetodoPago.TARJETA_DEBITO);

        assertEquals(10000.0, pedido.getTotal());
        assertEquals(EstadoPedido.PENDIENTE_PAGO, pedido.getEstado());
        assertEquals(EstadoPago.PENDIENTE, pedido.getEstadoPago());
        assertEquals(MetodoPago.TARJETA_DEBITO, pedido.getMetodoPago());
        assertEquals(EstadoCarrito.FINALIZADO, carrito.getEstado());
    }

    // ------------------------------------------------------------ pago simulado

    @Test
    void procesarPago_deberiaMarcarPedidoComoPagado_siMsPagosAprueba() {
        Pedido pedido = pedidoPendiente(5L);
        when(pedidoRepository.findById(5L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoClient.registrarPago(any(), eq(5L), eq(USUARIO), any(), anyString(), eq(false)))
                .thenReturn(new PagoClient.ResultadoPago(1L, 5L, "APROBADO", null));

        Pedido resultado = carritoService.procesarPago(USUARIO, 5L, false, "token-azure");

        assertEquals(EstadoPago.APROBADO, resultado.getEstadoPago());
        assertEquals(EstadoPedido.CONFIRMADO, resultado.getEstado());
        verify(pagoClient).registrarPago(any(), eq(5L), eq(USUARIO), any(), anyString(), eq(false));
    }

    @Test
    void procesarPago_deberiaCancelarYRestaurarCarrito_siMsPagosRechaza() {
        Pedido pedido = pedidoPendiente(6L);
        when(pedidoRepository.findById(6L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(carritoRepository.findByUsuarioIdAndEstado(USUARIO, EstadoCarrito.ACTIVO))
                .thenReturn(Optional.empty());
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoClient.registrarPago(any(), eq(6L), eq(USUARIO), any(), anyString(), eq(true)))
                .thenReturn(new PagoClient.ResultadoPago(2L, 6L, "RECHAZADO", "Pago rechazado (simulación)"));

        Pedido resultado = carritoService.procesarPago(USUARIO, 6L, true, "token-azure");

        assertEquals(EstadoPago.RECHAZADO, resultado.getEstadoPago());
        assertEquals(EstadoPedido.CANCELADO, resultado.getEstado());

        ArgumentCaptor<Carrito> captor = ArgumentCaptor.forClass(Carrito.class);
        verify(carritoRepository, atLeastOnce()).save(captor.capture());
        Carrito restaurado = captor.getValue();
        assertEquals(1, restaurado.getItems().size());
        assertEquals("Hamburguesa", restaurado.getItems().get(0).getNombreProducto());
        assertEquals(EstadoCarrito.ACTIVO, restaurado.getEstado());
    }

    @Test
    void procesarPago_deberiaImpedirPagosDuplicadosSinLlamarAMsPagos() {
        Pedido pedido = pedidoPendiente(7L);
        pedido.setEstadoPago(EstadoPago.APROBADO);
        when(pedidoRepository.findById(7L)).thenReturn(Optional.of(pedido));

        assertThrows(IllegalStateException.class,
                () -> carritoService.procesarPago(USUARIO, 7L, false, "token-azure"));
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verifyNoInteractions(pagoClient);
    }

    @Test
    void procesarPago_deberiaPropagarElError_siMsPagosNoResponde() {
        Pedido pedido = pedidoPendiente(9L);
        when(pedidoRepository.findById(9L)).thenReturn(Optional.of(pedido));
        when(pagoClient.registrarPago(any(), eq(9L), any(), any(), anyString(), eq(false)))
                .thenThrow(new IllegalStateException("No se pudo procesar el pago en ms-pagos."));

        assertThrows(IllegalStateException.class,
                () -> carritoService.procesarPago(USUARIO, 9L, false, "token-azure"));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    // --------------------------------------------------------------- seguridad

    @Test
    void obtenerPedido_deberiaImpedirVerPedidosDeOtroUsuario() {
        Pedido pedido = pedidoPendiente(8L);
        pedido.setUsuarioId("user-2");
        when(pedidoRepository.findById(8L)).thenReturn(Optional.of(pedido));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> carritoService.obtenerPedido(USUARIO, 8L));
    }

    @Test
    void obtenerPedido_deberiaLanzarExcepcion_siNoExiste() {
        when(pedidoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> carritoService.obtenerPedido(USUARIO, 99L));
    }

    @Test
    void obtenerPedido_deberiaDevolverElPedidoDelPropietario() {
        Pedido pedido = pedidoPendiente(9L);
        when(pedidoRepository.findById(9L)).thenReturn(Optional.of(pedido));

        assertEquals(9L, carritoService.obtenerPedido(USUARIO, 9L).getId());
    }

    // -------------------------------------------------------- actualizarEstado

    @Test
    void actualizarEstado_deberiaAvanzar_siElPedidoEstaPagado() {
        Pedido pedido = pedidoPagado(11L);
        when(pedidoRepository.findById(11L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido resultado = carritoService.actualizarEstado(USUARIO, 11L, EstadoPedido.EN_PREPARACION);

        assertEquals(EstadoPedido.EN_PREPARACION, resultado.getEstado());
    }

    @Test
    void actualizarEstado_deberiaRechazarTransicionesNoPermitidas() {
        // Un pedido pendiente de pago no puede saltar directamente a "enviado".
        Pedido pedido = pedidoPendiente(12L);
        when(pedidoRepository.findById(12L)).thenReturn(Optional.of(pedido));

        assertThrows(IllegalStateException.class,
                () -> carritoService.actualizarEstado(USUARIO, 12L, EstadoPedido.ENVIADO));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void actualizarEstado_deberiaImpedirSiNoEsElPropietario() {
        Pedido pedido = pedidoPagado(13L);
        pedido.setUsuarioId("user-2");
        when(pedidoRepository.findById(13L)).thenReturn(Optional.of(pedido));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> carritoService.actualizarEstado(USUARIO, 13L, EstadoPedido.EN_PREPARACION));
    }

    @Test
    void estadoPedido_desdeDeberiaRechazarValoresInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> EstadoPedido.desde("VOLANDO"));
        assertEquals(EstadoPedido.EN_PREPARACION, EstadoPedido.desde("en preparacion"));
    }

    private Pedido pedidoPagado(Long id) {
        Pedido pedido = pedidoPendiente(id);
        pedido.setEstado(EstadoPedido.CONFIRMADO);
        pedido.setEstadoPago(EstadoPago.APROBADO);
        return pedido;
    }

    private Pedido pedidoPendiente(Long id) {
        Pedido pedido = new Pedido(USUARIO);
        pedido.setId(id);
        pedido.setTotal(10000.0);
        pedido.setMetodoPago(MetodoPago.TARJETA_CREDITO);
        pedido.setEstado(EstadoPedido.PENDIENTE_PAGO);
        pedido.setEstadoPago(EstadoPago.PENDIENTE);
        pedido.agregarItem(new ItemPedido(1L, "Hamburguesa", 5000.0, 2));
        return pedido;
    }
}
