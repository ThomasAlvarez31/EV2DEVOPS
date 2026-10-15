package com.pedidos360.productos.config;

import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Siembra inicial del catalogo: si la base esta vacia la carga con productos
 * de ejemplo para poder probar el flujo completo catalogo -> carrito ->
 * checkout -> pago -> email.
 *
 * Se ejecuta solo cuando no hay datos (count() == 0), por lo que sirve igual
 * en local (H2 en memoria) que en el primer arranque de un despliegue, donde
 * con AUTH_BYPASS=false la BD arranca vacia y si no habria catalogo.
 *
 * Desactivable con APP_CATALOGO_SEED=false (por ejemplo si se apunta a una BD
 * con productos reales).
 */
@Component
@ConditionalOnProperty(name = "app.catalogo.seed", havingValue = "true", matchIfMissing = true)
public class DatosDeEjemplo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDeEjemplo.class);

    private final ProductoRepository productoRepository;

    public DatosDeEjemplo(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productoRepository.count() > 0) {
            return; // ya hay datos (p. ej. reinicio del contenedor con volumes)
        }

        productoRepository.saveAll(List.of(
                new Producto("Laptop Lenovo IdeaPad 3",
                        "Laptop 15\" con Ryzen 5, 8 GB de RAM y SSD de 512 GB. Ideal para estudio y trabajo.",
                        449990.0, 10, "Tecnología",
                        "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=600&q=80"),
                new Producto("Mouse Logitech M170 inalámbrico",
                        "Mouse inalámbrico 2.4 GHz con receptor USB y hasta 10 meses de batería.",
                        14990.0, 25, "Tecnología",
                        "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=600&q=80"),
                new Producto("Teclado mecánico Redragon Kumara",
                        "Teclado mecánico retroiluminado, switches rojos, diseño compacto.",
                        34990.0, 15, "Tecnología",
                        "https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=600&q=80"),
                new Producto("Audífonos JBL Tune 520BT",
                        "Audífonos inalámbricos con Bluetooth 5.3 y hasta 57 horas de reproducción.",
                        49990.0, 18, "Tecnología",
                        "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&q=80"),
                new Producto("Bicicleta urbana Explorer 21 velocidades",
                        "Bicicleta con marco de acero, frenos de disco y 21 velocidades.",
                        189990.0, 5, "Deportes",
                        "https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=600&q=80"),
                new Producto("Balón de fútbol Adidas Starlancer",
                        "Balón tamaño oficial, costuras cosidas para mayor durabilidad.",
                        19990.0, 20, "Deportes",
                        "https://images.unsplash.com/photo-1614632537190-23e4146777db?w=600&q=80"),
                new Producto("Cafetera italiana 6 tazas",
                        "Cafetera moka de aluminio, compatible con todo tipo de cocinas.",
                        24990.0, 12, "Hogar",
                        "https://images.unsplash.com/photo-1517668808822-9ebb02f2a0e6?w=600&q=80"),
                new Producto("Sartén antiadherente 28 cm",
                        "Sartén con recubrimiento antiadherente y mango ergonómico.",
                        12990.0, 30, "Hogar",
                        "https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=600&q=80")
        ));

        log.info("Cargados 8 productos de ejemplo (solo prueba local, AUTH_BYPASS=true)");
    }
}
