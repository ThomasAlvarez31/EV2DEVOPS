import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ProductoService } from '../../services/producto.service';
import { CarritoService } from '../../services/carrito.service';
import { ToastService } from '../../services/toast.service';
import { Producto } from '../../models/producto.model';
import { imagenDe, mensajeDeError } from '../../shared/utilidades';

@Component({
  selector: 'app-catalogo',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './catalogo.component.html'
})
export class CatalogoComponent implements OnInit {

  productos: Producto[] = [];
  categorias: string[] = [];
  categoriaSeleccionada: string | null = null;
  terminoBusqueda = '';
  cargando = false;
  error: string | null = null;
  agregando: number | null = null;

  constructor(
    private productoService: ProductoService,
    private carritoService: CarritoService,
    private toastService: ToastService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      this.terminoBusqueda = params.get('q') ?? '';
      this.categoriaSeleccionada = params.get('categoria');
      this.cargarProductos();
    });

    this.productoService.categorias().subscribe({
      next: (cats) => (this.categorias = cats),
      error: () => (this.categorias = [])
    });
  }

  cargarProductos(): void {
    this.cargando = true;
    this.error = null;

    this.productoService.buscar(this.terminoBusqueda, this.categoriaSeleccionada).subscribe({
      next: (data) => {
        this.productos = data;
        this.cargando = false;
      },
      error: (err) => {
        this.error = mensajeDeError(err,
          'No se pudo cargar el catálogo. Verifica que ms-productos esté disponible y que el token sea válido.');
        this.cargando = false;
      }
    });
  }

  filtrarCategoria(categoria: string | null): void {
    this.categoriaSeleccionada = categoria;
    this.sincronizarUrl();
  }

  buscar(): void {
    this.sincronizarUrl();
  }

  limpiarFiltros(): void {
    this.terminoBusqueda = '';
    this.categoriaSeleccionada = null;
    this.sincronizarUrl();
  }

  agregarAlCarrito(producto: Producto): void {
    if (!producto.id || this.agregando === producto.id) {
      return;
    }

    this.agregando = producto.id;
    this.carritoService.agregarItem({
      productoId: producto.id,
      nombreProducto: producto.nombre,
      precioUnitario: producto.precio,
      cantidad: 1
    }).subscribe({
      next: () => {
        this.toastService.exito(`"${producto.nombre}" se agregó al carrito.`);
        this.agregando = null;
      },
      error: (err) => {
        this.toastService.error(mensajeDeError(err, 'No se pudo agregar el producto al carrito.'));
        this.agregando = null;
      }
    });
  }

  private sincronizarUrl(): void {
    const queryParams: { [k: string]: string } = {};
    if (this.terminoBusqueda.trim()) {
      queryParams['q'] = this.terminoBusqueda.trim();
    }
    if (this.categoriaSeleccionada) {
      queryParams['categoria'] = this.categoriaSeleccionada;
    }
    this.router.navigate(['/catalogo'], { queryParams });
  }

  imagenDe = imagenDe;
}
