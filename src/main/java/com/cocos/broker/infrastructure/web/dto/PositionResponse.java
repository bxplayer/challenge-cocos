package com.cocos.broker.infrastructure.web.dto;

import com.cocos.broker.domain.model.Position;

import java.math.BigDecimal;

public record PositionResponse(
        Long instrumentId,
        String ticker,
        String name,
        int quantity,
        BigDecimal marketValue,
        BigDecimal totalReturnPercent,
        BigDecimal dailyReturnPercent
) {
    public static PositionResponse from(Position p) {
        return new PositionResponse(
                p.instrumentId(), p.ticker(), p.name(), p.quantity(),
                p.marketValue(), p.totalReturnPercent(), p.dailyReturnPercent());
    }
}
