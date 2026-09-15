package com.cocos.broker.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Precio de un instrumento para una fecha. {@code close} es el último precio;
 * {@code previousClose} el cierre anterior (para el retorno diario).
 */
public record MarketData(
        Long id,
        Long instrumentId,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        BigDecimal close,
        BigDecimal previousClose,
        LocalDate date
) {
}
