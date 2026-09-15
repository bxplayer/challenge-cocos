package com.cocos.broker.domain.exception;

/**
 * La orden no puede procesarse por datos inválidos o inconsistentes
 * (p. ej. LIMIT sin precio, ni size ni amount, monto insuficiente para una acción,
 * o falta de precio de mercado). No confundir con el rechazo por fondos/acciones,
 * que se persiste como estado REJECTED.
 */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
