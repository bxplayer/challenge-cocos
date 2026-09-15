package com.cocos.broker.infrastructure.web.dto;

import com.cocos.broker.domain.OrderSide;
import com.cocos.broker.domain.OrderStatus;
import com.cocos.broker.domain.OrderType;
import com.cocos.broker.domain.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
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
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.id(), o.instrumentId(), o.userId(), o.size(), o.price(),
                o.type(), o.side(), o.status(), o.datetime());
    }
}
