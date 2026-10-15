package com.pedidos360.carrito.messaging;

import com.pedidos360.carrito.model.ItemPedido;
import com.pedidos360.carrito.model.Pedido;

import java.time.format.DateTimeFormatter;

/**
 * Plantillas HTML sencillas y compatibles con los clientes de correo
 * (tablas con estilos en línea, sin CSS externo).
 */
public final class PlantillaEmails {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private PlantillaEmails() {
    }

    /** Envoltorio comun: cabecera de marca, contenido y pie. */
    public static String envoltorio(String titulo, String cuerpoHtml) {
        return "<!doctype html>\n"
                + "<html lang=\"es\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>\n"
                + "<body style=\"margin:0;padding:0;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;\">\n"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#f1f5f9;padding:24px 12px;\"><tr><td align=\"center\">\n"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:600px;width:100%;background:#ffffff;border-radius:12px;"
                + "border:1px solid #e2e8f0;overflow:hidden;\">\n"
                + "<tr><td style=\"background:#0f172a;padding:20px 28px;\">"
                + "<span style=\"color:#ffffff;font-size:20px;font-weight:bold;letter-spacing:-0.5px;\">"
                + "Pedidos360</span></td></tr>\n"
                + "<tr><td style=\"padding:28px;\">"
                + "<h1 style=\"margin:0 0 18px;font-size:20px;color:#0f172a;line-height:1.3;\">"
                + escape(titulo) + "</h1>"
                + cuerpoHtml
                + "</td></tr>\n"
                + "<tr><td style=\"background:#f8fafc;padding:16px 28px;border-top:1px solid #e2e8f0;"
                + "color:#64748b;font-size:12px;\">Correo automático de Pedidos360. "
                + "No respondas a este mensaje.</td></tr>\n"
                + "</table>\n</td></tr></table>\n</body></html>";
    }

    /** Tabla con el detalle de productos, total, metodo de pago y estado. */
    public static String resumenPedido(Pedido pedido, String nombreCliente) {
        StringBuilder html = new StringBuilder();

        html.append("<p style=\"margin:0 0 18px;color:#334155;font-size:14px;line-height:1.6;\">Hola <strong>")
                .append(escape(nombreCliente == null || nombreCliente.isBlank() ? "cliente" : nombreCliente))
                .append("</strong>,</p>");

        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"border-collapse:collapse;font-size:14px;\">");

        html.append("<tr style=\"background:#f8fafc;\">"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#64748b;\">Producto</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#64748b;text-align:center;\">Cant.</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#64748b;text-align:right;\">Importe</td>"
                + "</tr>");

        for (ItemPedido item : pedido.getItems()) {
            html.append("<tr>")
                    .append("<td style=\"padding:10px 12px;border-bottom:1px solid #e2e8f0;color:#0f172a;\">")
                    .append(escape(item.getNombreProducto()))
                    .append(" <span style=\"color:#94a3b8;\">x $")
                    .append(item.getPrecioUnitario()).append("</span></td>")
                    .append("<td style=\"padding:10px 12px;border-bottom:1px solid #e2e8f0;text-align:center;\">")
                    .append(item.getCantidad()).append("</td>")
                    .append("<td style=\"padding:10px 12px;border-bottom:1px solid #e2e8f0;text-align:right;\">$")
                    .append(item.getPrecioUnitario() * item.getCantidad()).append("</td>")
                    .append("</tr>");
        }

        html.append("<tr><td colspan=\"2\" style=\"padding:14px 12px;font-size:16px;font-weight:bold;\">Total</td>"
                + "<td style=\"padding:14px 12px;text-align:right;font-size:18px;font-weight:bold;\">$")
                .append(pedido.getTotal()).append("</td></tr>");
        html.append("</table>");

        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"margin-top:18px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;"
                + "font-size:13px;color:#334155;\">");
        html.append(dato("Pedido", "#" + pedido.getId()));
        html.append(dato("Fecha", FECHA.format(pedido.getFecha())));
        if (pedido.getMetodoPago() != null) {
            html.append(dato("Método de pago", pedido.getMetodoPago().getEtiqueta()));
        }
        html.append(dato("Estado del pedido", etiquetaEstado(pedido)));
        html.append(dato("Estado del pago", etiquetaEstadoPago(pedido)));
        html.append("</table>");

        return html.toString();
    }

    /** Mensaje breve de cierre. */
    public static String cierre(String texto) {
        return "<p style=\"margin:20px 0 0;color:#334155;font-size:14px;line-height:1.6;\">"
                + escape(texto) + "</p>";
    }

    /** Aviso destacado (por ejemplo "pago rechazado"). */
    public static String aviso(String color, String texto) {
        return "<p style=\"margin:0 0 18px;padding:12px 14px;border-radius:8px;font-size:14px;"
                + "background:" + color + ";color:#0f172a;border:1px solid #e2e8f0;\">" + escape(texto) + "</p>";
    }

    public static String etiquetaEstado(Pedido pedido) {
        return switch (pedido.getEstado()) {
            case PENDIENTE_PAGO -> "Pendiente de pago";
            case CONFIRMADO -> "Pagado";
            case EN_PREPARACION -> "En preparación";
            case ENVIADO -> "Enviado";
            case ENTREGADO -> "Entregado";
            case CANCELADO -> "Cancelado";
        };
    }

    public static String etiquetaEstadoPago(Pedido pedido) {
        return switch (pedido.getEstadoPago()) {
            case PENDIENTE -> "Pendiente";
            case APROBADO -> "Aprobado";
            case RECHAZADO -> "Rechazado";
        };
    }

    private static String dato(String etiqueta, String valor) {
        return "<tr><td style=\"padding:8px 12px;color:#64748b;\">" + escape(etiqueta) + "</td>"
                + "<td style=\"padding:8px 12px;text-align:right;color:#0f172a;font-weight:600;\">"
                + escape(valor) + "</td></tr>";
    }

    private static String escape(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
