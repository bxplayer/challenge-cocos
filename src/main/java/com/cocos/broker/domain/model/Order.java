package com.cocos.broker.domain.model;

import com.cocos.broker.domain.OrderSide;
import com.cocos.broker.domain.OrderStatus;
import com.cocos.broker.domain.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Orden del libro. El signo de la tenencia y del cash se deriva de
 * {@code side}/{@code status}; {@code size} es la cantidad (acciones o pesos
 * en el caso de CASH_IN/CASH_OUT).
 */
public record Order(
        Long id,
        Long instrumentId,
        Long userId,
        Integer size,
        BigDecimal price,
        OrderType type,
        OrderSide side,
        OrderStatus status,
        LocalDateTime datetime
) {
    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, instrumentId, userId, size, price, type, side, newStatus, datetime);
    }
}
