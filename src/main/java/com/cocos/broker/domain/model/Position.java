package com.cocos.broker.domain.model;

import java.math.BigDecimal;

/**
 * Posición del usuario en un instrumento.
 *
 * @param quantity          cantidad de acciones en cartera
 * @param marketValue       valor de mercado ($) = close * quantity
 * @param totalReturnPercent rendimiento total (%) respecto al costo promedio
 * @param dailyReturnPercent retorno diario (%) = (close - previousClose)/previousClose
 */
public record Position(
        Long instrumentId,
        String ticker,
        String name,
        int quantity,
        BigDecimal marketValue,
        BigDecimal totalReturnPercent,
        BigDecimal dailyReturnPercent
) {
}
