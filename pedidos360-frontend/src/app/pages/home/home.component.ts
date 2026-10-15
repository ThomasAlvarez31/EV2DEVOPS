import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ProductoService } from '../../services/producto.service';
import { CarritoService } from '../../services/carrito.service';
import { ToastService } from '../../services/toast.service';
import { Producto } from '../../models/producto.model';
import { imagenDe, mensajeDeError } from '../../shared/utilidades';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './home.component.html'
})
export class HomeComponent implements OnInit {

  destacados: Producto[] = [];
  categorias: string[] = [];
  cargando = true;
  error: string | null = null;
  termino = '';
  agregando: number | null = null;

  constructor(
    private productoService: ProductoService,
    private carritoService: CarritoService,
    private toastService: ToastService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.productoService.listar().subscribe({
      next: (productos) => {
        this.destacados = productos.slice(0, 8);
        this.cargando = false;
      },
      error: (err) => {
        this.error = mensajeDeError(err, 'No se pudo cargar el catálogo.');
        this.cargando = false;
      }
    });

    this.productoService.categorias().subscribe({
      next: (cats) => (this.categorias = cats),
      error: () => (this.categorias = [])
    });
  }

  buscar(): void {
    const q = this.termino.trim();
    this.router.navigate(['/catalogo'], { queryParams: q ? { q } : {} });
  }

  irACategoria(categoria: string): void {
    this.router.navigate(['/catalogo'], { queryParams: { categoria } });
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
        this.toastService.error(mensajeDeError(err, 'No se pudo agregar el producto.'));
        this.agregando = null;
      }
    });
  }

  imagenDe = imagenDe;
}
