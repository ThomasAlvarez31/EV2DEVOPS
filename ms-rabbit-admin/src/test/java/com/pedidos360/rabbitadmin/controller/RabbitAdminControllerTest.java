package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.CrearBindingRequest;
import com.pedidos360.rabbitadmin.dto.CrearColaRequest;
import com.pedidos360.rabbitadmin.dto.CrearExchangeRequest;
import com.pedidos360.rabbitadmin.dto.ColaInfo;
import com.pedidos360.rabbitadmin.dto.ExchangeInfo;
import com.pedidos360.rabbitadmin.error.ApiExceptionHandler;
import com.pedidos360.rabbitadmin.error.RecursoNoEncontradoException;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la API REST: verbos, codigos HTTP y validacion de la entrada.
 * El servicio se sustituye por un mock; aqui se comprueba el contrato HTTP.
 */
class RabbitAdminControllerTest {

    private RabbitAdminService servicio;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        servicio = mock(RabbitAdminService.class);

        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RabbitAdminController(servicio))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validador)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    // ------------------------------------------------------------------
    //  COLAS
    // ------------------------------------------------------------------

    @Test
    void postQueues_deberiaDevolver201() throws Exception {
        when(servicio.crearCola(any(CrearColaRequest.class)))
                .thenReturn(new ColaInfo("pedidos360.emails", 0, 0, true, false, null));

        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"pedidos360.emails\",\"durable\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("pedidos360.emails"));
    }

    @Test
    void postQueues_deberiaDevolver400_siElNombreEstaVacio() throws Exception {
        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"durable\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("name")));

        verify(servicio, never()).crearCola(any());
    }

    @Test
    void postQueues_deberiaDevolver400_siNoEnviaDurableNiNombre() throws Exception {
        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());

        verify(servicio, never()).crearCola(any());
    }

    @Test
    void postQueues_deberiaDevolver400_siElJsonEsInvalido() throws Exception {
        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void getQueue_deberiaDevolver200() throws Exception {
        when(servicio.obtenerCola("pedidos360.emails"))
                .thenReturn(new ColaInfo("pedidos360.emails", 5, 1, null, null, null));

        mockMvc.perform(get("/queues/pedidos360.emails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").value(5))
                .andExpect(jsonPath("$.consumers").value(1));
    }

    @Test
    void getQueue_deberiaDevolver404_siNoExiste() throws Exception {
        when(servicio.obtenerCola("fantasma"))
                .thenThrow(new RecursoNoEncontradoException("No existe la cola 'fantasma'."));

        mockMvc.perform(get("/queues/fantasma"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No existe la cola 'fantasma'."));
    }

    @Test
    void deleteQueue_deberiaDevolver204() throws Exception {
        mockMvc.perform(delete("/queues/pedidos360.emails")
                        .param("ifUnused", "true"))
                .andExpect(status().isNoContent());

        verify(servicio).eliminarCola("pedidos360.emails", true, false);
    }

    @Test
    void deleteQueue_deberiaDevolver404_siNoExiste() throws Exception {
        org.mockito.Mockito.doThrow(new RecursoNoEncontradoException("No existe la cola 'x'."))
                .when(servicio).eliminarCola("x", false, false);

        mockMvc.perform(delete("/queues/x"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getQueues_deberiaDevolverLista() throws Exception {
        when(servicio.listarColas()).thenReturn(List.of(
                new ColaInfo("pedidos360.emails", 0, 1, true, false, null)));

        mockMvc.perform(get("/queues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("pedidos360.emails"));
    }

    // ------------------------------------------------------------------
    //  EXCHANGES
    // ------------------------------------------------------------------

    @Test
    void postExchanges_deberiaDevolver201() throws Exception {
        when(servicio.crearExchange(any(CrearExchangeRequest.class)))
                .thenReturn(new ExchangeInfo("pedidos360.eventos.topic", "direct", true, false, null));

        mockMvc.perform(post("/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"pedidos360.eventos.topic\",\"type\":\"direct\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("direct"));
    }

    @Test
    void postExchanges_deberiaDevolver400_siElTipoNoEsValido() throws Exception {
        mockMvc.perform(post("/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"cualquiera\",\"type\":\"rabbit\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("type")));

        verify(servicio, never()).crearExchange(any());
    }

    @Test
    void deleteExchange_deberiaDevolver204() throws Exception {
        mockMvc.perform(delete("/exchanges/pedidos360.eventos.topic"))
                .andExpect(status().isNoContent());

        verify(servicio).eliminarExchange("pedidos360.eventos.topic");
    }

    // ------------------------------------------------------------------
    //  BINDINGS
    // ------------------------------------------------------------------

    @Test
    void postBindings_deberiaDevolver201() throws Exception {
        when(servicio.crearBinding(any(CrearBindingRequest.class)))
                .thenReturn(new com.pedidos360.rabbitadmin.dto.BindingInfo(
                        "pedidos360.eventos.topic", "pedidos360.emails", "pedido.registrado", null));

        mockMvc.perform(post("/bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"pedidos360.emails\","
                                + "\"exchange\":\"pedidos360.eventos.topic\","
                                + "\"routingKey\":\"pedido.registrado\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routingKey").value("pedido.registrado"));
    }

    @Test
    void postBindings_deberiaDevolver400_siFaltaLaRoutingKey() throws Exception {
        mockMvc.perform(post("/bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"pedidos360.emails\","
                                + "\"exchange\":\"pedidos360.eventos.topic\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("routingKey")));

        verify(servicio, never()).crearBinding(any());
    }

    @Test
    void deleteBindings_deberiaDevolver204_conQueryParams() throws Exception {
        mockMvc.perform(delete("/bindings")
                        .param("queue", "pedidos360.emails")
                        .param("exchange", "pedidos360.eventos.topic")
                        .param("routingKey", "pedido.registrado"))
                .andExpect(status().isNoContent());

        verify(servicio).eliminarBinding("pedidos360.emails",
                "pedidos360.eventos.topic", "pedido.registrado");
    }

    @Test
    void deleteBindings_deberiaDevolver400_siFaltanLosQueryParams() throws Exception {
        mockMvc.perform(delete("/bindings").param("queue", "pedidos360.emails"))
                .andExpect(status().isBadRequest());
    }
}
