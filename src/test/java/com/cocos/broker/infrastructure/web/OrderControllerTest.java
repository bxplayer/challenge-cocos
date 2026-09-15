package com.cocos.broker.infrastructure.web;

import com.cocos.broker.application.CancelOrderService;
import com.cocos.broker.application.IdempotentOrderService;
import com.cocos.broker.application.SendOrderCommand;
import com.cocos.broker.domain.OrderSide;
import com.cocos.broker.domain.OrderStatus;
import com.cocos.broker.domain.OrderType;
import com.cocos.broker.domain.exception.InstrumentNotFoundException;
import com.cocos.broker.domain.model.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa HTTP aislada (sin DB ni Redis): validación del request y mapeo de
 * excepciones a ProblemDetail (RFC 7807).
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private IdempotentOrderService idempotentOrderService;
    @MockBean
    private CancelOrderService cancelOrderService;

    @Test
    @DisplayName("POST /orders válido → 201 con la orden (REJECTED también es 201)")
    void sendReturns201() throws Exception {
        Order rejected = new Order(12L, 54L, 1L, 10, new BigDecimal("229.50"), OrderType.MARKET, OrderSide.BUY,
                OrderStatus.REJECTED, LocalDateTime.parse("2024-01-15T10:30:00"));
        when(idempotentOrderService.submit(any(SendOrderCommand.class), eq("abc"))).thenReturn(rejected);

        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"instrumentId":54,"side":"BUY","type":"MARKET","size":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Bean Validation → 400 con el detalle por campo")
    void invalidBodyReturns400WithErrors() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instrumentId":54,"side":"BUY","type":"LIMIT","size":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("userId")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("size")));
    }

    @Test
    @DisplayName("Excepción de dominio → ProblemDetail con el status correspondiente")
    void domainExceptionReturnsProblemDetail() throws Exception {
        when(idempotentOrderService.submit(any(), any())).thenThrow(new InstrumentNotFoundException(999L));

        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"instrumentId":999,"side":"BUY","type":"MARKET","size":10}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Instrument not found: 999"));
    }

    @Test
    @DisplayName("Los errores propios de Spring conservan su status (ruta inexistente → 404, no 500)")
    void springErrorsKeepTheirStatus() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON));
    }
}
