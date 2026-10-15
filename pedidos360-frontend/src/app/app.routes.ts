import { Routes } from '@angular/router';
import { environment } from '../environments/environment';
import { authGuard } from './core/auth.guard';
import { LoginComponent } from './pages/login/login.component';
import { HomeComponent } from './pages/home/home.component';
import { CatalogoComponent } from './pages/catalogo/catalogo.component';
import { CarritoComponent } from './pages/carrito/carrito.component';
import { CheckoutComponent } from './pages/checkout/checkout.component';
import { PedidosComponent } from './pages/pedidos/pedidos.component';
import { RabbitmqComponent } from './pages/rabbitmq/rabbitmq.component';

// SOLO PRUEBA LOCAL (authBypass=true): sin guards para entrar sin login.
// Con authBypass=false las rutas quedan protegidas con el guard unico que
// acepta sesion de Microsoft Entra ID O de Amazon Cognito (core/auth.guard).
const guards = environment.authBypass ? [] : [authGuard];

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'catalogo', component: CatalogoComponent, canActivate: guards },
  { path: 'carrito', component: CarritoComponent, canActivate: guards },
  { path: 'checkout', component: CheckoutComponent, canActivate: guards },
  { path: 'pedidos', component: PedidosComponent, canActivate: guards },
  { path: 'rabbitmq', component: RabbitmqComponent, canActivate: guards },
  { path: '**', redirectTo: '' }
];
