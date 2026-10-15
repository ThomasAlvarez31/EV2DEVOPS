package com.pedidos360.pagos.service;

import com.pedidos360.pagos.dto.SolicitudPago;
import com.pedidos360.pagos.model.EstadoPago;
import com.pedidos360.pagos.model.MetodoPago;
import com.pedidos360.pagos.model.Pago;
import com.pedidos360.pagos.repository.PagoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    private static final String USUARIO = "usuario-1";

    @Mock
    private PagoRepository pagoRepository;

    @InjectMocks
    private PagoService pagoService;

    private SolicitudPago solicitud(Long pedidoId, Double monto, boolean rechazo) {
        return new SolicitudPago(pedidoId, USUARIO, monto, "TARJETA_CREDITO", rechazo);
    }

    @Test
    void registrar_deberiaAprobarElPago_porDefecto() {
        when(pagoRepository.existsByPedidoId(1L)).thenReturn(false);
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        Pago pago = pagoService.registrar(solicitud(1L, 19990.0, false));

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals(MetodoPago.TARJETA_CREDITO, pago.getMetodoPago());
        assertEquals(19990.0, pago.getMonto());
        assertNull(pago.getMotivo());

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getPedidoId());
        assertEquals(USUARIO, captor.getValue().getUsuarioId());
    }

    @Test
    void registrar_deberiaRechazarYGuardarElMotivo_siSeSimulaRechazo() {
        when(pagoRepository.existsByPedidoId(2L)).thenReturn(false);
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        Pago pago = pagoService.registrar(solicitud(2L, 5000.0, true));

        assertEquals(EstadoPago.RECHAZADO, pago.getEstado());
        assertNotNull(pago.getMotivo());
        assertTrue(pago.getMotivo().contains("rechazado"));
    }

    @Test
    void registrar_deberiaImpedirPagosDuplicados() {
        Pago existente = new Pago(3L, USUARIO, 100.0, MetodoPago.TARJETA_DEBITO,
                EstadoPago.APROBADO, null);
        when(pagoRepository.existsByPedidoId(3L)).thenReturn(true);
        when(pagoRepository.findByPedidoId(3L)).thenReturn(Optional.of(existente));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> pagoService.registrar(solicitud(3L, 100.0, false)));

        assertTrue(error.getMessage().contains("ya tiene un pago procesado"));
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void registrar_deberiaValidarElMonto() {
        IllegalArgumentException sinMonto = assertThrows(IllegalArgumentException.class,
                () -> pagoService.registrar(solicitud(4L, null, false)));
        assertTrue(sinMonto.getMessage().contains("monto"));

        IllegalArgumentException montoCero = assertThrows(IllegalArgumentException.class,
                () -> pagoService.registrar(solicitud(4L, 0.0, false)));
        assertTrue(montoCero.getMessage().contains("monto"));
    }

    @Test
    void registrar_deberiaValidarElPedido() {
        assertThrows(IllegalArgumentException.class,
                () -> pagoService.registrar(solicitud(null, 100.0, false)));
        assertThrows(IllegalArgumentException.class, () -> pagoService.registrar(null));
    }

    @Test
    void registrar_deberiaValidarElMetodoDePago() {
        when(pagoRepository.existsByPedidoId(5L)).thenReturn(false);

        SolicitudPago invalido = new SolicitudPago(5L, USUARIO, 100.0, "BITCOIN", false);
        assertThrows(IllegalArgumentException.class, () -> pagoService.registrar(invalido));
    }

    @Test
    void obtenerPorPedido_deberiaDevolverElPagoRegistrado() {
        Pago guardado = new Pago(6L, USUARIO, 2500.0, MetodoPago.TRANSFERENCIA,
                EstadoPago.APROBADO, null);
        when(pagoRepository.findByPedidoId(6L)).thenReturn(Optional.of(guardado));

        Pago pago = pagoService.obtenerPorPedido(6L);

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals(MetodoPago.TRANSFERENCIA, pago.getMetodoPago());
    }

    @Test
    void obtenerPorPedido_deberiaLanzarExcepcion_siNoExiste() {
        when(pagoRepository.findByPedidoId(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> pagoService.obtenerPorPedido(99L));
    }
}
