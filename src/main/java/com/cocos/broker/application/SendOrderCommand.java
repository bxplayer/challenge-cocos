package com.cocos.broker.application;

import com.cocos.broker.domain.OrderSide;
import com.cocos.broker.domain.OrderType;

import java.math.BigDecimal;

/**
 * Datos de entrada para enviar una orden. Se debe indicar exactamente uno de
 * {@code size} (cantidad de acciones) o {@code amount} (monto en pesos).
 * {@code price} es obligatorio solo para órdenes LIMIT.
 */
public record SendOrderCommand(
        Long userId,
        Long instrumentId,
        OrderSide side,
        OrderType type,
        Integer size,
        BigDecimal amount,
        BigDecimal price
) {
}
