package com.cocos.broker.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Portfolio de un usuario.
 *
 * @param totalAccountValue valor total de la cuenta = cash total (incluye reservas) + valor de mercado de las posiciones
 * @param availableCash      pesos disponibles para operar
 * @param positions          posiciones con tenencia mayor a cero
 */
public record Portfolio(
        Long userId,
        BigDecimal totalAccountValue,
        BigDecimal availableCash,
        List<Position> positions
) {
}
