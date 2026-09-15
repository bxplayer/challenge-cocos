package com.cocos.broker.domain;

/**
 * Tipo de orden. MARKET se ejecuta al último precio; LIMIT se envía con un
 * precio objetivo y queda en estado NEW.
 */
public enum OrderType {
    MARKET,
    LIMIT
}
