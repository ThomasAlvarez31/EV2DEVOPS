import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  template: `
    <footer class="footer">
      <div class="footer-grid">
        <div class="footer-col">
          <div class="footer-brand">Pedidos360</div>
          <p class="footer-texto">
            Tienda online desarrollada con arquitectura cloud native:
            microservicios en Spring Boot, frontend en Angular y autenticación
            con Microsoft Entra ID.
          </p>
        </div>
        <div class="footer-col">
          <h4>Tienda</h4>
          <a routerLink="/catalogo">Catálogo</a>
          <a routerLink="/carrito">Carrito</a>
          <a routerLink="/pedidos">Mis pedidos</a>
        </div>
        <div class="footer-col">
          <h4>Cuenta</h4>
          <a routerLink="/login">Iniciar sesión</a>
          <a routerLink="/pedidos">Historial de compras</a>
        </div>
        <div class="footer-col">
          <h4>Proyecto</h4>
          <span>Desarrollo Cloud Native I · DSY1107</span>
          <span>Pagos simulados, sin conexión a bancos reales.</span>
        </div>
      </div>
      <div class="footer-bottom">
        <span>© {{ anio }} Pedidos360</span>
        <span>Thomas Alvarez · Diego Diaz</span>
      </div>
    </footer>
  `
})
export class FooterComponent {
  readonly anio = new Date().getFullYear();
}
