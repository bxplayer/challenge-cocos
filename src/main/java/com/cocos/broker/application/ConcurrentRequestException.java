package com.cocos.broker.application;

/**
 * Llegó una solicitud con una Idempotency-Key cuya solicitud original todavía
 * está en proceso. El cliente debe reintentar más tarde para obtener el resultado.
 */
public class ConcurrentRequestException extends RuntimeException {

    public ConcurrentRequestException(String idempotencyKey) {
        super("Ya hay una solicitud en curso con Idempotency-Key '" + idempotencyKey + "'");
    }
}
