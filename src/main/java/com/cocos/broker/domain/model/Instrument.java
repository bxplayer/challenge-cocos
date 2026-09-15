package com.cocos.broker.domain.model;

/**
 * Instrumento del mercado. {@code type} es 'ACCIONES' o 'MONEDA'
 * (el cash ARS se modela como instrumento de tipo MONEDA).
 */
public record Instrument(
        Long id,
        String ticker,
        String name,
        String type
) {
    public static final String TYPE_CURRENCY = "MONEDA";

    public boolean isCurrency() {
        return TYPE_CURRENCY.equalsIgnoreCase(type);
    }
}
