package com.cocos.broker.infrastructure.web.dto;

import com.cocos.broker.domain.model.Portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        Long userId,
        BigDecimal totalAccountValue,
        BigDecimal availableCash,
        List<PositionResponse> positions
) {
    public static PortfolioResponse from(Portfolio portfolio) {
        List<PositionResponse> positions = portfolio.positions().stream()
                .map(PositionResponse::from)
                .toList();
        return new PortfolioResponse(
                portfolio.userId(),
                portfolio.totalAccountValue(),
                portfolio.availableCash(),
                positions);
    }
}
