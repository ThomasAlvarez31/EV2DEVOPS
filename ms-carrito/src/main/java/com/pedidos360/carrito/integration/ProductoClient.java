package com.pedidos360.carrito.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente HTTP hacia ms-productos.
 *
 * Se usa para NO confiar en los precios que envia el frontend: al agregar un
 * producto al carrito y al confirmar la compra se toma el precio y el stock
 * que tiene el microservicio de catalogo, que es la fuente de verdad.
 *
 * Configuracion: app.productos.url (variable de entorno PRODUCTOS_SERVICE_URL).
 *   local:  http://localhost:8081/productos
 *   docker: http://ms-productos:8081/productos
 */
@Component
public class ProductoClient {

    private static final Logger log = LoggerFactory.getLogger(ProductoClient.class);

    private final RestTemplate rest;
    private final String baseUrl;

    public ProductoClient(@Value("${app.productos.url}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.rest = new RestTemplate(factory);
    }

    /**
     * Devuelve el producto si ms-productos responde.
     *
     * @throws java.util.NoSuchElementException si el producto no existe (404).
     * @throws IllegalStateException si ms-productos no responde (caido o sin red).
     */
    public ProductoInfo buscarProducto(Long productoId) {
        if (productoId == null) {
            throw new IllegalArgumentException("Debe indicar un producto");
        }
        try {
            ProductoInfo producto = rest.getForObject(baseUrl + "/" + productoId, ProductoInfo.class);
            if (producto == null) {
                throw new java.util.NoSuchElementException("Producto no encontrado en ms-productos: " + productoId);
            }
            return producto;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new java.util.NoSuchElementException("Producto no encontrado en ms-productos: " + productoId);
        } catch (RestClientException e) {
            log.error("ms-productos no responde ({}): {}", baseUrl, e.getMessage());
            throw new IllegalStateException(
                    "No se pudo verificar el producto en ms-productos. Intenta nuevamente.", e);
        }
    }

    /** Subconjunto de campos de ms-productos que necesita el carrito. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductoInfo(Long id, String nombre, Double precio, Integer stock, String imagenUrl) {

        public boolean conStockDisponible(int cantidadDeseada) {
            return stock == null || stock >= cantidadDeseada;
        }
    }
}
