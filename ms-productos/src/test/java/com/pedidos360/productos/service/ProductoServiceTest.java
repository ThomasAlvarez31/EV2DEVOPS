package com.pedidos360.productos.service;

import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private ProductoService productoService;

    @Test
    void obtenerPorId_deberiaRetornarProducto_siExiste() {
        Producto producto = new Producto("Pizza", "Pizza muzzarella", 9990.0, 10, "Comida");
        producto.setId(1L);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        Producto resultado = productoService.obtenerPorId(1L);

        assertEquals("Pizza", resultado.getNombre());
    }

    @Test
    void obtenerPorId_deberiaLanzarExcepcion_siNoExiste() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> productoService.obtenerPorId(99L));
    }

    @Test
    void crear_deberiaGuardarYRetornarProducto() {
        Producto producto = new Producto("Bebida", "Bebida 500ml", 1500.0, 50, "Bebidas");
        when(productoRepository.save(producto)).thenReturn(producto);

        Producto resultado = productoService.crear(producto);

        assertEquals("Bebida", resultado.getNombre());
        verify(productoRepository, times(1)).save(producto);
    }

    @Test
    void buscar_sinFiltros_deberiaRetornarTodoElCatalogo() {
        when(productoRepository.findAll()).thenReturn(java.util.List.of());

        assertTrue(productoService.buscar(null, null).isEmpty());
        assertTrue(productoService.buscar("  ", "   ").isEmpty());
    }

    @Test
    void buscar_conNombreYCategoria_deberiaFiltrarPorAmbosCampos() {
        Producto mouse = new Producto("Mouse Logitech", "Inalambrico", 14990.0, 5, "Tecnologia");
        Producto pelota = new Producto("Pelota futbol", "Cuero", 12990.0, 8, "Deportes");
        when(productoRepository.findAll()).thenReturn(java.util.List.of(mouse, pelota));

        java.util.List<Producto> resultado = productoService.buscar("mouse", "Tecnologia");

        assertEquals(1, resultado.size());
        assertEquals("Mouse Logitech", resultado.get(0).getNombre());
    }

    @Test
    void buscarSoloPorCategoria_deberiaFiltrarElCatalogo() {
        Producto notebook = new Producto("Notebook", "14 pulgadas", 799990.0, 3, "Tecnología");
        Producto sillon = new Producto("Sillón", "Reclinable", 149990.0, 2, "Hogar");
        when(productoRepository.findAll()).thenReturn(java.util.List.of(notebook, sillon));

        java.util.List<Producto> resultado = productoService.buscar(null, "tecnologia");

        assertEquals(1, resultado.size());
        assertEquals("Notebook", resultado.get(0).getNombre());
    }

    @Test
    void buscarPorNombre_deberiaIgnorarTildesMayusculas() {
        Producto mouse = new Producto("Mouse inalambrico", "2.4 GHz", 14990.0, 5, "Tecnología");
        when(productoRepository.findAll()).thenReturn(java.util.List.of(mouse));

        assertFalse(productoService.buscarPorNombre("INALAMBRICO").isEmpty());
        assertTrue(productoService.buscarPorNombre("telefono").isEmpty());
    }
}
