package com.cocos.broker.infrastructure.web;

import com.cocos.broker.application.CancelOrderService;
import com.cocos.broker.application.IdempotentOrderService;
import com.cocos.broker.domain.model.Order;
import com.cocos.broker.infrastructure.web.dto.CancelOrderRequest;
import com.cocos.broker.infrastructure.web.dto.OrderResponse;
import com.cocos.broker.infrastructure.web.dto.SendOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada REST para órdenes. Sin lógica de negocio: solo mapea
 * request → comando, delega en la capa de aplicación y mapea el resultado a
 * la respuesta HTTP.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Envío y cancelación de órdenes")
public class OrderController {

    private final IdempotentOrderService idempotentOrderService;
    private final CancelOrderService cancelOrderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enviar una orden (MARKET/LIMIT, BUY/SELL, por cantidad o por monto)",
            description = "La orden se persiste siempre. El rechazo por fondos/acciones insuficientes "
                    + "no es un error HTTP: se devuelve 201 con status=REJECTED. "
                    + "Admite el header 'Idempotency-Key' para evitar duplicados ante reintentos.")
    public OrderResponse send(
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(description = "Clave de idempotencia para evitar órdenes duplicadas") String idempotencyKey,
            @Valid @RequestBody SendOrderRequest sendOrderRequest) {

        Order submittedOrder = idempotentOrderService.submit(sendOrderRequest.toCommand(), idempotencyKey);
        return OrderResponse.from(submittedOrder);
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancelar una orden NEW del usuario (NEW → CANCELLED)")
    public OrderResponse cancel(@PathVariable Long orderId, @Valid @RequestBody CancelOrderRequest request) {
        Order cancelledOrder = cancelOrderService.cancel(orderId, request.userId());
        return OrderResponse.from(cancelledOrder);
    }
}
