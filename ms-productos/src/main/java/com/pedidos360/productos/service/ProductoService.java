package com.pedidos360.productos.service;

import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    /**
     * Busqueda combinada: acepta nombre, categoria, o ambos a la vez.
     * Si no se envia ningun filtro se devuelve el catalogo completo.
     *
     * El filtro se aplica en memoria con texto normalizado (sin acentos y en
     * minusculas) para que "tecnologia" encuentre "Tecnologia" y "inalambrico"
     * encuentre "inalambrico". El catalogo es pequeño y esta en H2 en memoria,
     * por lo que es más simple y portable que traducir la normalizacion a SQL.
     */
    public List<Producto> buscar(String nombre, String categoria) {
        boolean hayNombre = nombre != null && !nombre.isBlank();
        boolean hayCategoria = categoria != null && !categoria.isBlank();

        List<Producto> base = hayCategoria ? buscarPorCategoria(categoria) : listarTodos();
        if (!hayNombre) {
            return base;
        }

        String buscado = normalizar(nombre);
        return base.stream()
                .filter(p -> normalizar(p.getNombre()).contains(buscado))
                .collect(Collectors.toList());
    }

    public List<Producto> buscarPorCategoria(String categoria) {
        String buscada = normalizar(categoria);
        return listarTodos().stream()
                .filter(p -> normalizar(p.getCategoria()).equals(buscada))
                .collect(Collectors.toList());
    }

    public List<Producto> buscarPorNombre(String nombre) {
        String buscado = normalizar(nombre);
        return listarTodos().stream()
                .filter(p -> normalizar(p.getNombre()).contains(buscado))
                .collect(Collectors.toList());
    }

    /** Minusculas + sin acentos, para comparar textos del catalogo. */
    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Categorias distintas existentes, para los filtros del frontend. */
    public List<String> listarCategorias() {
        return productoRepository.findCategorias();
    }

    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + id));
    }

    public Producto crear(Producto producto) {
        return productoRepository.save(producto);
    }

    public Producto actualizar(Long id, Producto datos) {
        Producto existente = obtenerPorId(id);
        existente.setNombre(datos.getNombre());
        existente.setDescripcion(datos.getDescripcion());
        existente.setPrecio(datos.getPrecio());
        existente.setStock(datos.getStock());
        existente.setCategoria(datos.getCategoria());
        existente.setImagenUrl(datos.getImagenUrl());
        return productoRepository.save(existente);
    }

    public void eliminar(Long id) {
        if (!productoRepository.existsById(id)) {
            throw new NoSuchElementException("Producto no encontrado: " + id);
        }
        productoRepository.deleteById(id);
    }
}
