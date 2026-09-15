package com.cocos.broker.infrastructure.web.dto;

import com.cocos.broker.application.SendOrderCommand;
import com.cocos.broker.domain.OrderSide;
import com.cocos.broker.domain.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Solicitud de envío de orden. Acá solo se valida la forma (campos requeridos y
 * signos); las reglas cruzadas (exactamente uno de {@code size}/{@code amount},
 * {@code price} obligatorio para LIMIT, side BUY/SELL) las aplica el caso de uso
 * y responden 422.
 */
public record SendOrderRequest(

        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull @Schema(description = "BUY o SELL") OrderSide side,
        @NotNull OrderType type,

        @Positive @Schema(description = "Cantidad de acciones (excluyente con amount)") Integer size,
        @Positive @Schema(description = "Monto en pesos a invertir (excluyente con size)") BigDecimal amount,
        @Positive @Schema(description = "Precio; obligatorio para LIMIT") BigDecimal price
) {
    public SendOrderCommand toCommand() {
        return new SendOrderCommand(userId, instrumentId, side, type, size, amount, price);
    }
}
