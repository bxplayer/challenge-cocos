package com.cocos.broker.domain;

/**
 * Lado de la orden. BUY/SELL operan instrumentos; CASH_IN/CASH_OUT modelan
 * transferencias de dinero (entrantes/salientes).
 */
public enum OrderSide {
    BUY,
    SELL,
    CASH_IN,
    CASH_OUT
}
